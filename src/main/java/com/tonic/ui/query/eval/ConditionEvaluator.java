package com.tonic.ui.query.eval;

import com.tonic.analysis.instruction.Instruction;
import com.tonic.ui.query.ast.Accessor;
import com.tonic.ui.query.ast.Condition;
import com.tonic.ui.query.ast.Operand;
import com.tonic.ui.query.ast.Step;
import com.tonic.ui.query.value.Value;
import com.tonic.ui.query.value.ValueKind;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Interprets a {@link Condition} against a {@link Subject}. The single evaluation engine: walks
 * accessor paths through the {@link AttributeRegistry}, applies operators over {@link Value}s, and
 * folds quantifier/count streams — no per-keyword dispatch. Matches record navigation evidence into
 * the subject's {@link EvalContext}.
 */
public final class ConditionEvaluator implements Condition.Visitor<Boolean> {

    private final AttributeRegistry registry;
    private Subject subject;

    public ConditionEvaluator(AttributeRegistry registry) {
        this.registry = registry;
    }

    public boolean eval(Condition condition, Subject against) {
        Subject previous = this.subject;
        this.subject = against;
        try {
            return condition.accept(this);
        } finally {
            this.subject = previous;
        }
    }

    @Override
    public Boolean visitAnd(Condition.And c) {
        for (Condition t : c.terms()) {
            if (!c0(t)) return false;
        }
        return true;
    }

    @Override
    public Boolean visitOr(Condition.Or c) {
        for (Condition t : c.terms()) {
            if (c0(t)) return true;
        }
        return false;
    }

    @Override
    public Boolean visitNot(Condition.Not c) {
        return !c0(c.inner());
    }

    @Override
    public Boolean visitGroup(Condition.Group c) {
        return c0(c.inner());
    }

    @Override
    public Boolean visitTrue(Condition.True c) {
        return true;
    }

    @Override
    public Boolean visitComparison(Condition.Comparison c) {
        if (c.op() != null && c.op().isRelational()) {
            Accessor rhs = ((Operand.Ref) c.operand()).accessor();
            return DataFlowAnalysis.evaluate(subject, c.accessor(), c.op(), rhs);
        }
        Value lhs = resolveScalar(c.accessor(), subject);
        if (c.isBoolean()) {
            if (lhs.kind() == ValueKind.BOOL) {
                return ((Value.BoolValue) lhs).get();
            }
            return lhs.kind() != ValueKind.ABSENT;
        }
        if (lhs.kind() == ValueKind.ABSENT) {
            return false;
        }
        Value rhs = resolveOperand(c.operand(), subject);
        return c.op().test(lhs, rhs);
    }

    @Override
    public Boolean visitQuantifier(Condition.Quantifier c) {
        List<Subject> elements = resolveStream(c.stream(), subject).collect(java.util.stream.Collectors.toList());
        switch (c.quant()) {
            case ANY:
                for (Subject e : elements) {
                    if (bodyMatches(c.body(), e)) {
                        recordEvidence(e);
                        return true;
                    }
                }
                return false;
            case NONE:
                for (Subject e : elements) {
                    if (bodyMatches(c.body(), e)) return false;
                }
                return true;
            case ALL:
            default:
                for (Subject e : elements) {
                    if (!bodyMatches(c.body(), e)) return false;
                }
                return !elements.isEmpty();
        }
    }

    @Override
    public Boolean visitCount(Condition.Count c) {
        long count = resolveStream(c.stream(), subject)
                .filter(e -> bodyMatches(c.body(), e))
                .count();
        Value rhs = resolveOperand(c.operand(), subject);
        return c.op().test(Value.of(count), rhs);
    }

    @Override
    public Boolean visitSequence(Condition.Sequence c) {
        EvalContext ctx = subject.context();
        List<Instruction> insns = ctx.instructions();
        if (insns.isEmpty()) {
            return false;
        }
        List<Subject> elementSubjects = new ArrayList<>(insns.size());
        for (int i = 0; i < insns.size(); i++) {
            elementSubjects.add(new Subject.InstructionSubject(insns.get(i), i, ctx));
        }
        int[] budget = {200_000};
        for (int start = 0; start < insns.size(); start++) {
            if (matchSequence(c.elements(), 0, elementSubjects, start, budget)) {
                recordSequenceEvidence(ctx, insns.get(start));
                return true;
            }
        }
        return false;
    }

    /** Greedy + backtracking match of the element list against instructions starting at {@code ii}. */
    private boolean matchSequence(List<Condition.Sequence.Element> elements, int ei,
                                  List<Subject> subjects, int ii, int[] budget) {
        if (ei == elements.size()) {
            return true;
        }
        if (--budget[0] < 0) {
            return false;
        }
        Condition.Sequence.Element e = elements.get(ei);
        int limit = Math.min(e.max(), subjects.size() - ii);
        int run = 0;
        while (run < limit && eval(e.matcher(), subjects.get(ii + run))) {
            run++;
        }
        for (int k = run; k >= e.min(); k--) {
            if (matchSequence(elements, ei + 1, subjects, ii + k, budget)) {
                return true;
            }
        }
        return false;
    }

    private void recordSequenceEvidence(EvalContext ctx, Instruction at) {
        if (ctx.method() == null) {
            return;
        }
        ctx.evidence().record(ctx.method().getOwnerName(), ctx.method().getName(),
                ctx.method().getDesc(), at.getOffset(), "sequence @ pc " + at.getOffset());
    }

    private boolean bodyMatches(Condition body, Subject element) {
        return body == null || eval(body, element);
    }

    private boolean c0(Condition condition) {
        return condition.accept(this);
    }

    // ---- accessor resolution ---------------------------------------------

    private Value resolveScalar(Accessor accessor, Subject from) {
        List<Step> steps = accessor.steps();
        Subject cur = from;
        for (int i = 0; i < steps.size() - 1; i++) {
            cur = single(expand(steps.get(i), cur));
            if (cur == null) {
                return Value.ABSENT;
            }
        }
        Step terminal = steps.get(steps.size() - 1);
        AttributeRegistry.Entry entry = registry.lookup(cur.kind(), terminal.keyword());
        if (entry == null || entry.isStream()) {
            return Value.ABSENT;
        }
        return entry.scalar().get(cur);
    }

    private Stream<Subject> resolveStream(Accessor accessor, Subject from) {
        List<Step> steps = accessor.steps();
        Subject cur = from;
        for (int i = 0; i < steps.size() - 1; i++) {
            cur = single(expand(steps.get(i), cur));
            if (cur == null) {
                return Stream.empty();
            }
        }
        return expand(steps.get(steps.size() - 1), cur);
    }

    private Stream<Subject> expand(Step step, Subject on) {
        AttributeRegistry.Entry entry = registry.lookup(on.kind(), step.keyword());
        if (entry == null || !entry.isStream()) {
            return Stream.empty();
        }
        return entry.stream().expand(on, step);
    }

    private Value resolveOperand(Operand operand, Subject from) {
        if (operand instanceof Operand.Literal) {
            return ((Operand.Literal) operand).value();
        }
        return resolveScalar(((Operand.Ref) operand).accessor(), from);
    }

    private static Subject single(Stream<Subject> stream) {
        return stream.findFirst().orElse(null);
    }

    private void recordEvidence(Subject element) {
        EvalContext ctx = element.context();
        if (ctx.method() == null) {
            return;
        }
        Instruction at = locate(element);
        int pc = at != null ? at.getOffset() : 0;
        ctx.evidence().record(ctx.method().getOwnerName(), ctx.method().getName(),
                ctx.method().getDesc(), pc, describe(element, pc));
    }

    private static Instruction locate(Subject s) {
        if (s instanceof Subject.CallSubject) return ((Subject.CallSubject) s).invoke();
        if (s instanceof Subject.InstructionSubject) return ((Subject.InstructionSubject) s).instruction();
        if (s instanceof Subject.FieldAccessSubject) return ((Subject.FieldAccessSubject) s).instruction();
        if (s instanceof Subject.ArgSubject) return ((Subject.ArgSubject) s).call().invoke();
        return null;
    }

    private static String describe(Subject s, int pc) {
        return s.kind().name().toLowerCase() + " @ pc " + pc;
    }
}
