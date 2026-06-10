package com.tonic.ui.query.planner;

import com.tonic.ui.query.ast.Query;
import com.tonic.ui.query.ast.Scope;
import com.tonic.ui.query.planner.filter.StaticFilter;
import com.tonic.ui.query.planner.visitor.ScopeFilterVisitor;

/**
 * Compiles a query into an executable {@link ProbePlan}: the {@code WHERE}
 * {@link com.tonic.ui.query.ast.Condition} travels on the query and is evaluated by the runner via
 * the attribute registry; the planner contributes the scope prefilter.
 */
public class QueryPlanner {

    private final ScopeFilterVisitor scopeFilterVisitor = new ScopeFilterVisitor();

    public ProbePlan plan(Query query) {
        return ProbePlan.builder(query)
                .staticFilter(buildScopeFilter(query.scope()))
                .build();
    }

    private StaticFilter buildScopeFilter(Scope scope) {
        if (scope == null || scope.isAll()) {
            return StaticFilter.all();
        }
        return scope.accept(scopeFilterVisitor);
    }
}
