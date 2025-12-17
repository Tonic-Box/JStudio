package com.tonic.ui.script.engine;

import lombok.Getter;

/**
 * Token types for the script lexer.
 */
@Getter
public class ScriptToken {

    public enum Type {
        // Literals
        IDENTIFIER,
        STRING,
        NUMBER,
        BOOLEAN,
        NULL,

        // Keywords
        LET,
        CONST,
        IF,
        ELSE,
        RETURN,
        FUNCTION,
        TRUE,
        FALSE,

        // Loop keywords
        FOR,
        WHILE,
        DO,
        BREAK,
        CONTINUE,
        IN,
        OF,

        // Exception keywords
        TRY,
        CATCH,
        FINALLY,
        THROW,

        // Operators
        PLUS,           // +
        MINUS,          // -
        STAR,           // *
        SLASH,          // /
        PERCENT,        // %
        EQUALS,         // =
        EQUALS_EQUALS,  // ==
        NOT_EQUALS,     // !=
        LESS,           // <
        LESS_EQUALS,    // <=
        GREATER,        // >
        GREATER_EQUALS, // >=
        AND,            // &&
        OR,             // ||
        NOT,            // !
        DOT,            // .
        QUESTION,       // ?
        COLON,          // :
        PLUS_PLUS,      // ++
        MINUS_MINUS,    // --
        PLUS_EQUALS,    // +=
        MINUS_EQUALS,   // -=
        STAR_EQUALS,    // *=
        SLASH_EQUALS,   // /=

        // Punctuation
        LPAREN,         // (
        RPAREN,         // )
        LBRACE,         // {
        RBRACE,         // }
        LBRACKET,       // [
        RBRACKET,       // ]
        COMMA,          // ,
        SEMICOLON,      // ;
        ARROW,          // =>

        // Special
        EOF,
        ERROR
    }

    private final Type type;
    private final String value;
    private final int line;
    private final int column;

    public ScriptToken(Type type, String value, int line, int column) {
        this.type = type;
        this.value = value;
        this.line = line;
        this.column = column;
    }

    @Override
    public String toString() {
        return String.format("%s(%s) at %d:%d", type, value, line, column);
    }

    public boolean is(Type... types) {
        for (Type t : types) {
            if (this.type == t) return true;
        }
        return false;
    }
}
