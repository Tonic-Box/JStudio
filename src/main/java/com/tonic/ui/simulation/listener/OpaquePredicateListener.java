package com.tonic.ui.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationResult;
import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.simulation.listener.AbstractListener;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.BranchInstruction;
import com.tonic.analysis.ssa.ir.CompareOp;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Listener that detects opaque predicates - branch conditions that always
 * evaluate to the same value (always true or always false).
 * <p>
 * Opaque predicates are commonly used in obfuscation to:
 * - Confuse static analysis tools
 * - Add dead code paths
 * - Implement anti-tampering checks
 */
public class OpaquePredicateListener extends AbstractListener {

    private final Map<Integer, BranchAnalysis> branchAnalyses = new HashMap<>();
    private final List<BranchAnalysis> confirmedOpaquePredicates = new ArrayList<>();

    @Override
    public void onSimulationStart(IRMethod method) {
        super.onSimulationStart(method);
        branchAnalyses.clear();
        confirmedOpaquePredicates.clear();
    }

    @Override
    public void onBranch(BranchInstruction instr, boolean taken, SimulationState state) {
        if (instr == null || instr.getCondition() == null) {
            return;
        }

        int instrId = System.identityHashCode(instr);
        BranchAnalysis analysis = branchAnalyses.get(instrId);

        if (analysis == null) {
            analysis = new BranchAnalysis(instr);
            branchAnalyses.put(instrId, analysis);
        }

        boolean conditionResult = evaluateCondition(instr, state);
        analysis.recordExecution(conditionResult);
    }

    @Override
    public void onSimulationEnd(IRMethod method, SimulationResult result) {
        super.onSimulationEnd(method, result);

        for (BranchAnalysis analysis : branchAnalyses.values()) {
            if (analysis.isOpaque()) {
                confirmedOpaquePredicates.add(analysis);
            }
        }
    }

    public List<BranchAnalysis> getAnalyzedBranches() {
        return List.copyOf(branchAnalyses.values());
    }

    public List<BranchAnalysis> getOpaquePredicates() {
        return Collections.unmodifiableList(confirmedOpaquePredicates);
    }

    public int getOpaquePredicateCount() {
        return confirmedOpaquePredicates.size();
    }

    public boolean hasOpaquePredicates() {
        return !confirmedOpaquePredicates.isEmpty();
    }

    private boolean evaluateCondition(BranchInstruction instr, SimulationState state) {
        if (state == null || state.stackDepth() == 0) {
            return true;
        }

        SimValue topValue = state.peek(0);
        if (topValue == null) {
            return true;
        }

        if (topValue.isConstant()) {
            Object constant = topValue.getConstantValue();
            if (constant instanceof Number) {
                int value = ((Number) constant).intValue();
                return evaluateComparisonResult(instr.getCondition(), value);
            } else if (constant instanceof Boolean) {
                return (Boolean) constant;
            }
        }

        return true;
    }

    private boolean evaluateComparisonResult(CompareOp op, int value) {
        if (op == null) {
            return true;
        }

        switch (op) {
            case EQ:
            case IFEQ:
            case ACMPEQ:
            case IFNULL:
                return value == 0;

            case NE:
            case IFNE:
            case ACMPNE:
            case IFNONNULL:
                return value != 0;

            case LT:
            case IFLT:
                return value < 0;

            case GE:
            case IFGE:
                return value >= 0;

            case GT:
            case IFGT:
                return value > 0;

            case LE:
            case IFLE:
                return value <= 0;

            default:
                return true;
        }
    }

    /**
     * Tracks the analysis state of a single branch instruction.
     */
    @Getter
    public static class BranchAnalysis {
        private final BranchInstruction instruction;
        private int trueCount = 0;
        private int falseCount = 0;
        private int executionCount = 0;

        public BranchAnalysis(BranchInstruction instruction) {
            this.instruction = instruction;
        }

        public void recordExecution(boolean conditionResult) {
            executionCount++;
            if (conditionResult) {
                trueCount++;
            } else {
                falseCount++;
            }
        }

        public boolean isOpaque() {
            if (executionCount == 0) {
                return false;
            }
            return trueCount == 0 || falseCount == 0;
        }

        public boolean isAlwaysTrue() {
            return executionCount > 0 && falseCount == 0;
        }

        public boolean isAlwaysFalse() {
            return executionCount > 0 && trueCount == 0;
        }

        public int getBlockId() {
            if (instruction != null && instruction.getBlock() != null) {
                return instruction.getBlock().getId();
            }
            return -1;
        }

        public int getBytecodeOffset() {
            if (instruction != null) {
                return instruction.getId();
            }
            return -1;
        }

        @Override
        public String toString() {
            return "BranchAnalysis[" +
                    "block=" + getBlockId() +
                    ", executions=" + executionCount +
                    ", true=" + trueCount +
                    ", false=" + falseCount +
                    ", opaque=" + isOpaque() +
                    "]";
        }
    }
}
