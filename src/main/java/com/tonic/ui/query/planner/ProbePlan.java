package com.tonic.ui.query.planner;

import com.tonic.ui.query.ast.Query;
import com.tonic.ui.query.ast.RunSpec;
import com.tonic.ui.query.planner.filter.StaticFilter;
import com.tonic.ui.query.planner.probe.ProbeSet;

import java.util.Objects;

/**
 * Compiled query execution plan.
 * Contains all information needed to execute a query against bytecode.
 */
public final class ProbePlan {

    private final Query originalQuery;
    private final StaticFilter staticFilter;
    private final RunSpec runSpec;
    private final ProbeSet probes;
    private final PostFilter postFilter;
    private final ResultProjector projector;
    private final boolean hasXrefBackedFilter;

    public ProbePlan(Query originalQuery, StaticFilter staticFilter, RunSpec runSpec,
                     ProbeSet probes, PostFilter postFilter, ResultProjector projector,
                     boolean hasXrefBackedFilter) {
        this.originalQuery = originalQuery;
        this.staticFilter = staticFilter;
        this.runSpec = runSpec;
        this.probes = probes;
        this.postFilter = postFilter;
        this.projector = projector;
        this.hasXrefBackedFilter = hasXrefBackedFilter;
    }

    public Query originalQuery() {
        return originalQuery;
    }

    public StaticFilter staticFilter() {
        return staticFilter;
    }

    public RunSpec runSpec() {
        return runSpec;
    }

    public ProbeSet probes() {
        return probes;
    }

    public PostFilter postFilter() {
        return postFilter;
    }

    public ResultProjector projector() {
        return projector;
    }

    public boolean hasXrefBackedFilter() {
        return hasXrefBackedFilter;
    }

    public static Builder builder(Query query) {
        return new Builder(query);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProbePlan)) return false;
        ProbePlan probePlan = (ProbePlan) o;
        return Objects.equals(originalQuery, probePlan.originalQuery) &&
               Objects.equals(staticFilter, probePlan.staticFilter) &&
               Objects.equals(runSpec, probePlan.runSpec) &&
               Objects.equals(probes, probePlan.probes) &&
               Objects.equals(postFilter, probePlan.postFilter) &&
               Objects.equals(projector, probePlan.projector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalQuery, staticFilter, runSpec, probes, postFilter, projector);
    }

    @Override
    public String toString() {
        return "ProbePlan{originalQuery=" + originalQuery + ", runSpec=" + runSpec + "}";
    }

    public static class Builder {
        private final Query query;
        private StaticFilter staticFilter;
        private RunSpec runSpec;
        private ProbeSet probes;
        private PostFilter postFilter;
        private ResultProjector projector;
        private boolean hasXrefBackedFilter;

        public Builder(Query query) {
            this.query = query;
            this.runSpec = query.runSpec() != null ? query.runSpec() : RunSpec.DEFAULT;
        }

        public Builder staticFilter(StaticFilter filter) {
            this.staticFilter = filter;
            return this;
        }

        public Builder hasXrefBackedFilter(boolean value) {
            this.hasXrefBackedFilter = value;
            return this;
        }

        public Builder runSpec(RunSpec spec) {
            this.runSpec = spec;
            return this;
        }

        public Builder probes(ProbeSet probes) {
            this.probes = probes;
            return this;
        }

        public Builder postFilter(PostFilter filter) {
            this.postFilter = filter;
            return this;
        }

        public Builder projector(ResultProjector projector) {
            this.projector = projector;
            return this;
        }

        public ProbePlan build() {
            return new ProbePlan(query, staticFilter, runSpec, probes, postFilter, projector, hasXrefBackedFilter);
        }
    }
}
