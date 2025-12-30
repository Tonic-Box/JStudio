package com.tonic.ui.query.parser;

import java.util.Objects;

/**
 * Token produced by QueryLexer.
 */
public final class Token {

    public enum TokenType {
        FIND,
        SHOW,
        WHERE,
        IN,
        DURING,
        BETWEEN,
        AND,
        OR,
        NOT,
        BEFORE,
        AFTER,
        WITH,
        LIMIT,
        ORDER,
        BY,
        ASC,
        DESC,
        OF,
        ALL,
        CLASS,
        METHOD,
        CLINIT,
        BECOMES,
        NON_NULL,
        NULL,

        METHODS,
        CLASSES,
        PATHS,
        EVENTS,
        STRINGS,
        OBJECTS,

        CALLS,
        ALLOC_COUNT,
        WRITES_FIELD,
        READS_FIELD,
        FIELD,
        CONTAINS_STRING,
        THROWS,
        INSTRUCTION_COUNT,
        COVERAGE,

        ARG_ANY,
        ARG_LITERAL,
        ARG_DYNAMIC,
        ARG_FIELD,
        ARG_LOCAL,
        ARG_CALL,

        IDENTIFIER,
        STRING,
        REGEX,
        NUMBER,

        LPAREN,
        RPAREN,
        COMMA,
        DOT,
        COLON,
        GT,
        GTE,
        LT,
        LTE,
        EQ,
        NEQ,

        EOF
    }

    private final TokenType type;
    private final String value;
    private final int position;

    public Token(TokenType type, String value, int position) {
        this.type = type;
        this.value = value;
        this.position = position;
    }

    public TokenType type() {
        return type;
    }

    public String value() {
        return value;
    }

    public int position() {
        return position;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Token)) return false;
        Token token = (Token) o;
        return position == token.position &&
               type == token.type &&
               Objects.equals(value, token.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value, position);
    }

    @Override
    public String toString() {
        return type + (value != null ? "(" + value + ")" : "") + "@" + position;
    }
}
