package com.tonic.ui.query.planner.visitor;

import com.tonic.analysis.xref.XrefDatabase;
import com.tonic.ui.query.ast.*;
import com.tonic.ui.query.planner.filter.ConstPoolFilter;
import com.tonic.ui.query.planner.filter.StaticFilter;
import com.tonic.ui.query.planner.filter.XrefMethodFilter;

public class StaticFilterBuildingVisitor implements PredicateVisitor<StaticFilter> {

    private final XrefDatabase xrefDatabase;

    public StaticFilterBuildingVisitor(XrefDatabase xrefDatabase) {
        this.xrefDatabase = xrefDatabase;
    }

    @Override
    public StaticFilter visitCalls(CallsPredicate predicate) {
        if (xrefDatabase != null) {
            return XrefMethodFilter.callsMethod(xrefDatabase,
                predicate.ownerClass(), predicate.methodName(),
                predicate.descriptor(), predicate.argumentType());
        }
        return null;
    }

    @Override
    public StaticFilter visitReadsField(ReadsFieldPredicate predicate) {
        if (xrefDatabase != null) {
            return XrefMethodFilter.readsField(xrefDatabase,
                predicate.ownerClass(), predicate.fieldName(), predicate.descriptor());
        }
        return null;
    }

    @Override
    public StaticFilter visitWritesField(WritesFieldPredicate predicate) {
        if (xrefDatabase != null) {
            return XrefMethodFilter.writesField(xrefDatabase,
                predicate.ownerClass(), predicate.fieldName(), predicate.descriptor());
        }
        return null;
    }

    @Override
    public StaticFilter visitAllocCount(AllocCountPredicate predicate) {
        return null;
    }

    @Override
    public StaticFilter visitInstructionCount(InstructionCountPredicate predicate) {
        return null;
    }

    @Override
    public StaticFilter visitCoverage(CoveragePredicate predicate) {
        return null;
    }

    @Override
    public StaticFilter visitContainsString(ContainsStringPredicate predicate) {
        if (predicate.isRegex()) {
            return ConstPoolFilter.matchesString(predicate.pattern());
        }
        return ConstPoolFilter.containsString(predicate.pattern());
    }

    @Override
    public StaticFilter visitThrows(ThrowsPredicate predicate) {
        return null;
    }

    @Override
    public StaticFilter visitFieldBecomes(FieldBecomesPredicate predicate) {
        return null;
    }

    @Override
    public StaticFilter visitBefore(BeforePredicate predicate) {
        return predicate.event().accept(this);
    }

    @Override
    public StaticFilter visitAfter(AfterPredicate predicate) {
        return predicate.event().accept(this);
    }

    @Override
    public StaticFilter visitAnd(AndPredicate predicate) {
        StaticFilter left = predicate.left().accept(this);
        StaticFilter right = predicate.right().accept(this);
        if (left != null && right != null) {
            return left.and(right);
        }
        return left != null ? left : right;
    }

    @Override
    public StaticFilter visitOr(OrPredicate predicate) {
        StaticFilter left = predicate.left().accept(this);
        StaticFilter right = predicate.right().accept(this);
        if (left != null && right != null) {
            return left.or(right);
        }
        return left != null ? left : right;
    }

    @Override
    public StaticFilter visitNot(NotPredicate predicate) {
        return null;
    }
}
