package com.tonic.ui.query.ast;

import com.tonic.ui.core.util.ComparisonOperator;
import java.util.Objects;

/**
 * Matches based on allocation count of a specific type.
 * Example: allocCount("java/lang/String") > 100
 */
public final class AllocCountPredicate implements Predicate {

    @Deprecated
    public enum ComparisonOp {
        GT, GTE, LT, LTE, EQ, NEQ;

        public ComparisonOperator toOperator() {
            switch (this) {
                case GT: return ComparisonOperator.GT;
                case GTE: return ComparisonOperator.GTE;
                case LT: return ComparisonOperator.LT;
                case LTE: return ComparisonOperator.LTE;
                case EQ: return ComparisonOperator.EQ;
                case NEQ: return ComparisonOperator.NEQ;
                default: throw new IllegalStateException();
            }
        }

        public static ComparisonOp fromOperator(ComparisonOperator op) {
            switch (op) {
                case GT: return GT;
                case GTE: return GTE;
                case LT: return LT;
                case LTE: return LTE;
                case EQ: return EQ;
                case NEQ: return NEQ;
                default: throw new IllegalStateException();
            }
        }
    }

    private final String typeName;
    private final ComparisonOperator operator;
    private final int threshold;

    public AllocCountPredicate(String typeName, ComparisonOperator operator, int threshold) {
        this.typeName = typeName;
        this.operator = operator;
        this.threshold = threshold;
    }

    @Deprecated
    public AllocCountPredicate(String typeName, ComparisonOp operator, int threshold) {
        this(typeName, operator.toOperator(), threshold);
    }

    public String typeName() {
        return typeName;
    }

    public ComparisonOperator operator() {
        return operator;
    }

    @Deprecated
    public ComparisonOp legacyOperator() {
        return ComparisonOp.fromOperator(operator);
    }

    public int threshold() {
        return threshold;
    }

    public static AllocCountPredicate greaterThan(String type, int count) {
        return new AllocCountPredicate(type, ComparisonOperator.GT, count);
    }

    public static AllocCountPredicate atLeast(String type, int count) {
        return new AllocCountPredicate(type, ComparisonOperator.GTE, count);
    }

    public boolean test(int actualCount) {
        return operator.test(actualCount, threshold);
    }

    @Override
    public <T> T accept(PredicateVisitor<T> visitor) {
        return visitor.visitAllocCount(this);
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
