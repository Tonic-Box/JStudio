package com.tonic.ui.script.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tokenizer for JStudio script language.
 */
public class ScriptLexer {

    private static final Map<String, ScriptToken.Type> KEYWORDS = new HashMap<>();

    static {
        KEYWORDS.put("let", ScriptToken.Type.LET);
        KEYWORDS.put("const", ScriptToken.Type.CONST);
        KEYWORDS.put("if", ScriptToken.Type.IF);
        KEYWORDS.put("else", ScriptToken.Type.ELSE);
        KEYWORDS.put("return", ScriptToken.Type.RETURN);
        KEYWORDS.put("function", ScriptToken.Type.FUNCTION);
        KEYWORDS.put("true", ScriptToken.Type.TRUE);
        KEYWORDS.put("false", ScriptToken.Type.FALSE);
        KEYWORDS.put("null", ScriptToken.Type.NULL);

        KEYWORDS.put("for", ScriptToken.Type.FOR);
        KEYWORDS.put("while", ScriptToken.Type.WHILE);
        KEYWORDS.put("do", ScriptToken.Type.DO);
        KEYWORDS.put("break", ScriptToken.Type.BREAK);
        KEYWORDS.put("continue", ScriptToken.Type.CONTINUE);
        KEYWORDS.put("in", ScriptToken.Type.IN);
        KEYWORDS.put("of", ScriptToken.Type.OF);

        KEYWORDS.put("try", ScriptToken.Type.TRY);
        KEYWORDS.put("catch", ScriptToken.Type.CATCH);
        KEYWORDS.put("finally", ScriptToken.Type.FINALLY);
        KEYWORDS.put("throw", ScriptToken.Type.THROW);
    }

    private final String source;
    private final List<ScriptToken> tokens = new ArrayList<>();
    private int start = 0;
    private int current = 0;
    private int line = 1;
    private int column = 1;
    private final List<String> errors = new ArrayList<>();

    public ScriptLexer(String source) {
        this.source = source != null ? source : "";
    }

    public List<ScriptToken> tokenize() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }

        tokens.add(new ScriptToken(ScriptToken.Type.EOF, "", line, column));
        return tokens;
    }

    public List<String> getErrors() {
        return errors;
    }

    private void scanToken() {
        char c = advance();

        switch (c) {
            case '(': addToken(ScriptToken.Type.LPAREN); break;
            case ')': addToken(ScriptToken.Type.RPAREN); break;
            case '{': addToken(ScriptToken.Type.LBRACE); break;
            case '}': addToken(ScriptToken.Type.RBRACE); break;
            case '[': addToken(ScriptToken.Type.LBRACKET); break;
            case ']': addToken(ScriptToken.Type.RBRACKET); break;
            case ',': addToken(ScriptToken.Type.COMMA); break;
            case ';': addToken(ScriptToken.Type.SEMICOLON); break;
            case '.': addToken(ScriptToken.Type.DOT); break;
            case '+':
                if (match('+')) {
                    addToken(ScriptToken.Type.PLUS_PLUS);
                } else if (match('=')) {
                    addToken(ScriptToken.Type.PLUS_EQUALS);
                } else {
                    addToken(ScriptToken.Type.PLUS);
                }
                break;
            case '-':
                if (match('-')) {
                    addToken(ScriptToken.Type.MINUS_MINUS);
                } else if (match('=')) {
                    addToken(ScriptToken.Type.MINUS_EQUALS);
                } else {
                    addToken(ScriptToken.Type.MINUS);
                }
                break;
            case '*':
                addToken(match('=') ? ScriptToken.Type.STAR_EQUALS : ScriptToken.Type.STAR);
                break;
            case '%': addToken(ScriptToken.Type.PERCENT); break;
            case ':': addToken(ScriptToken.Type.COLON); break;
            case '?': addToken(ScriptToken.Type.QUESTION); break;

            case '/':
                if (match('/')) {
                    // Single-line comment
                    while (peek() != '\n' && !isAtEnd()) advance();
                } else if (match('*')) {
                    // Multi-line comment
                    blockComment();
                } else if (match('=')) {
                    addToken(ScriptToken.Type.SLASH_EQUALS);
                } else {
                    addToken(ScriptToken.Type.SLASH);
                }
                break;

            case '=':
                if (match('>')) {
                    addToken(ScriptToken.Type.ARROW);
                } else if (match('=')) {
                    addToken(ScriptToken.Type.EQUALS_EQUALS);
                } else {
                    addToken(ScriptToken.Type.EQUALS);
                }
                break;

            case '!':
                addToken(match('=') ? ScriptToken.Type.NOT_EQUALS : ScriptToken.Type.NOT);
                break;

            case '<':
                addToken(match('=') ? ScriptToken.Type.LESS_EQUALS : ScriptToken.Type.LESS);
                break;

            case '>':
                addToken(match('=') ? ScriptToken.Type.GREATER_EQUALS : ScriptToken.Type.GREATER);
                break;

            case '&':
                if (match('&')) {
                    addToken(ScriptToken.Type.AND);
                } else {
                    error("Unexpected character '&'. Did you mean '&&'?");
                }
                break;

            case '|':
                if (match('|')) {
                    addToken(ScriptToken.Type.OR);
                } else {
                    error("Unexpected character '|'. Did you mean '||'?");
                }
                break;

            // Whitespace
            case ' ':
            case '\r':
            case '\t':
                break;

            case '\n':
                line++;
                column = 1;
                break;

            // Strings
            case '"': string('"'); break;
            case '\'': string('\''); break;

            default:
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    error("Unexpected character '" + c + "'");
                }
                break;
        }
    }

    private void blockComment() {
        int nesting = 1;
        while (nesting > 0 && !isAtEnd()) {
            if (peek() == '/' && peekNext() == '*') {
                advance();
                advance();
                nesting++;
            } else if (peek() == '*' && peekNext() == '/') {
                advance();
                advance();
                nesting--;
            } else {
                if (peek() == '\n') {
                    line++;
                    column = 0;
                }
                advance();
            }
        }
    }

    private void string(char quote) {
        int startLine = line;
        int startCol = column;
        StringBuilder sb = new StringBuilder();

        while (peek() != quote && !isAtEnd()) {
            if (peek() == '\n') {
                line++;
                column = 0;
            }
            if (peek() == '\\') {
                advance();
                char escaped = advance();
                switch (escaped) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case '\\': sb.append('\\'); break;
                    case '"': sb.append('"'); break;
                    case '\'': sb.append('\''); break;
                    default: sb.append(escaped); break;
                }
            } else {
                sb.append(advance());
            }
        }

        if (isAtEnd()) {
            error("Unterminated string starting at " + startLine + ":" + startCol);
            return;
        }

        advance(); // Closing quote
        tokens.add(new ScriptToken(ScriptToken.Type.STRING, sb.toString(), startLine, startCol));
    }

    private void number() {
        while (isDigit(peek())) advance();

        // Decimal
        if (peek() == '.' && isDigit(peekNext())) {
            advance(); // Consume '.'
            while (isDigit(peek())) advance();
        }

        // Exponent
        if (peek() == 'e' || peek() == 'E') {
            advance();
            if (peek() == '+' || peek() == '-') advance();
            while (isDigit(peek())) advance();
        }

        addToken(ScriptToken.Type.NUMBER);
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) advance();

        String text = source.substring(start, current);
        ScriptToken.Type type = KEYWORDS.getOrDefault(text, ScriptToken.Type.IDENTIFIER);
        addToken(type);
    }

    private char advance() {
        char c = source.charAt(current);
        current++;
        column++;
        return c;
    }

    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;
        current++;
        column++;
        return true;
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') ||
               (c >= 'A' && c <= 'Z') ||
               c == '_' || c == '$';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private void addToken(ScriptToken.Type type) {
        String text = source.substring(start, current);
        tokens.add(new ScriptToken(type, text, line, column - text.length()));
    }

    private void error(String message) {
        errors.add("Line " + line + ", column " + column + ": " + message);
        tokens.add(new ScriptToken(ScriptToken.Type.ERROR, message, line, column));
    }
}
