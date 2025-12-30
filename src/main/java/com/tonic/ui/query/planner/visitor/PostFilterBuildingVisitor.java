package com.tonic.ui.query.planner.visitor;

import com.tonic.ui.query.ast.*;
import com.tonic.ui.query.planner.PostFilter;

public class PostFilterBuildingVisitor implements PredicateVisitor<PostFilter> {

    @Override
    public PostFilter visitCalls(CallsPredicate predicate) {
        return result -> result.hasCallTo(predicate.ownerClass(), predicate.methodName());
    }

    @Override
    public PostFilter visitReadsField(ReadsFieldPredicate predicate) {
        return PostFilter.alwaysTrue();
    }

    @Override
    public PostFilter visitWritesField(WritesFieldPredicate predicate) {
        return PostFilter.alwaysTrue();
    }

    @Override
    public PostFilter visitAllocCount(AllocCountPredicate predicate) {
        return result -> predicate.test(result.getAllocationCount(predicate.typeName()));
    }

    @Override
    public PostFilter visitInstructionCount(InstructionCountPredicate predicate) {
        return result -> predicate.test(result.getInstructionCount());
    }

    @Override
    public PostFilter visitCoverage(CoveragePredicate predicate) {
        return PostFilter.alwaysTrue();
    }

    @Override
    public PostFilter visitContainsString(ContainsStringPredicate predicate) {
        return result -> result.getStringEvents().stream()
            .anyMatch(e -> predicate.matches(e.value()));
    }

    @Override
    public PostFilter visitThrows(ThrowsPredicate predicate) {
        return result -> result.getExceptionEvents().stream()
            .anyMatch(e -> e.exceptionType().contains(predicate.exceptionType()));
    }

    @Override
    public PostFilter visitFieldBecomes(FieldBecomesPredicate predicate) {
        return PostFilter.alwaysTrue();
    }

    @Override
    public PostFilter visitBefore(BeforePredicate predicate) {
        return predicate.event().accept(this);
    }

    @Override
    public PostFilter visitAfter(AfterPredicate predicate) {
        return predicate.event().accept(this);
    }

    @Override
    public PostFilter visitAnd(AndPredicate predicate) {
        return predicate.left().accept(this).and(predicate.right().accept(this));
    }

    @Override
    public PostFilter visitOr(OrPredicate predicate) {
        return predicate.left().accept(this).or(predicate.right().accept(this));
    }

    @Override
    public PostFilter visitNot(NotPredicate predicate) {
        return predicate.inner().accept(this).negate();
    }
}
