package com.tonic.ui.query.parser;

import com.tonic.ui.query.ast.*;

import java.util.List;

/**
 * Parses the outer query shell — {@code (FIND|SHOW) target [IN|DURING scope] [WHERE expr]
 * [WITH runspec] [LIMIT n] [ORDER BY key]} — and delegates the {@code WHERE} expression to
 * {@link ConditionParser}, the composable matcher grammar.
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
        Condition condition = null;
        RunSpec runSpec = null;
        Integer limit = null;
        OrderBy orderBy = null;

        if (check(Token.TokenType.IN)) {
            scope = parseInScope();
        } else if (check(Token.TokenType.DURING)) {
            scope = parseDuringScope();
        }

        if (match(Token.TokenType.WHERE)) {
            condition = parseCondition();
        }

        if (match(Token.TokenType.WITH)) {
            runSpec = parseRunSpec();
        }

        // LIMIT and ORDER BY may appear in either order.
        while (true) {
            if (match(Token.TokenType.LIMIT)) {
                limit = parseInteger();
            } else if (match(Token.TokenType.ORDER)) {
                expect(Token.TokenType.BY);
                String key = orderKey();
                boolean desc = match(Token.TokenType.DESC);
                if (!desc) {
                    match(Token.TokenType.ASC);
                }
                orderBy = desc ? OrderBy.desc(key) : OrderBy.asc(key);
            } else {
                break;
            }
        }

        if (isFind) {
            return new FindQuery(target, scope, condition, runSpec, limit, orderBy);
        }
        return new ShowQuery(target, scope, condition, runSpec, limit, orderBy);
    }

    private Condition parseCondition() throws ParseException {
        ConditionParser parser = new ConditionParser(tokens, current);
        Condition condition = parser.parseExpression();
        current = parser.position();
        return condition;
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
                    builder.traceMode(RunSpec.TraceMode.valueOf(advance().value().toUpperCase()));
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

    /** An {@code ORDER BY} key — any name-like token (column names like {@code method} lex as keywords). */
    private String orderKey() throws ParseException {
        Token token = peek();
        String value = token.value();
        if (value == null || value.isEmpty() || !Character.isLetter(value.charAt(0))) {
            throw new ParseException("Expected an order key", token.position());
        }
        advance();
        return value;
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
