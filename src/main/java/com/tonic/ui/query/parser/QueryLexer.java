package com.tonic.ui.query.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tokenizer for the query DSL.
 */
public class QueryLexer {

    private static final Map<String, Token.TokenType> KEYWORDS = Map.ofEntries(
        Map.entry("find", Token.TokenType.FIND),
        Map.entry("show", Token.TokenType.SHOW),
        Map.entry("where", Token.TokenType.WHERE),
        Map.entry("in", Token.TokenType.IN),
        Map.entry("during", Token.TokenType.DURING),
        Map.entry("between", Token.TokenType.BETWEEN),
        Map.entry("and", Token.TokenType.AND),
        Map.entry("or", Token.TokenType.OR),
        Map.entry("not", Token.TokenType.NOT),
        Map.entry("before", Token.TokenType.BEFORE),
        Map.entry("after", Token.TokenType.AFTER),
        Map.entry("with", Token.TokenType.WITH),
        Map.entry("limit", Token.TokenType.LIMIT),
        Map.entry("order", Token.TokenType.ORDER),
        Map.entry("by", Token.TokenType.BY),
        Map.entry("asc", Token.TokenType.ASC),
        Map.entry("desc", Token.TokenType.DESC),
        Map.entry("of", Token.TokenType.OF),
        Map.entry("all", Token.TokenType.ALL),
        Map.entry("class", Token.TokenType.CLASS),
        Map.entry("method", Token.TokenType.METHOD),
        Map.entry("clinit", Token.TokenType.CLINIT),
        Map.entry("<clinit>", Token.TokenType.CLINIT),
        Map.entry("becomes", Token.TokenType.BECOMES),
        Map.entry("non-null", Token.TokenType.NON_NULL),
        Map.entry("null", Token.TokenType.NULL),

        Map.entry("methods", Token.TokenType.METHODS),
        Map.entry("classes", Token.TokenType.CLASSES),
        Map.entry("paths", Token.TokenType.PATHS),
        Map.entry("events", Token.TokenType.EVENTS),
        Map.entry("strings", Token.TokenType.STRINGS),
        Map.entry("objects", Token.TokenType.OBJECTS),

        Map.entry("calls", Token.TokenType.CALLS),
        Map.entry("alloccount", Token.TokenType.ALLOC_COUNT),
        Map.entry("alloc_count", Token.TokenType.ALLOC_COUNT),
        Map.entry("writesfield", Token.TokenType.WRITES_FIELD),
        Map.entry("writes_field", Token.TokenType.WRITES_FIELD),
        Map.entry("readsfield", Token.TokenType.READS_FIELD),
        Map.entry("reads_field", Token.TokenType.READS_FIELD),
        Map.entry("field", Token.TokenType.FIELD),
        Map.entry("containsstring", Token.TokenType.CONTAINS_STRING),
        Map.entry("contains_string", Token.TokenType.CONTAINS_STRING),
        Map.entry("throws", Token.TokenType.THROWS),
        Map.entry("instructioncount", Token.TokenType.INSTRUCTION_COUNT),
        Map.entry("instruction_count", Token.TokenType.INSTRUCTION_COUNT),
        Map.entry("coverage", Token.TokenType.COVERAGE),

        Map.entry("any", Token.TokenType.ARG_ANY),
        Map.entry("literal", Token.TokenType.ARG_LITERAL),
        Map.entry("dynamic", Token.TokenType.ARG_DYNAMIC),
        Map.entry("dynamicarg", Token.TokenType.ARG_DYNAMIC),
        Map.entry("fieldarg", Token.TokenType.ARG_FIELD),
        Map.entry("localarg", Token.TokenType.ARG_LOCAL),
        Map.entry("callarg", Token.TokenType.ARG_CALL)
    );

    private final String input;
    private int position;
    private final List<Token> tokens;

    public QueryLexer(String input) {
        this.input = input;
        this.position = 0;
        this.tokens = new ArrayList<>();
    }

    public List<Token> tokenize() throws ParseException {
        tokens.clear();
        position = 0;

        while (position < input.length()) {
            skipWhitespace();
            if (position >= input.length()) {
                break;
            }

            char c = peek();

            if (c == '"' || c == '\'') {
                tokens.add(readString());
            } else if (c == '/') {
                tokens.add(readRegex());
            } else if (c == '(') {
                tokens.add(new Token(Token.TokenType.LPAREN, "(", position++));
            } else if (c == ')') {
                tokens.add(new Token(Token.TokenType.RPAREN, ")", position++));
            } else if (c == ',') {
                tokens.add(new Token(Token.TokenType.COMMA, ",", position++));
            } else if (c == '.') {
                tokens.add(new Token(Token.TokenType.DOT, ".", position++));
            } else if (c == ':') {
                tokens.add(new Token(Token.TokenType.COLON, ":", position++));
            } else if (c == '>') {
                tokens.add(readComparison());
            } else if (c == '<') {
                if (position + 6 < input.length() &&
                    input.substring(position, position + 8).equalsIgnoreCase("<clinit>")) {
                    tokens.add(new Token(Token.TokenType.CLINIT, "<clinit>", position));
                    position += 8;
                } else {
                    tokens.add(readComparison());
                }
            } else if (c == '=' || c == '!') {
                tokens.add(readComparison());
            } else if (Character.isDigit(c) || (c == '-' && position + 1 < input.length() && Character.isDigit(input.charAt(position + 1)))) {
                tokens.add(readNumber());
            } else if (Character.isLetter(c) || c == '_') {
                tokens.add(readIdentifier());
            } else {
                throw new ParseException("Unexpected character: " + c, position);
            }
        }

        tokens.add(new Token(Token.TokenType.EOF, null, position));
        return tokens;
    }

    private char peek() {
        return input.charAt(position);
    }

    private char advance() {
        return input.charAt(position++);
    }

    private void skipWhitespace() {
        while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
            position++;
        }
    }

    private Token readString() throws ParseException {
        int start = position;
        char quote = advance();
        StringBuilder sb = new StringBuilder();

        while (position < input.length() && peek() != quote) {
            if (peek() == '\\' && position + 1 < input.length()) {
                advance();
                char escaped = advance();
                char replacement;
                switch (escaped) {
                    case 'n': replacement = '\n'; break;
                    case 't': replacement = '\t'; break;
                    case 'r': replacement = '\r'; break;
                    case '\\': replacement = '\\'; break;
                    case '"': replacement = '"'; break;
                    case '\'': replacement = '\''; break;
                    default: replacement = escaped; break;
                }
                sb.append(replacement);
            } else {
                sb.append(advance());
            }
        }

        if (position >= input.length()) {
            throw new ParseException("Unterminated string", start);
        }
        advance();
        return new Token(Token.TokenType.STRING, sb.toString(), start);
    }

    private Token readRegex() throws ParseException {
        int start = position;
        advance();
        StringBuilder sb = new StringBuilder();

        while (position < input.length() && peek() != '/') {
            if (peek() == '\\' && position + 1 < input.length()) {
                sb.append(advance());
                sb.append(advance());
            } else {
                sb.append(advance());
            }
        }

        if (position >= input.length()) {
            throw new ParseException("Unterminated regex", start);
        }
        advance();

        String flags = "";
        while (position < input.length() && Character.isLetter(peek())) {
            flags += advance();
        }

        String pattern = sb.toString();
        if (!flags.isEmpty()) {
            pattern = "(?" + flags + ")" + pattern;
        }
        return new Token(Token.TokenType.REGEX, pattern, start);
    }

    private Token readNumber() {
        int start = position;
        StringBuilder sb = new StringBuilder();

        if (peek() == '-') {
            sb.append(advance());
        }

        while (position < input.length() && (Character.isDigit(peek()) || peek() == '_')) {
            char c = advance();
            if (c != '_') {
                sb.append(c);
            }
        }

        if (position < input.length() && peek() == '.') {
            sb.append(advance());
            while (position < input.length() && Character.isDigit(peek())) {
                sb.append(advance());
            }
        }

        return new Token(Token.TokenType.NUMBER, sb.toString(), start);
    }

    private Token readIdentifier() {
        int start = position;
        StringBuilder sb = new StringBuilder();

        while (position < input.length() &&
               (Character.isLetterOrDigit(peek()) || peek() == '_' || peek() == '-' ||
                (peek() == '<' || peek() == '>'))) {
            sb.append(advance());
        }

        String word = sb.toString();
        String lower = word.toLowerCase();

        Token.TokenType type = KEYWORDS.get(lower);
        if (type != null) {
            return new Token(type, word, start);
        }

        return new Token(Token.TokenType.IDENTIFIER, word, start);
    }

    private Token readComparison() throws ParseException {
        int start = position;
        char c = advance();

        if (c == '>' && position < input.length() && peek() == '=') {
            advance();
            return new Token(Token.TokenType.GTE, ">=", start);
        }
        if (c == '>') {
            return new Token(Token.TokenType.GT, ">", start);
        }
        if (c == '<' && position < input.length() && peek() == '=') {
            advance();
            return new Token(Token.TokenType.LTE, "<=", start);
        }
        if (c == '<') {
            return new Token(Token.TokenType.LT, "<", start);
        }
        if (c == '=' && position < input.length() && peek() == '=') {
            advance();
            return new Token(Token.TokenType.EQ, "==", start);
        }
        if (c == '!') {
            if (position < input.length() && peek() == '=') {
                advance();
                return new Token(Token.TokenType.NEQ, "!=", start);
            }
            throw new ParseException("Expected '=' after '!'", position);
        }

        throw new ParseException("Unexpected comparison operator", start);
    }
}
