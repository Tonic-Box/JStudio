package com.tonic.ui.query.eval;

import com.tonic.analysis.ssa.analysis.DefUseChains;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.ir.ReturnInstruction;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.ui.query.ast.Accessor;
import com.tonic.ui.query.ast.Step;
import com.tonic.ui.query.value.Operator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Evaluates the data-flow relations {@link Operator#FLOWS_TO}/{@link Operator#FLOWS_FROM} over a
 * method's SSA IR. {@code A flowsTo B} holds when a value produced at endpoint {@code A} reaches
 * endpoint {@code B} along def-use edges (forward reachability); {@code flowsFrom} is the same
 * relation with the roles swapped.
 *
 * <p>Endpoints are resolved from accessors against the current {@link Subject}: {@code param(n)} (a
 * parameter value) and {@code return} (any returned value) are method-global; {@code arg(n)} resolves
 * against a {@link Subject.CallSubject} (the call's nth argument, receiver excluded) and {@code insn}
 * against a {@link Subject.InstructionSubject} (the value the instruction defines), both mapped to SSA
 * via {@code IRInstruction.getBytecodeOffset()}. An endpoint that cannot be resolved is empty and the
 * relation is {@code false}.
 */
final class DataFlowAnalysis {

    private DataFlowAnalysis() {
    }

    static boolean evaluate(Subject subject, Accessor lhs, Operator op, Accessor rhs) {
        EvalContext ctx = subject.context();
        IRMethod ir = ctx.ir();
        DefUseChains du = ctx.defUse();
        if (ir == null || du == null) {
            return false;
        }
        Accessor sourceAccessor = op == Operator.FLOWS_FROM ? rhs : lhs;
        Accessor sinkAccessor = op == Operator.FLOWS_FROM ? lhs : rhs;
        Endpoint source = resolve(sourceAccessor, subject, ir);
        Endpoint sink = resolve(sinkAccessor, subject, ir);
        if (source.seeds.isEmpty() || sink.isEmptySink()) {
            return false;
        }
        return reaches(source.seeds, sink, du);
    }

    private static boolean reaches(Set<SSAValue> seeds, Endpoint sink, DefUseChains du) {
        for (SSAValue seed : seeds) {
            if (sink.values.contains(seed)) {
                return true;
            }
        }
        Deque<SSAValue> work = new ArrayDeque<>(seeds);
        Set<SSAValue> seen = new HashSet<>(seeds);
        while (!work.isEmpty()) {
            SSAValue value = work.poll();
            for (IRInstruction use : du.getUses(value)) {
                if (sink.matcher != null && sink.matcher.test(use)) {
                    return true;
                }
                SSAValue result = use.getResult();
                if (result != null && seen.add(result)) {
                    if (sink.values.contains(result)) {
                        return true;
                    }
                    work.add(result);
                }
            }
        }
        return false;
    }

    private static Endpoint resolve(Accessor accessor, Subject subject, IRMethod ir) {
        Step head = accessor.steps().get(0);
        String keyword = head.keyword().toLowerCase();
        if ("param".equals(keyword) && head.hasIndex()) {
            Endpoint endpoint = new Endpoint();
            List<SSAValue> params = ir.getParameters();
            if (head.index() >= 0 && head.index() < params.size()) {
                seed(endpoint, params.get(head.index()));
            }
            return endpoint;
        }
        if ("return".equals(keyword)) {
            Endpoint endpoint = new Endpoint();
            endpoint.matcher = instr -> instr instanceof ReturnInstruction;
            for (IRInstruction instr : allInstructions(ir)) {
                if (instr instanceof ReturnInstruction) {
                    for (Value operand : instr.getOperands()) {
                        if (operand instanceof SSAValue) {
                            endpoint.seeds.add((SSAValue) operand);
                        }
                    }
                }
            }
            return endpoint;
        }
        if ("arg".equals(keyword) && head.hasIndex() && subject instanceof Subject.CallSubject) {
            Endpoint endpoint = new Endpoint();
            int offset = ((Subject.CallSubject) subject).invoke().getOffset();
            InvokeInstruction invoke = findInvokeAt(ir, offset);
            if (invoke != null) {
                List<Value> args = invoke.getMethodArguments();
                if (head.index() >= 0 && head.index() < args.size()
                        && args.get(head.index()) instanceof SSAValue) {
                    seed(endpoint, (SSAValue) args.get(head.index()));
                }
            }
            return endpoint;
        }
        if (("insn".equals(keyword) || "instruction".equals(keyword))
                && subject instanceof Subject.InstructionSubject) {
            Endpoint endpoint = new Endpoint();
            int offset = ((Subject.InstructionSubject) subject).instruction().getOffset();
            for (IRInstruction instr : allInstructions(ir)) {
                if (instr.getBytecodeOffset() == offset && instr.getResult() != null) {
                    seed(endpoint, instr.getResult());
                }
            }
            return endpoint;
        }
        return new Endpoint();
    }

    private static void seed(Endpoint endpoint, SSAValue value) {
        endpoint.seeds.add(value);
        endpoint.values.add(value);
    }

    private static InvokeInstruction findInvokeAt(IRMethod ir, int offset) {
        for (IRInstruction instr : allInstructions(ir)) {
            if (instr instanceof InvokeInstruction && instr.getBytecodeOffset() == offset) {
                return (InvokeInstruction) instr;
            }
        }
        return null;
    }

    private static List<IRInstruction> allInstructions(IRMethod ir) {
        List<IRInstruction> all = new ArrayList<>();
        ir.getBlocksInOrder().forEach(block -> all.addAll(block.getAllInstructions()));
        return all;
    }

    /**
     * A resolved data-flow endpoint. As a source it contributes {@link #seeds} to start the forward
     * walk; as a sink it is reached when the walk hits an instruction matching {@link #matcher} or a
     * value in {@link #values}.
     */
    private static final class Endpoint {
        final Set<SSAValue> seeds = new HashSet<>();
        final Set<SSAValue> values = new HashSet<>();
        Predicate<IRInstruction> matcher;

        boolean isEmptySink() {
            return matcher == null && values.isEmpty();
        }
    }
}
