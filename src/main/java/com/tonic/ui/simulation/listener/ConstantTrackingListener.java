package com.tonic.ui.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.simulation.listener.AbstractListener;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.BinaryOpInstruction;
import com.tonic.analysis.ssa.ir.ConstantInstruction;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.UnaryOpInstruction;
import com.tonic.analysis.ssa.value.SSAValue;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Listener that tracks constant values computed during simulation.
 * Identifies instructions where the result is always a known constant,
 * which can be used for constant folding optimization.
 */
public class ConstantTrackingListener extends AbstractListener {

    private final Map<IRInstruction, ConstantResult> constantResults = new HashMap<>();
    private final List<ConstantResult> foldableConstants = new ArrayList<>();

    @Override
    public void onSimulationStart(IRMethod method) {
        super.onSimulationStart(method);
        constantResults.clear();
        foldableConstants.clear();
    }

    @Override
    public void onAfterInstruction(IRInstruction instr, SimulationState before, SimulationState after) {
        if (instr == null || after == null) {
            return;
        }

        SSAValue result = instr.getResult();
        if (result == null) {
            return;
        }

        if (instr instanceof ConstantInstruction) {
            return;
        }

        if (after.stackDepth() > 0) {
            SimValue topValue = after.peek(0);
            if (topValue != null && topValue.isConstant()) {
                Object constantValue = topValue.getConstantValue();
                recordConstant(instr, constantValue, isFoldableInstruction(instr));
            }
        }
    }

    private boolean isFoldableInstruction(IRInstruction instr) {
        return instr instanceof BinaryOpInstruction || instr instanceof UnaryOpInstruction;
    }

    private void recordConstant(IRInstruction instr, Object value, boolean foldable) {
        ConstantResult existing = constantResults.get(instr);

        if (existing == null) {
            ConstantResult result = new ConstantResult(instr, value, foldable);
            constantResults.put(instr, result);
            if (foldable) {
                foldableConstants.add(result);
            }
        } else {
            existing.incrementExecutionCount();
            if (!valuesEqual(existing.getValue(), value)) {
                existing.markAsVariable();
            }
        }
    }

    private boolean valuesEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    public Map<IRInstruction, ConstantResult> getConstantResults() {
        return Collections.unmodifiableMap(constantResults);
    }

    public List<ConstantResult> getFoldableConstants() {
        List<ConstantResult> result = new ArrayList<>();
        for (ConstantResult cr : foldableConstants) {
            if (cr.isAlwaysConstant()) {
                result.add(cr);
            }
        }
        return result;
    }

    public int getConstantCount() {
        return (int) constantResults.values().stream()
                .filter(ConstantResult::isAlwaysConstant)
                .count();
    }

    public int getFoldableCount() {
        return (int) foldableConstants.stream()
                .filter(ConstantResult::isAlwaysConstant)
                .count();
    }

    /**
     * Represents a constant value result for an instruction.
     */
    @Getter
    public static class ConstantResult {
        private final IRInstruction instruction;
        private final Object value;
        private final boolean foldable;
        private int executionCount = 1;
        private boolean alwaysConstant = true;

        public ConstantResult(IRInstruction instruction, Object value, boolean foldable) {
            this.instruction = instruction;
            this.value = value;
            this.foldable = foldable;
        }

        void incrementExecutionCount() {
            executionCount++;
        }

        void markAsVariable() {
            alwaysConstant = false;
        }

        public int getBlockId() {
            if (instruction != null && instruction.getBlock() != null) {
                return instruction.getBlock().getId();
            }
            return -1;
        }

        public String getValueString() {
            if (value == null) return "null";
            if (value instanceof String) return "\"" + value + "\"";
            return value.toString();
        }

        public String getValueType() {
            if (value == null) return "null";
            return value.getClass().getSimpleName();
        }

        @Override
        public String toString() {
            return "ConstantResult[" +
                    "block=" + getBlockId() +
                    ", value=" + getValueString() +
                    ", type=" + getValueType() +
                    ", foldable=" + foldable +
                    ", executions=" + executionCount +
                    ", constant=" + alwaysConstant +
                    "]";
        }
    }
}
