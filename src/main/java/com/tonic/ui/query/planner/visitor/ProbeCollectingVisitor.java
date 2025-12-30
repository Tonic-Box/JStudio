package com.tonic.ui.query.planner.visitor;

import com.tonic.ui.query.ast.*;
import com.tonic.ui.query.planner.probe.ProbeSet;

public class ProbeCollectingVisitor implements PredicateVisitor<Void> {

    private final ProbeSet.Builder builder;

    public ProbeCollectingVisitor(ProbeSet.Builder builder) {
        this.builder = builder;
    }

    @Override
    public Void visitCalls(CallsPredicate predicate) {
        builder.addCallProbe(predicate.ownerClass(), predicate.methodName(), predicate.descriptor());
        return null;
    }

    @Override
    public Void visitReadsField(ReadsFieldPredicate predicate) {
        builder.addFieldReadProbe(predicate.ownerClass(), predicate.fieldName());
        return null;
    }

    @Override
    public Void visitWritesField(WritesFieldPredicate predicate) {
        builder.addFieldWriteProbe(predicate.ownerClass(), predicate.fieldName());
        return null;
    }

    @Override
    public Void visitAllocCount(AllocCountPredicate predicate) {
        builder.addAllocProbe(predicate.typeName());
        return null;
    }

    @Override
    public Void visitInstructionCount(InstructionCountPredicate predicate) {
        return null;
    }

    @Override
    public Void visitCoverage(CoveragePredicate predicate) {
        builder.addBranchProbe();
        return null;
    }

    @Override
    public Void visitContainsString(ContainsStringPredicate predicate) {
        builder.addStringProbe(predicate.pattern(), predicate.isRegex());
        return null;
    }

    @Override
    public Void visitThrows(ThrowsPredicate predicate) {
        builder.addExceptionProbe(predicate.exceptionType());
        return null;
    }

    @Override
    public Void visitFieldBecomes(FieldBecomesPredicate predicate) {
        builder.addFieldTransitionProbe(predicate.ownerClass(), predicate.fieldName());
        return null;
    }

    @Override
    public Void visitBefore(BeforePredicate predicate) {
        predicate.event().accept(this);
        return null;
    }

    @Override
    public Void visitAfter(AfterPredicate predicate) {
        predicate.event().accept(this);
        return null;
    }

    @Override
    public Void visitAnd(AndPredicate predicate) {
        predicate.left().accept(this);
        predicate.right().accept(this);
        return null;
    }

    @Override
    public Void visitOr(OrPredicate predicate) {
        predicate.left().accept(this);
        predicate.right().accept(this);
        return null;
    }

    @Override
    public Void visitNot(NotPredicate predicate) {
        predicate.inner().accept(this);
        return null;
    }
}
