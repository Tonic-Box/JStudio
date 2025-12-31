package com.tonic.ui.query.planner;

import com.tonic.analysis.xref.XrefDatabase;
import com.tonic.ui.query.ast.*;
import com.tonic.ui.query.planner.filter.StaticFilter;
import com.tonic.ui.query.planner.probe.ProbeSet;
import com.tonic.ui.query.planner.visitor.*;

/**
 * Compiles a Query AST into an executable ProbePlan.
 * Uses visitor pattern to map predicates to static filters, runtime probes, and post-filters.
 */
public class QueryPlanner {

    private final StaticFilterBuildingVisitor staticFilterVisitor;
    private final ScopeFilterVisitor scopeFilterVisitor;
    private final PostFilterBuildingVisitor postFilterVisitor;

    public QueryPlanner(XrefDatabase xrefDatabase) {
        this.staticFilterVisitor = new StaticFilterBuildingVisitor(xrefDatabase);
        this.scopeFilterVisitor = new ScopeFilterVisitor(staticFilterVisitor);
        this.postFilterVisitor = new PostFilterBuildingVisitor();
    }

    public ProbePlan plan(Query query) {
        ProbePlan.Builder builder = ProbePlan.builder(query);

        StaticFilter predicateFilter = buildPredicateStaticFilter(query.predicate());
        boolean hasXrefBacked = predicateFilter != null;

        StaticFilter scopeFilter = buildScopeFilter(query.scope());
        StaticFilter staticFilter = combineFilters(scopeFilter, predicateFilter);

        builder.staticFilter(staticFilter);
        builder.hasXrefBackedFilter(hasXrefBacked);

        ProbeSet probes = buildProbeSet(query);
        builder.probes(probes);

        PostFilter postFilter = buildPostFilter(query);
        builder.postFilter(postFilter);

        ResultProjector projector = ResultProjector.forTarget(query.target());
        builder.projector(projector);

        if (query.runSpec() != null) {
            builder.runSpec(query.runSpec());
        }

        return builder.build();
    }

    private StaticFilter combineFilters(StaticFilter scopeFilter, StaticFilter predicateFilter) {
        if (scopeFilter != null && predicateFilter != null) {
            return scopeFilter.and(predicateFilter);
        } else if (scopeFilter != null) {
            return scopeFilter;
        } else if (predicateFilter != null) {
            return predicateFilter;
        }
        return StaticFilter.all();
    }

    private StaticFilter buildScopeFilter(Scope scope) {
        if (scope == null || scope.isAll()) {
            return StaticFilter.all();
        }
        return scope.accept(scopeFilterVisitor);
    }

    private StaticFilter buildPredicateStaticFilter(Predicate predicate) {
        if (predicate == null) {
            return null;
        }
        return predicate.accept(staticFilterVisitor);
    }

    private ProbeSet buildProbeSet(Query query) {
        ProbeSet.Builder builder = ProbeSet.builder();

        if (query.predicate() != null) {
            ProbeCollectingVisitor probeVisitor = new ProbeCollectingVisitor(builder);
            query.predicate().accept(probeVisitor);
        }

        if (query.scope() != null) {
            ScopeProbeVisitor scopeProbeVisitor = new ScopeProbeVisitor(builder);
            query.scope().accept(scopeProbeVisitor);
        }

        if (query.target() == Target.STRINGS) {
            builder.addStringProbe();
        } else if (query.target() == Target.OBJECTS) {
            builder.addAllAllocsProbe();
        }

        return builder.build();
    }

    private PostFilter buildPostFilter(Query query) {
        if (query.predicate() == null) {
            return PostFilter.alwaysTrue();
        }
        return query.predicate().accept(postFilterVisitor);
    }
}
