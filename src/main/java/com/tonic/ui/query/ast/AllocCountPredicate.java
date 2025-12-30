package com.tonic.ui.query.ast;

import com.tonic.ui.core.util.ComparisonOperator;
import java.util.Objects;

/**
 * Matches based on allocation count of a specific type.
 * Example: allocCount("java/lang/String") > 100
 */
public final class AllocCountPredicate implements Predicate {

    private final String typeName;
    private final ComparisonOperator operator;
    private final int threshold;

    public AllocCountPredicate(String typeName, ComparisonOperator operator, int threshold) {
        this.typeName = typeName;
        this.operator = operator;
        this.threshold = threshold;
    }

    public String typeName() {
        return typeName;
    }

    public ComparisonOperator operator() {
        return operator;
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
