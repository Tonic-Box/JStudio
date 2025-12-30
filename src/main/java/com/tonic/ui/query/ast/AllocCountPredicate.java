package com.tonic.ui.query.ast;

import java.util.Objects;

/**
 * Matches based on allocation count of a specific type.
 * Example: allocCount("java/lang/String") > 100
 */
public final class AllocCountPredicate implements Predicate {

    public enum ComparisonOp {
        GT, GTE, LT, LTE, EQ, NEQ
    }

    private final String typeName;
    private final ComparisonOp operator;
    private final int threshold;

    public AllocCountPredicate(String typeName, ComparisonOp operator, int threshold) {
        this.typeName = typeName;
        this.operator = operator;
        this.threshold = threshold;
    }

    public String typeName() {
        return typeName;
    }

    public ComparisonOp operator() {
        return operator;
    }

    public int threshold() {
        return threshold;
    }

    public static AllocCountPredicate greaterThan(String type, int count) {
        return new AllocCountPredicate(type, ComparisonOp.GT, count);
    }

    public static AllocCountPredicate atLeast(String type, int count) {
        return new AllocCountPredicate(type, ComparisonOp.GTE, count);
    }

    public boolean test(int actualCount) {
        switch (operator) {
            case GT: return actualCount > threshold;
            case GTE: return actualCount >= threshold;
            case LT: return actualCount < threshold;
            case LTE: return actualCount <= threshold;
            case EQ: return actualCount == threshold;
            case NEQ: return actualCount != threshold;
            default: return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AllocCountPredicate)) return false;
        AllocCountPredicate that = (AllocCountPredicate) o;
        return threshold == that.threshold &&
               Objects.equals(typeName, that.typeName) &&
               operator == that.operator;
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeName, operator, threshold);
    }

    @Override
    public String toString() {
        return "AllocCountPredicate{typeName='" + typeName + "', operator=" + operator +
               ", threshold=" + threshold + "}";
    }
}
