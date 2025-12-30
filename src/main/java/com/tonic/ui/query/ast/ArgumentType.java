package com.tonic.ui.query.ast;

public enum ArgumentType {
    ANY,
    LITERAL,
    DYNAMIC,
    FIELD,
    LOCAL,
    CALL;

    public boolean matches(ArgumentType actual) {
        if (this == ANY) return true;
        if (this == DYNAMIC) {
            return actual == FIELD || actual == LOCAL || actual == CALL;
        }
        return this == actual;
    }

    public static ArgumentType fromString(String s) {
        if (s == null) return ANY;
        switch (s.toLowerCase()) {
            case "any": return ANY;
            case "literal": return LITERAL;
            case "dynamic": return DYNAMIC;
            case "field": return FIELD;
            case "local": return LOCAL;
            case "call": return CALL;
            default: return ANY;
        }
    }
}
