package com.tonic.ui.query.planner;

import com.tonic.ui.query.ast.Query;
import com.tonic.ui.query.planner.filter.StaticFilter;

import java.util.Objects;

/**
 * Compiled query plan: the scope prefilter plus the original query, whose {@code WHERE}
 * {@link com.tonic.ui.query.ast.Condition} the runner evaluates via the attribute registry.
 */
public final class ProbePlan {

    private final Query originalQuery;
    private final StaticFilter staticFilter;

    public ProbePlan(Query originalQuery, StaticFilter staticFilter) {
        this.originalQuery = originalQuery;
        this.staticFilter = staticFilter;
    }

    public Query originalQuery() {
        return originalQuery;
    }

    public StaticFilter staticFilter() {
        return staticFilter;
    }

    public static Builder builder(Query query) {
        return new Builder(query);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProbePlan)) return false;
        ProbePlan that = (ProbePlan) o;
        return Objects.equals(originalQuery, that.originalQuery)
                && Objects.equals(staticFilter, that.staticFilter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalQuery, staticFilter);
    }

    @Override
    public String toString() {
        return "ProbePlan{query=" + originalQuery + "}";
    }

    public static class Builder {
        private final Query query;
        private StaticFilter staticFilter;

        public Builder(Query query) {
            this.query = query;
        }

        public Builder staticFilter(StaticFilter filter) {
            this.staticFilter = filter;
            return this;
        }

        public ProbePlan build() {
            return new ProbePlan(query, staticFilter);
        }
    }
}
