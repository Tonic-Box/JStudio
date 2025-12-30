package com.tonic.ui.query.planner.visitor;

import com.tonic.ui.query.ast.*;
import com.tonic.ui.query.planner.probe.ProbeSet;

public class ScopeProbeVisitor implements ScopeVisitor<Void> {

    private final ProbeSet.Builder builder;
    private final ProbeCollectingVisitor predicateVisitor;

    public ScopeProbeVisitor(ProbeSet.Builder builder) {
        this.builder = builder;
        this.predicateVisitor = new ProbeCollectingVisitor(builder);
    }

    @Override
    public Void visitAll(AllScope scope) {
        return null;
    }

    @Override
    public Void visitClass(ClassScope scope) {
        return null;
    }

    @Override
    public Void visitMethod(MethodScope scope) {
        return null;
    }

    @Override
    public Void visitDuring(DuringScope scope) {
        return null;
    }

    @Override
    public Void visitBetween(BetweenScope scope) {
        scope.startEvent().accept(predicateVisitor);
        scope.endEvent().accept(predicateVisitor);
        return null;
    }
}
