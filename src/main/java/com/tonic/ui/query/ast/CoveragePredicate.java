package com.tonic.ui.query.ast;

import java.util.Objects;

/**
 * Matches based on code coverage metrics.
 * Example: coverage > 0.8 or coverage(blockId) > 0
 */
public final class CoveragePredicate implements Predicate {

    private final String blockId;
    private final AllocCountPredicate.ComparisonOp operator;
    private final double threshold;

    public CoveragePredicate(String blockId, AllocCountPredicate.ComparisonOp operator, double threshold) {
        this.blockId = blockId;
        this.operator = operator;
        this.threshold = threshold;
    }

    public String blockId() {
        return blockId;
    }

    public AllocCountPredicate.ComparisonOp operator() {
        return operator;
    }

    public double threshold() {
        return threshold;
    }

    public static CoveragePredicate blockCovered(String blockId) {
        return new CoveragePredicate(blockId, AllocCountPredicate.ComparisonOp.GT, 0);
    }

    public static CoveragePredicate overallGreaterThan(double ratio) {
        return new CoveragePredicate(null, AllocCountPredicate.ComparisonOp.GT, ratio);
    }

    public boolean test(double actualCoverage) {
        switch (operator) {
            case GT: return actualCoverage > threshold;
            case GTE: return actualCoverage >= threshold;
            case LT: return actualCoverage < threshold;
            case LTE: return actualCoverage <= threshold;
            case EQ: return Math.abs(actualCoverage - threshold) < 0.001;
            case NEQ: return Math.abs(actualCoverage - threshold) >= 0.001;
            default: return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CoveragePredicate)) return false;
        CoveragePredicate that = (CoveragePredicate) o;
        return Double.compare(that.threshold, threshold) == 0 &&
               Objects.equals(blockId, that.blockId) &&
               operator == that.operator;
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockId, operator, threshold);
    }

    @Override
    public String toString() {
        return "CoveragePredicate{blockId='" + blockId + "', operator=" + operator +
               ", threshold=" + threshold + "}";
    }
}
