package com.tonic.ui.query.planner;

import com.tonic.analysis.xref.XrefDatabase;
import com.tonic.ui.query.ast.*;
import com.tonic.ui.query.planner.filter.*;
import com.tonic.ui.query.planner.probe.*;

/**
 * Compiles a Query AST into an executable ProbePlan.
 * Maps predicates to static filters, runtime probes, and post-filters.
 */
public class QueryPlanner {

    private final XrefDatabase xrefDatabase;

    public QueryPlanner(XrefDatabase xrefDatabase) {
        this.xrefDatabase = xrefDatabase;
    }

    public ProbePlan plan(Query query) {
        ProbePlan.Builder builder = ProbePlan.builder(query);

        StaticFilter predicateFilter = buildPredicateStaticFilter(query.predicate());
        boolean hasXrefBacked = predicateFilter != null;

        StaticFilter scopeFilter = buildScopeFilter(query.scope());
        StaticFilter staticFilter;
        if (scopeFilter != null && predicateFilter != null) {
            staticFilter = scopeFilter.and(predicateFilter);
        } else if (scopeFilter != null) {
            staticFilter = scopeFilter;
        } else if (predicateFilter != null) {
            staticFilter = predicateFilter;
        } else {
            staticFilter = StaticFilter.all();
        }

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

    private StaticFilter buildScopeFilter(Scope scope) {
        if (scope == null || scope instanceof AllScope) {
            return StaticFilter.all();
        }

        if (scope instanceof ClassScope) {
            ClassScope cs = (ClassScope) scope;
            return PatternFilter.classMatching(cs.pattern());
        }

        if (scope instanceof MethodScope) {
            MethodScope ms = (MethodScope) scope;
            return PatternFilter.methodMatching(ms.pattern());
        }

        if (scope instanceof DuringScope) {
            DuringScope ds = (DuringScope) scope;
            if (ds.isClinit()) {
                StaticFilter clinitFilter = PatternFilter.clinitMethods();
                if (ds.classFilter() != null) {
                    return clinitFilter.and(PatternFilter.classMatching(ds.classFilter().pattern()));
                }
                return clinitFilter;
            }
            return PatternFilter.methodMatching(ds.methodPattern());
        }

        if (scope instanceof BetweenScope) {
            BetweenScope bs = (BetweenScope) scope;
            StaticFilter startFilter = buildPredicateStaticFilter(bs.startEvent());
            StaticFilter endFilter = buildPredicateStaticFilter(bs.endEvent());
            if (startFilter != null && endFilter != null) {
                return startFilter.or(endFilter);
            }
        }

        return StaticFilter.all();
    }

    private StaticFilter buildPredicateStaticFilter(Predicate predicate) {
        if (predicate == null) {
            return null;
        }

        if (predicate instanceof CallsPredicate) {
            CallsPredicate cp = (CallsPredicate) predicate;
            if (xrefDatabase != null) {
                return XrefMethodFilter.callsMethod(xrefDatabase,
                    cp.ownerClass(), cp.methodName(), cp.descriptor(), cp.argumentType());
            }
            return null;
        }

        if (predicate instanceof WritesFieldPredicate) {
            WritesFieldPredicate wf = (WritesFieldPredicate) predicate;
            if (xrefDatabase != null) {
                return XrefMethodFilter.writesField(xrefDatabase,
                    wf.ownerClass(), wf.fieldName(), wf.descriptor());
            }
            return null;
        }

        if (predicate instanceof ReadsFieldPredicate) {
            ReadsFieldPredicate rf = (ReadsFieldPredicate) predicate;
            if (xrefDatabase != null) {
                return XrefMethodFilter.readsField(xrefDatabase,
                    rf.ownerClass(), rf.fieldName(), rf.descriptor());
            }
            return null;
        }

        if (predicate instanceof ContainsStringPredicate) {
            ContainsStringPredicate cs = (ContainsStringPredicate) predicate;
            if (cs.isRegex()) {
                return ConstPoolFilter.matchesString(cs.pattern());
            }
            return ConstPoolFilter.containsString(cs.pattern());
        }

        if (predicate instanceof AndPredicate) {
            AndPredicate ap = (AndPredicate) predicate;
            StaticFilter left = buildPredicateStaticFilter(ap.left());
            StaticFilter right = buildPredicateStaticFilter(ap.right());
            if (left != null && right != null) {
                return left.and(right);
            }
            return left != null ? left : right;
        }

        if (predicate instanceof OrPredicate) {
            OrPredicate op = (OrPredicate) predicate;
            StaticFilter left = buildPredicateStaticFilter(op.left());
            StaticFilter right = buildPredicateStaticFilter(op.right());
            if (left != null && right != null) {
                return left.or(right);
            }
            return left != null ? left : right;
        }

        return null;
    }

    private ProbeSet buildProbeSet(Query query) {
        ProbeSet.Builder builder = ProbeSet.builder();
        collectProbesFromPredicate(query.predicate(), builder);
        collectProbesFromScope(query.scope(), builder);

        if (query.target() == Target.STRINGS) {
            builder.addStringProbe();
        } else if (query.target() == Target.OBJECTS) {
            builder.addAllAllocsProbe();
        }

        return builder.build();
    }

    private void collectProbesFromPredicate(Predicate predicate, ProbeSet.Builder builder) {
        if (predicate == null) return;

        if (predicate instanceof CallsPredicate) {
            CallsPredicate cp = (CallsPredicate) predicate;
            builder.addCallProbe(cp.ownerClass(), cp.methodName(), cp.descriptor());
        } else if (predicate instanceof AllocCountPredicate) {
            AllocCountPredicate acp = (AllocCountPredicate) predicate;
            builder.addAllocProbe(acp.typeName());
        } else if (predicate instanceof WritesFieldPredicate) {
            WritesFieldPredicate wf = (WritesFieldPredicate) predicate;
            builder.addFieldWriteProbe(wf.ownerClass(), wf.fieldName());
        } else if (predicate instanceof ReadsFieldPredicate) {
            ReadsFieldPredicate rf = (ReadsFieldPredicate) predicate;
            builder.addFieldReadProbe(rf.ownerClass(), rf.fieldName());
        } else if (predicate instanceof FieldBecomesPredicate) {
            FieldBecomesPredicate fb = (FieldBecomesPredicate) predicate;
            builder.addFieldTransitionProbe(fb.ownerClass(), fb.fieldName());
        } else if (predicate instanceof ContainsStringPredicate) {
            ContainsStringPredicate cs = (ContainsStringPredicate) predicate;
            builder.addStringProbe(cs.pattern(), cs.isRegex());
        } else if (predicate instanceof ThrowsPredicate) {
            ThrowsPredicate tp = (ThrowsPredicate) predicate;
            builder.addExceptionProbe(tp.exceptionType());
        } else if (predicate instanceof CoveragePredicate) {
            builder.addBranchProbe();
        } else if (predicate instanceof BeforePredicate) {
            BeforePredicate bp = (BeforePredicate) predicate;
            collectProbesFromPredicate(bp.event(), builder);
        } else if (predicate instanceof AfterPredicate) {
            AfterPredicate afterPred = (AfterPredicate) predicate;
            collectProbesFromPredicate(afterPred.event(), builder);
        } else if (predicate instanceof AndPredicate) {
            AndPredicate andPred = (AndPredicate) predicate;
            collectProbesFromPredicate(andPred.left(), builder);
            collectProbesFromPredicate(andPred.right(), builder);
        } else if (predicate instanceof OrPredicate) {
            OrPredicate orPred = (OrPredicate) predicate;
            collectProbesFromPredicate(orPred.left(), builder);
            collectProbesFromPredicate(orPred.right(), builder);
        } else if (predicate instanceof NotPredicate) {
            NotPredicate np = (NotPredicate) predicate;
            collectProbesFromPredicate(np.inner(), builder);
        }
    }

    private void collectProbesFromScope(Scope scope, ProbeSet.Builder builder) {
        if (scope instanceof BetweenScope) {
            BetweenScope bs = (BetweenScope) scope;
            collectProbesFromPredicate(bs.startEvent(), builder);
            collectProbesFromPredicate(bs.endEvent(), builder);
        }
    }

    private PostFilter buildPostFilter(Query query) {
        if (query.predicate() == null) {
            return PostFilter.alwaysTrue();
        }
        return buildPredicatePostFilter(query.predicate());
    }

    private PostFilter buildPredicatePostFilter(Predicate predicate) {
        if (predicate instanceof CallsPredicate) {
            CallsPredicate cp = (CallsPredicate) predicate;
            return result -> result.hasCallTo(cp.ownerClass(), cp.methodName());
        }

        if (predicate instanceof AllocCountPredicate) {
            AllocCountPredicate acp = (AllocCountPredicate) predicate;
            return result -> acp.test(result.getAllocationCount(acp.typeName()));
        }

        if (predicate instanceof InstructionCountPredicate) {
            InstructionCountPredicate icp = (InstructionCountPredicate) predicate;
            return result -> icp.test(result.getInstructionCount());
        }

        if (predicate instanceof ThrowsPredicate) {
            ThrowsPredicate tp = (ThrowsPredicate) predicate;
            return result -> result.getExceptionEvents().stream()
                .anyMatch(e -> e.exceptionType().contains(tp.exceptionType()));
        }

        if (predicate instanceof ContainsStringPredicate) {
            ContainsStringPredicate cs = (ContainsStringPredicate) predicate;
            return result -> result.getStringEvents().stream()
                .anyMatch(e -> cs.matches(e.value()));
        }

        if (predicate instanceof AndPredicate) {
            AndPredicate ap = (AndPredicate) predicate;
            return buildPredicatePostFilter(ap.left())
                .and(buildPredicatePostFilter(ap.right()));
        }

        if (predicate instanceof OrPredicate) {
            OrPredicate op = (OrPredicate) predicate;
            return buildPredicatePostFilter(op.left())
                .or(buildPredicatePostFilter(op.right()));
        }

        if (predicate instanceof NotPredicate) {
            NotPredicate np = (NotPredicate) predicate;
            return buildPredicatePostFilter(np.inner()).negate();
        }

        return PostFilter.alwaysTrue();
    }
}
