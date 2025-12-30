package com.tonic.ui.query.ast;

public interface PredicateVisitor<T> {

    T visitCalls(CallsPredicate predicate);

    T visitReadsField(ReadsFieldPredicate predicate);

    T visitWritesField(WritesFieldPredicate predicate);

    T visitAllocCount(AllocCountPredicate predicate);

    T visitInstructionCount(InstructionCountPredicate predicate);

    T visitCoverage(CoveragePredicate predicate);

    T visitContainsString(ContainsStringPredicate predicate);

    T visitThrows(ThrowsPredicate predicate);

    T visitFieldBecomes(FieldBecomesPredicate predicate);

    T visitBefore(BeforePredicate predicate);

    T visitAfter(AfterPredicate predicate);

    T visitAnd(AndPredicate predicate);

    T visitOr(OrPredicate predicate);

    T visitNot(NotPredicate predicate);
}
