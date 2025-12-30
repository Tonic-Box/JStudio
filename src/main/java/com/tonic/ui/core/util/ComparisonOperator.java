package com.tonic.ui.core.util;

public enum ComparisonOperator {
    GT(">"),
    GTE(">="),
    LT("<"),
    LTE("<="),
    EQ("=="),
    NEQ("!=");

    private final String symbol;

    ComparisonOperator(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    public boolean test(int left, int right) {
        switch (this) {
            case GT: return left > right;
            case GTE: return left >= right;
            case LT: return left < right;
            case LTE: return left <= right;
            case EQ: return left == right;
            case NEQ: return left != right;
            default: return false;
        }
    }

    public boolean test(long left, long right) {
        switch (this) {
            case GT: return left > right;
            case GTE: return left >= right;
            case LT: return left < right;
            case LTE: return left <= right;
            case EQ: return left == right;
            case NEQ: return left != right;
            default: return false;
        }
    }

    public boolean test(double left, double right) {
        switch (this) {
            case GT: return left > right;
            case GTE: return left >= right;
            case LT: return left < right;
            case LTE: return left <= right;
            case EQ: return Double.compare(left, right) == 0;
            case NEQ: return Double.compare(left, right) != 0;
            default: return false;
        }
    }

    public static ComparisonOperator fromSymbol(String symbol) {
        for (ComparisonOperator op : values()) {
            if (op.symbol.equals(symbol)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown comparison operator: " + symbol);
    }

    @Override
    public String toString() {
        return symbol;
    }
}
