package com.tonic.ui.query.parser;

import com.tonic.ui.query.ast.Accessor;
import com.tonic.ui.query.ast.Condition;
import com.tonic.ui.query.ast.Operand;
import com.tonic.ui.query.ast.Step;
import com.tonic.ui.query.value.Operator;
import com.tonic.ui.query.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Recursive-descent parser for the composable {@code WHERE} expression language. Operates over a
 * shared token list with an internal cursor so the top-level query parser can hand it the tokens
 * after {@code WHERE} and resume at {@link #position()}.
 *
 * <p>Closed grammar, open vocabulary: structural keywords (and/or/not/has/any/all/none/count/where)
 * are recognized by spelling, while atom names ({@code value}, {@code arg}, {@code call}, …) are just
 * identifiers resolved later by the attribute registry — so adding vocabulary never touches the parser.
 */
public final class ConditionParser {

    private static final Set<String> PRIMITIVES =
            Set.of("int", "long", "double", "float", "boolean", "byte", "char", "short", "void");

    private static final Map<String, Operator> WORD_OPERATORS = Map.of(
            "matches", Operator.MATCHES,
            "contains", Operator.CONTAINS,
            "startswith", Operator.STARTS_WITH,
            "endswith", Operator.ENDS_WITH,
            "in", Operator.IN,
            "flowsto", Operator.FLOWS_TO,
            "flowsfrom", Operator.FLOWS_FROM);

    private final List<Token> tokens;
    private int current;

    public ConditionParser(List<Token> tokens, int start) {
        this.tokens = tokens;
        this.current = start;
    }

    /** Parses a complete standalone expression string. */
    public static Condition parse(String expression) throws ParseException {
        List<Token> tokens = new QueryLexer(expression).tokenize();
        ConditionParser parser = new ConditionParser(tokens, 0);
        Condition condition = parser.parseExpression();
        if (parser.peek().type() != Token.TokenType.EOF) {
            throw new ParseException("Unexpected token: " + parser.peek().value(), parser.peek().position());
        }
        return condition;
    }

    public int position() {
        return current;
    }

    public Condition parseExpression() throws ParseException {
        return parseOr();
    }

    private Condition parseOr() throws ParseException {
        Condition first = parseAnd();
        if (!isKeyword("or")) {
            return first;
        }
        List<Condition> terms = new ArrayList<>();
        terms.add(first);
        while (matchKeyword("or")) {
            terms.add(parseAnd());
        }
        return new Condition.Or(terms);
    }

    private Condition parseAnd() throws ParseException {
        Condition first = parseNot();
        if (!isKeyword("and")) {
            return first;
        }
        List<Condition> terms = new ArrayList<>();
        terms.add(first);
        while (matchKeyword("and")) {
            terms.add(parseNot());
        }
        return new Condition.And(terms);
    }

    private Condition parseNot() throws ParseException {
        if (matchKeyword("not")) {
            return new Condition.Not(parseNot());
        }
        return parseAtom();
    }

    private Condition parseAtom() throws ParseException {
        if (peek().type() == Token.TokenType.LPAREN) {
            advance();
            Condition inner = parseExpression();
            expect(Token.TokenType.RPAREN);
            return new Condition.Group(inner);
        }
        if (isAnyKeyword("has", "any", "all", "none")) {
            return parseQuantifier();
        }
        if (isKeyword("count")) {
            return parseCount();
        }
        if (isAnyKeyword("sequence", "seq")) {
            return parseSequence();
        }
        return parseComparison();
    }

    private Condition parseSequence() throws ParseException {
        advance(); // SEQUENCE | SEQ
        expect(Token.TokenType.LBRACKET);
        List<Condition.Sequence.Element> elements = new ArrayList<>();
        if (peek().type() != Token.TokenType.RBRACKET) {
            elements.add(parseSequenceElement());
            while (peek().type() == Token.TokenType.COMMA) {
                advance();
                elements.add(parseSequenceElement());
            }
        }
        expect(Token.TokenType.RBRACKET);
        if (elements.isEmpty()) {
            throw new ParseException("SEQUENCE [...] needs at least one step", peek().position());
        }
        return new Condition.Sequence(elements);
    }

    private Condition.Sequence.Element parseSequenceElement() throws ParseException {
        if (peek().type() == Token.TokenType.DOT) {
            advance();
            expectAny("'.' to complete the '..' gap", Token.TokenType.DOT);
            return Condition.Sequence.Element.gap();
        }
        Condition matcher;
        if (peek().type() == Token.TokenType.LPAREN) {
            advance();
            matcher = parseExpression();
            expect(Token.TokenType.RPAREN);
        } else if ("_".equals(peek().value())) {
            advance();
            matcher = Condition.True.INSTANCE;
        } else {
            String mnemonic = consumeName().toLowerCase();
            matcher = new Condition.Comparison(
                    new Accessor(List.of(Step.of("opcode"))),
                    Operator.EQ,
                    Operand.literal(Value.of(mnemonic)));
        }
        return applyRepetition(matcher);
    }

    private Condition.Sequence.Element applyRepetition(Condition matcher) throws ParseException {
        int min = 1;
        int max = 1;
        switch (peek().type()) {
            case STAR:
                advance();
                min = 0;
                max = Condition.Sequence.Element.UNBOUNDED;
                break;
            case PLUS:
                advance();
                max = Condition.Sequence.Element.UNBOUNDED;
                break;
            case LBRACE:
                advance();
                min = Integer.parseInt(expectAny("a repetition count", Token.TokenType.NUMBER).value());
                max = min;
                if (peek().type() == Token.TokenType.COMMA) {
                    advance();
                    if (peek().type() == Token.TokenType.NUMBER) {
                        max = Integer.parseInt(advance().value());
                    } else {
                        max = Condition.Sequence.Element.UNBOUNDED;
                    }
                }
                expect(Token.TokenType.RBRACE);
                break;
            default:
                break;
        }
        return new Condition.Sequence.Element(matcher, min, max);
    }

    private Condition parseQuantifier() throws ParseException {
        String word = advance().value().toLowerCase();
        Condition.Quant quant = "none".equals(word) ? Condition.Quant.NONE
                : "all".equals(word) ? Condition.Quant.ALL : Condition.Quant.ANY;
        Accessor stream = parseAccessor();
        Condition body = null;
        if (matchKeyword("where")) {
            expect(Token.TokenType.LPAREN);
            body = parseExpression();
            expect(Token.TokenType.RPAREN);
        }
        return new Condition.Quantifier(quant, stream, body);
    }

    private Condition parseCount() throws ParseException {
        advance(); // count
        expect(Token.TokenType.LPAREN);
        Accessor stream = parseAccessor();
        Condition body = null;
        if (matchKeyword("where")) {
            expect(Token.TokenType.LPAREN);
            body = parseExpression();
            expect(Token.TokenType.RPAREN);
        }
        expect(Token.TokenType.RPAREN);
        Operator op = parseOperator();
        Operand operand = parseOperand();
        return new Condition.Count(stream, body, op, operand);
    }

    private Condition parseComparison() throws ParseException {
        Accessor accessor = parseAccessor();
        Operator op = tryParseOperator();
        if (op == null) {
            return new Condition.Comparison(accessor, null, null);
        }
        Operand operand = op.isRelational() ? Operand.accessor(parseAccessor()) : parseOperand();
        return new Condition.Comparison(accessor, op, operand);
    }

    private Accessor parseAccessor() throws ParseException {
        List<Step> steps = new ArrayList<>();
        steps.add(parseSegment());
        while (peek().type() == Token.TokenType.DOT) {
            advance();
            steps.add(parseSegment());
        }
        return new Accessor(steps);
    }

    private Step parseSegment() throws ParseException {
        String name = consumeName();
        if (peek().type() == Token.TokenType.LPAREN) {
            advance();
            Token num = expectAny("index", Token.TokenType.NUMBER);
            expect(Token.TokenType.RPAREN);
            return Step.indexed(name, Integer.parseInt(num.value()));
        }
        return Step.of(name);
    }

    private Operator parseOperator() throws ParseException {
        Operator op = tryParseOperator();
        if (op == null) {
            throw new ParseException("Expected an operator", peek().position());
        }
        return op;
    }

    private Operator tryParseOperator() {
        switch (peek().type()) {
            case EQ: advance(); return Operator.EQ;
            case NEQ: advance(); return Operator.NEQ;
            case LT: advance(); return Operator.LT;
            case LTE: advance(); return Operator.LTE;
            case GT: advance(); return Operator.GT;
            case GTE: advance(); return Operator.GTE;
            case IN: advance(); return Operator.IN;
            default:
                break;
        }
        String value = peek().value();
        if (value != null) {
            Operator word = WORD_OPERATORS.get(value.toLowerCase());
            if (word != null) {
                advance();
                return word;
            }
        }
        return null;
    }

    private Operand parseOperand() throws ParseException {
        Token t = peek();
        switch (t.type()) {
            case STRING:
                advance();
                return Operand.literal(Value.of(t.value()));
            case REGEX:
                advance();
                return Operand.literal(Value.ofRegex(Pattern.compile(t.value())));
            case NUMBER:
                advance();
                return Operand.literal(number(t.value()));
            default:
                return identifierOperand();
        }
    }

    private Operand identifierOperand() throws ParseException {
        String name = consumeName();
        String lower = name.toLowerCase();
        if ("true".equals(lower) || "false".equals(lower)) {
            return Operand.literal(Value.of(Boolean.parseBoolean(lower)));
        }
        if ("null".equals(lower)) {
            return Operand.literal(Value.ofNull());
        }
        if (PRIMITIVES.contains(lower)) {
            return Operand.literal(Value.ofType(lower));
        }
        // Dotted qualified name => a class type (e.g. java.lang.String).
        if (peek().type() == Token.TokenType.DOT) {
            StringBuilder sb = new StringBuilder(name);
            while (peek().type() == Token.TokenType.DOT) {
                advance();
                sb.append('.').append(consumeName());
            }
            return Operand.literal(Value.ofType(sb.toString()));
        }
        // A bare token: a kind/enum word (static, virtual, literal, read, write, ...).
        return Operand.literal(Value.of(name));
    }

    private Value number(String raw) {
        String v = raw.replace("_", "");
        if (v.contains(".")) {
            return Value.of(Double.parseDouble(v));
        }
        return Value.of(Long.parseLong(v));
    }

    // ---- token helpers ----------------------------------------------------

    private String consumeName() throws ParseException {
        Token t = peek();
        if (t.value() != null && !t.value().isEmpty() && isNameLike(t.value())) {
            advance();
            return t.value();
        }
        throw new ParseException("Expected a name, got " + t.type(), t.position());
    }

    private static boolean isNameLike(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '<' && c != '>') {
                return false;
            }
        }
        return Character.isLetter(s.charAt(0)) || s.charAt(0) == '_' || s.charAt(0) == '<';
    }

    private boolean isKeyword(String word) {
        return peek().value() != null && peek().value().equalsIgnoreCase(word);
    }

    private boolean isAnyKeyword(String... words) {
        for (String w : words) {
            if (isKeyword(w)) return true;
        }
        return false;
    }

    private boolean matchKeyword(String word) {
        if (isKeyword(word)) {
            advance();
            return true;
        }
        return false;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token advance() {
        Token t = tokens.get(current);
        if (t.type() != Token.TokenType.EOF) {
            current++;
        }
        return t;
    }

    private void expect(Token.TokenType type) throws ParseException {
        if (peek().type() != type) {
            throw new ParseException("Expected " + type + ", got " + peek().type(), peek().position());
        }
        advance();
    }

    private Token expectAny(String what, Token.TokenType type) throws ParseException {
        if (peek().type() != type) {
            throw new ParseException("Expected " + what, peek().position());
        }
        return advance();
    }
}
