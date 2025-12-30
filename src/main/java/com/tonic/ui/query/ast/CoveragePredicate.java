package com.tonic.ui.query.ast;

import com.tonic.ui.core.util.ComparisonOperator;
import java.util.Objects;

/**
 * Matches based on code coverage metrics.
 * Example: coverage > 0.8 or coverage(blockId) > 0
 */
public final class CoveragePredicate implements Predicate {

    private final String blockId;
    private final ComparisonOperator operator;
    private final double threshold;

    public CoveragePredicate(String blockId, ComparisonOperator operator, double threshold) {
        this.blockId = blockId;
        this.operator = operator;
        this.threshold = threshold;
    }

    @Deprecated
    public CoveragePredicate(String blockId, AllocCountPredicate.ComparisonOp operator, double threshold) {
        this(blockId, operator.toOperator(), threshold);
    }

    public String blockId() {
        return blockId;
    }

    public ComparisonOperator operator() {
        return operator;
    }

    public double threshold() {
        return threshold;
    }

    public static CoveragePredicate blockCovered(String blockId) {
        return new CoveragePredicate(blockId, ComparisonOperator.GT, 0);
    }

    public static CoveragePredicate overallGreaterThan(double ratio) {
        return new CoveragePredicate(null, ComparisonOperator.GT, ratio);
    }

    public boolean test(double actualCoverage) {
        return operator.test(actualCoverage, threshold);
    }

    @Override
    public <T> T accept(PredicateVisitor<T> visitor) {
        return visitor.visitCoverage(this);
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
