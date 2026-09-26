package com.tonic.script.engine;

import lombok.Getter;

/** A token from the script lexer: its type, text, and source line and column. */
@Getter
public class ScriptToken
{

    /** The kinds of script token: literals, keywords, operators, punctuation, EOF and error. */
    public enum Type
    {
        IDENTIFIER,
        STRING,
        NUMBER,
        BOOLEAN,
        NULL,

        LET,
        CONST,
        IF,
        ELSE,
        RETURN,
        FUNCTION,
        TRUE,
        FALSE,

        FOR,
        WHILE,
        DO,
        BREAK,
        CONTINUE,
        IN,
        OF,

        TRY,
        CATCH,
        FINALLY,
        THROW,

        PLUS,
        MINUS,
        STAR,
        SLASH,
        PERCENT,
        EQUALS,
        EQUALS_EQUALS,
        NOT_EQUALS,
        LESS,
        LESS_EQUALS,
        GREATER,
        GREATER_EQUALS,
        AND,
        OR,
        NOT,
        DOT,
        QUESTION,
        COLON,
        PLUS_PLUS,
        MINUS_MINUS,
        PLUS_EQUALS,
        MINUS_EQUALS,
        STAR_EQUALS,
        SLASH_EQUALS,

        LPAREN,
        RPAREN,
        LBRACE,
        RBRACE,
        LBRACKET,
        RBRACKET,
        COMMA,
        SEMICOLON,
        ARROW,

        EOF,
        ERROR
    }

    private final Type type;
    private final String value;
    private final int line;
    private final int column;

    /**
     * Creates a token.
     *
     * @param type the token kind
     * @param value the token text
     * @param line the 1-based source line
     * @param column the 1-based source column
     */
    public ScriptToken(Type type, String value, int line, int column)
    {
        this.type = type;
        this.value = value;
        this.line = line;
        this.column = column;
    }

    @Override
    public String toString()
    {
        return String.format("%s(%s) at %d:%d", type, value, line, column);
    }

    /**
     * Tells whether the token is any of the given kinds.
     *
     * @param types the kinds to test
     * @return true when the token type is one of them
     */
    public boolean is(Type... types)
    {
        for (Type t : types)
        {
            if (this.type == t) return true;
        }
        return false;
    }
}
