package com.tonic.ui.query.planner.visitor;

import com.tonic.ui.query.ast.*;
import com.tonic.ui.query.planner.filter.PatternFilter;
import com.tonic.ui.query.planner.filter.StaticFilter;

public class ScopeFilterVisitor implements ScopeVisitor<StaticFilter> {

    private final StaticFilterBuildingVisitor predicateVisitor;

    public ScopeFilterVisitor(StaticFilterBuildingVisitor predicateVisitor) {
        this.predicateVisitor = predicateVisitor;
    }

    @Override
    public StaticFilter visitAll(AllScope scope) {
        return StaticFilter.all();
    }

    @Override
    public StaticFilter visitClass(ClassScope scope) {
        return PatternFilter.classMatching(scope.pattern());
    }

    @Override
    public StaticFilter visitMethod(MethodScope scope) {
        return PatternFilter.methodMatching(scope.pattern());
    }

    @Override
    public StaticFilter visitDuring(DuringScope scope) {
        if (scope.isClinit()) {
            StaticFilter clinitFilter = PatternFilter.clinitMethods();
            if (scope.classFilter() != null) {
                return clinitFilter.and(PatternFilter.classMatching(scope.classFilter().pattern()));
            }
            return clinitFilter;
        }
        return PatternFilter.methodMatching(scope.methodPattern());
    }

    @Override
    public StaticFilter visitBetween(BetweenScope scope) {
        StaticFilter startFilter = scope.startEvent().accept(predicateVisitor);
        StaticFilter endFilter = scope.endEvent().accept(predicateVisitor);
        if (startFilter != null && endFilter != null) {
            return startFilter.or(endFilter);
        }
        return StaticFilter.all();
    }
}
