package com.tonic.ui.query.ast;

import java.util.Objects;

/**
 * Execution configuration for queries that require running code.
 */
public final class RunSpec {

    public enum TraceMode {
        NONE,
        RING,
        FULL
    }

    private final int seeds;
    private final int maxInstructions;
    private final int maxDepth;
    private final TraceMode traceMode;
    private final int timeBudgetMs;

    public RunSpec(int seeds, int maxInstructions, int maxDepth,
                   TraceMode traceMode, int timeBudgetMs) {
        this.seeds = seeds;
        this.maxInstructions = maxInstructions;
        this.maxDepth = maxDepth;
        this.traceMode = traceMode;
        this.timeBudgetMs = timeBudgetMs;
    }

    public int seeds() {
        return seeds;
    }

    public int maxInstructions() {
        return maxInstructions;
    }

    public int maxDepth() {
        return maxDepth;
    }

    public TraceMode traceMode() {
        return traceMode;
    }

    public int timeBudgetMs() {
        return timeBudgetMs;
    }

    public static final RunSpec DEFAULT = new RunSpec(
        10,
        100_000,
        50,
        TraceMode.RING,
        60_000
    );

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RunSpec)) return false;
        RunSpec runSpec = (RunSpec) o;
        return seeds == runSpec.seeds &&
               maxInstructions == runSpec.maxInstructions &&
               maxDepth == runSpec.maxDepth &&
               timeBudgetMs == runSpec.timeBudgetMs &&
               traceMode == runSpec.traceMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(seeds, maxInstructions, maxDepth, traceMode, timeBudgetMs);
    }

    @Override
    public String toString() {
        return "RunSpec{seeds=" + seeds + ", maxInstructions=" + maxInstructions +
               ", maxDepth=" + maxDepth + ", traceMode=" + traceMode +
               ", timeBudgetMs=" + timeBudgetMs + "}";
    }

    public static class Builder {
        private int seeds = 10;
        private int maxInstructions = 100_000;
        private int maxDepth = 50;
        private TraceMode traceMode = TraceMode.RING;
        private int timeBudgetMs = 60_000;

        public Builder seeds(int seeds) {
            this.seeds = seeds;
            return this;
        }

        public Builder maxInstructions(int max) {
            this.maxInstructions = max;
            return this;
        }

        public Builder maxDepth(int depth) {
            this.maxDepth = depth;
            return this;
        }

        public Builder traceMode(TraceMode mode) {
            this.traceMode = mode;
            return this;
        }

        public Builder timeBudget(int ms) {
            this.timeBudgetMs = ms;
            return this;
        }

        public RunSpec build() {
            return new RunSpec(seeds, maxInstructions, maxDepth, traceMode, timeBudgetMs);
        }
    }
}
