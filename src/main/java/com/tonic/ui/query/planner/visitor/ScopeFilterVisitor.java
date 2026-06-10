package com.tonic.ui.query.planner.visitor;

import com.tonic.ui.query.ast.AllScope;
import com.tonic.ui.query.ast.ClassScope;
import com.tonic.ui.query.ast.DuringScope;
import com.tonic.ui.query.ast.MethodScope;
import com.tonic.ui.query.ast.ScopeVisitor;
import com.tonic.ui.query.planner.filter.PatternFilter;
import com.tonic.ui.query.planner.filter.StaticFilter;

/**
 * Translates a {@link com.tonic.ui.query.ast.Scope} into a static prefilter over the candidate set.
 */
public class ScopeFilterVisitor implements ScopeVisitor<StaticFilter> {

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
}
