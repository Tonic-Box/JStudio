package com.tonic.ui.query.ast;

import com.tonic.ui.core.util.ComparisonOperator;
import java.util.Objects;

/**
 * Matches based on instruction count during execution.
 * Example: instructionCount > 50000
 */
public final class InstructionCountPredicate implements Predicate {

    private final ComparisonOperator operator;
    private final long threshold;

    public InstructionCountPredicate(ComparisonOperator operator, long threshold) {
        this.operator = operator;
        this.threshold = threshold;
    }

    public ComparisonOperator operator() {
        return operator;
    }

    public long threshold() {
        return threshold;
    }

    public static InstructionCountPredicate greaterThan(long count) {
        return new InstructionCountPredicate(ComparisonOperator.GT, count);
    }

    public static InstructionCountPredicate lessThan(long count) {
        return new InstructionCountPredicate(ComparisonOperator.LT, count);
    }

    public boolean test(long actualCount) {
        return operator.test(actualCount, threshold);
    }

    @Override
    public <T> T accept(PredicateVisitor<T> visitor) {
        return visitor.visitInstructionCount(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InstructionCountPredicate)) return false;
        InstructionCountPredicate that = (InstructionCountPredicate) o;
        return threshold == that.threshold && operator == that.operator;
    }

    @Override
    public int hashCode() {
        return Objects.hash(operator, threshold);
    }

    @Override
    public String toString() {
        return "InstructionCountPredicate{operator=" + operator + ", threshold=" + threshold + "}";
    }
}
