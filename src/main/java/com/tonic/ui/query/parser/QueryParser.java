package com.tonic.ui.query.parser;

import com.tonic.ui.core.util.ComparisonOperator;
import com.tonic.ui.query.ast.*;

import java.util.List;

/**
 * Recursive descent parser for the query DSL.
 *
 * Grammar:
 * QUERY := (FIND | SHOW) TARGET [SCOPE] [WHERE PREDICATE] [WITH RUNSPEC] [LIMIT N] [ORDER BY KEY (ASC|DESC)]
 * TARGET := methods | classes | paths | events | strings | objects
 * SCOPE := IN (all | class PATTERN | method PATTERN) | DURING <clinit> [OF classes matching PATTERN]
 * PREDICATE := OR_EXPR
 * OR_EXPR := AND_EXPR (OR AND_EXPR)*
 * AND_EXPR := UNARY (AND UNARY)*
 * UNARY := NOT UNARY | PRIMARY
 * PRIMARY := FUNCTION_CALL | (PREDICATE)
 * FUNCTION_CALL := calls(STRING) | allocCount(STRING) OP NUMBER | ...
 */
public class QueryParser {

    private List<Token> tokens;
    private int current;

    public Query parse(String input) throws ParseException {
        QueryLexer lexer = new QueryLexer(input);
        this.tokens = lexer.tokenize();
        this.current = 0;

        Query query = parseQuery();

        if (!isAtEnd()) {
            throw new ParseException("Unexpected token: " + peek().value(), peek().position());
        }

        return query;
    }

    private Query parseQuery() throws ParseException {
        boolean isFind = match(Token.TokenType.FIND);
        boolean isShow = match(Token.TokenType.SHOW);

        if (!isFind && !isShow) {
            throw new ParseException("Expected FIND or SHOW", peek().position());
        }

        Target target = parseTarget();
        Scope scope = AllScope.INSTANCE;
        Predicate predicate = null;
        RunSpec runSpec = null;
        Integer limit = null;
        OrderBy orderBy = null;

        if (check(Token.TokenType.IN)) {
            scope = parseInScope();
        } else if (check(Token.TokenType.DURING)) {
            scope = parseDuringScope();
        } else if (check(Token.TokenType.BETWEEN)) {
            scope = parseBetweenScope();
        }

        if (match(Token.TokenType.WHERE)) {
            predicate = parseOrExpr();
        }

        if (match(Token.TokenType.WITH)) {
            runSpec = parseRunSpec();
        }

        if (match(Token.TokenType.LIMIT)) {
            limit = parseInteger();
        }

        if (match(Token.TokenType.ORDER)) {
            expect(Token.TokenType.BY);
            String key = consume(Token.TokenType.IDENTIFIER, "Expected order key").value();
            boolean desc = match(Token.TokenType.DESC);
            if (!desc) {
                match(Token.TokenType.ASC);
            }
            orderBy = desc ? OrderBy.desc(key) : OrderBy.asc(key);
        }

        if (isFind) {
            return new FindQuery(target, scope, predicate, runSpec, limit, orderBy);
        } else {
            return new ShowQuery(target, scope, predicate, runSpec, limit, orderBy);
        }
    }

    private Target parseTarget() throws ParseException {
        if (match(Token.TokenType.METHODS)) return Target.METHODS;
        if (match(Token.TokenType.CLASSES)) return Target.CLASSES;
        if (match(Token.TokenType.PATHS)) return Target.PATHS;
        if (match(Token.TokenType.EVENTS)) return Target.EVENTS;
        if (match(Token.TokenType.STRINGS)) return Target.STRINGS;
        if (match(Token.TokenType.OBJECTS)) return Target.OBJECTS;

        if (match(Token.TokenType.ALL)) {
            return parseTarget();
        }

        throw new ParseException("Expected target type (methods, classes, paths, events, strings, objects)", peek().position());
    }

    private Scope parseInScope() throws ParseException {
        expect(Token.TokenType.IN);

        if (match(Token.TokenType.ALL)) {
            return AllScope.INSTANCE;
        }

        if (match(Token.TokenType.CLASS)) {
            String pattern = parsePatternOrString();
            boolean isRegex = previous().type() == Token.TokenType.REGEX;
            return new ClassScope(pattern, isRegex);
        }

        if (match(Token.TokenType.METHOD)) {
            String pattern = parsePatternOrString();
            boolean isRegex = previous().type() == Token.TokenType.REGEX;
            return new MethodScope(pattern, isRegex);
        }

        throw new ParseException("Expected 'all', 'class', or 'method' after IN", peek().position());
    }

    private Scope parseDuringScope() throws ParseException {
        expect(Token.TokenType.DURING);

        if (match(Token.TokenType.CLINIT)) {
            ClassScope classFilter = null;
            if (match(Token.TokenType.OF)) {
                if (match(Token.TokenType.CLASSES) || match(Token.TokenType.CLASS)) {
                    if (match(Token.TokenType.IDENTIFIER) && "matching".equalsIgnoreCase(previous().value())) {
                        String pattern = parsePatternOrString();
                        boolean isRegex = previous().type() == Token.TokenType.REGEX;
                        classFilter = new ClassScope(pattern, isRegex);
                    }
                }
            }
            return DuringScope.clinitOf(classFilter);
        }

        if (match(Token.TokenType.METHOD)) {
            String pattern = parsePatternOrString();
            return DuringScope.method(pattern);
        }

        throw new ParseException("Expected '<clinit>' or 'method' after DURING", peek().position());
    }

    private Scope parseBetweenScope() throws ParseException {
        expect(Token.TokenType.BETWEEN);
        Predicate start = parsePrimary();
        expect(Token.TokenType.AND);
        Predicate end = parsePrimary();
        return new BetweenScope(start, end);
    }

    private Predicate parseOrExpr() throws ParseException {
        Predicate left = parseAndExpr();

        while (match(Token.TokenType.OR)) {
            Predicate right = parseAndExpr();
            left = new OrPredicate(left, right);
        }

        return left;
    }

    private Predicate parseAndExpr() throws ParseException {
        Predicate left = parseUnary();

        while (match(Token.TokenType.AND)) {
            Predicate right = parseUnary();
            left = new AndPredicate(left, right);
        }

        return left;
    }

    private Predicate parseUnary() throws ParseException {
        if (match(Token.TokenType.NOT)) {
            return new NotPredicate(parseUnary());
        }
        return parsePrimary();
    }

    private Predicate parsePrimary() throws ParseException {
        if (match(Token.TokenType.LPAREN)) {
            Predicate expr = parseOrExpr();
            expect(Token.TokenType.RPAREN);
            return expr;
        }

        if (match(Token.TokenType.BEFORE)) {
            expect(Token.TokenType.LPAREN);
            Predicate inner = parsePrimary();
            expect(Token.TokenType.RPAREN);
            return new BeforePredicate(inner);
        }

        if (match(Token.TokenType.AFTER)) {
            expect(Token.TokenType.LPAREN);
            Predicate inner = parsePrimary();
            expect(Token.TokenType.RPAREN);
            return new AfterPredicate(inner);
        }

        if (match(Token.TokenType.CALLS)) {
            expect(Token.TokenType.LPAREN);
            String methodRef = parseString();
            ArgumentType argType = ArgumentType.ANY;
            if (match(Token.TokenType.COMMA)) {
                argType = parseArgumentType();
            }
            expect(Token.TokenType.RPAREN);
            return CallsPredicate.of(methodRef, argType);
        }

        if (match(Token.TokenType.ALLOC_COUNT)) {
            expect(Token.TokenType.LPAREN);
            String typeName = parseString();
            expect(Token.TokenType.RPAREN);
            ComparisonOperator op = parseComparisonOp();
            int threshold = parseInteger();
            return new AllocCountPredicate(typeName, op, threshold);
        }

        if (match(Token.TokenType.WRITES_FIELD)) {
            expect(Token.TokenType.LPAREN);
            String fieldRef = parseString();
            expect(Token.TokenType.RPAREN);
            return WritesFieldPredicate.of(fieldRef);
        }

        if (match(Token.TokenType.READS_FIELD)) {
            expect(Token.TokenType.LPAREN);
            String fieldRef = parseString();
            expect(Token.TokenType.RPAREN);
            return ReadsFieldPredicate.of(fieldRef);
        }

        if (match(Token.TokenType.FIELD)) {
            expect(Token.TokenType.LPAREN);
            String fieldRef = parseString();
            expect(Token.TokenType.RPAREN);

            if (match(Token.TokenType.BECOMES)) {
                if (match(Token.TokenType.NON_NULL)) {
                    return FieldBecomesPredicate.becomesNonNull(fieldRef);
                } else if (match(Token.TokenType.NULL)) {
                    return FieldBecomesPredicate.becomesNull(fieldRef);
                }
                throw new ParseException("Expected 'non-null' or 'null' after 'becomes'", peek().position());
            }

            return ReadsFieldPredicate.of(fieldRef);
        }

        if (match(Token.TokenType.CONTAINS_STRING)) {
            expect(Token.TokenType.LPAREN);
            String pattern = parsePatternOrString();
            boolean isRegex = previous().type() == Token.TokenType.REGEX;
            expect(Token.TokenType.RPAREN);
            return isRegex ? ContainsStringPredicate.regex(pattern) : ContainsStringPredicate.literal(pattern);
        }

        if (match(Token.TokenType.THROWS)) {
            expect(Token.TokenType.LPAREN);
            String exType = parseString();
            expect(Token.TokenType.RPAREN);
            return ThrowsPredicate.of(exType);
        }

        if (match(Token.TokenType.INSTRUCTION_COUNT)) {
            ComparisonOperator op = parseComparisonOp();
            long threshold = parseLong();
            return new InstructionCountPredicate(op, threshold);
        }

        if (match(Token.TokenType.COVERAGE)) {
            if (match(Token.TokenType.LPAREN)) {
                String blockId = parseString();
                expect(Token.TokenType.RPAREN);
                ComparisonOperator op = parseComparisonOp();
                double threshold = parseDouble();
                return new CoveragePredicate(blockId, op, threshold);
            }
            ComparisonOperator op = parseComparisonOp();
            double threshold = parseDouble();
            return new CoveragePredicate(null, op, threshold);
        }

        throw new ParseException("Expected predicate function", peek().position());
    }

    private RunSpec parseRunSpec() throws ParseException {
        RunSpec.Builder builder = RunSpec.builder();

        while (check(Token.TokenType.IDENTIFIER)) {
            String key = advance().value().toLowerCase();
            expect(Token.TokenType.COLON);

            switch (key) {
                case "seeds":
                    builder.seeds(parseInteger());
                    break;
                case "maxinstructions":
                case "max_instructions":
                    builder.maxInstructions(parseInteger());
                    break;
                case "maxdepth":
                case "max_depth":
                    builder.maxDepth(parseInteger());
                    break;
                case "tracemode":
                case "trace_mode":
                case "trace":
                    String mode = advance().value().toUpperCase();
                    builder.traceMode(RunSpec.TraceMode.valueOf(mode));
                    break;
                case "timebudget":
                case "time_budget":
                    builder.timeBudget(parseInteger());
                    break;
                default:
                    throw new ParseException("Unknown run spec key: " + key, previous().position());
            }

            if (!check(Token.TokenType.IDENTIFIER)) {
                break;
            }
        }

        return builder.build();
    }

    private ComparisonOperator parseComparisonOp() throws ParseException {
        if (match(Token.TokenType.GT)) return ComparisonOperator.GT;
        if (match(Token.TokenType.GTE)) return ComparisonOperator.GTE;
        if (match(Token.TokenType.LT)) return ComparisonOperator.LT;
        if (match(Token.TokenType.LTE)) return ComparisonOperator.LTE;
        if (match(Token.TokenType.EQ)) return ComparisonOperator.EQ;
        if (match(Token.TokenType.NEQ)) return ComparisonOperator.NEQ;
        throw new ParseException("Expected comparison operator", peek().position());
    }

    private ArgumentType parseArgumentType() throws ParseException {
        if (match(Token.TokenType.ARG_ANY)) return ArgumentType.ANY;
        if (match(Token.TokenType.ARG_LITERAL)) return ArgumentType.LITERAL;
        if (match(Token.TokenType.ARG_DYNAMIC)) return ArgumentType.DYNAMIC;
        if (match(Token.TokenType.ARG_FIELD)) return ArgumentType.FIELD;
        if (match(Token.TokenType.ARG_LOCAL)) return ArgumentType.LOCAL;
        if (match(Token.TokenType.ARG_CALL)) return ArgumentType.CALL;
        throw new ParseException("Expected argument type (any, literal, dynamic, field, local, call)", peek().position());
    }

    private String parseString() throws ParseException {
        Token token = consume(Token.TokenType.STRING, "Expected string");
        return token.value();
    }

    private String parsePatternOrString() throws ParseException {
        if (check(Token.TokenType.STRING) || check(Token.TokenType.REGEX)) {
            return advance().value();
        }
        throw new ParseException("Expected string or regex pattern", peek().position());
    }

    private int parseInteger() throws ParseException {
        Token token = consume(Token.TokenType.NUMBER, "Expected number");
        try {
            return Integer.parseInt(token.value().replace("_", ""));
        } catch (NumberFormatException e) {
            throw new ParseException("Invalid integer: " + token.value(), token.position());
        }
    }

    private long parseLong() throws ParseException {
        Token token = consume(Token.TokenType.NUMBER, "Expected number");
        try {
            return Long.parseLong(token.value().replace("_", ""));
        } catch (NumberFormatException e) {
            throw new ParseException("Invalid long: " + token.value(), token.position());
        }
    }

    private double parseDouble() throws ParseException {
        Token token = consume(Token.TokenType.NUMBER, "Expected number");
        try {
            return Double.parseDouble(token.value());
        } catch (NumberFormatException e) {
            throw new ParseException("Invalid double: " + token.value(), token.position());
        }
    }

    private boolean match(Token.TokenType type) {
        if (check(type)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean check(Token.TokenType type) {
        if (isAtEnd()) return false;
        return peek().type() == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private boolean isAtEnd() {
        return peek().type() == Token.TokenType.EOF;
    }

    private Token consume(Token.TokenType type, String message) throws ParseException {
        if (check(type)) return advance();
        throw new ParseException(message + ", got " + peek().type(), peek().position());
    }

    private void expect(Token.TokenType type) throws ParseException {
        consume(type, "Expected " + type);
    }
}
