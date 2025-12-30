package com.tonic.ui.query.ast;

import java.util.Objects;

/**
 * Matches based on instruction count during execution.
 * Example: instructionCount > 50000
 */
public final class InstructionCountPredicate implements Predicate {

    private final AllocCountPredicate.ComparisonOp operator;
    private final long threshold;

    public InstructionCountPredicate(AllocCountPredicate.ComparisonOp operator, long threshold) {
        this.operator = operator;
        this.threshold = threshold;
    }

    public AllocCountPredicate.ComparisonOp operator() {
        return operator;
    }

    public long threshold() {
        return threshold;
    }

    public static InstructionCountPredicate greaterThan(long count) {
        return new InstructionCountPredicate(AllocCountPredicate.ComparisonOp.GT, count);
    }

    public static InstructionCountPredicate lessThan(long count) {
        return new InstructionCountPredicate(AllocCountPredicate.ComparisonOp.LT, count);
    }

    public boolean test(long actualCount) {
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
