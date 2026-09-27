package com.tonic.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationResult;
import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.simulation.listener.AbstractListener;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.BranchInstruction;
import com.tonic.simulation.model.SimulationFinding;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** A simulation listener that flags opaque predicates: branches whose condition constant operands decide the same way on every visit. */
public class OpaquePredicateListener extends AbstractListener
{

    private final Map<BranchInstruction, BranchAnalysis> branchAnalyses = new IdentityHashMap<>();
    private final List<BranchAnalysis> confirmedOpaquePredicates = new ArrayList<>();

    @Override
    public void onSimulationStart(IRMethod method)
    {
        super.onSimulationStart(method);
        branchAnalyses.clear();
        confirmedOpaquePredicates.clear();
    }

    @Override
    public void onBranch(BranchInstruction instr, Boolean outcome, SimulationState state)
    {
        if (instr == null || instr.getCondition() == null)
        {
            return;
        }
        branchAnalyses.computeIfAbsent(instr, BranchAnalysis::new).recordExecution(outcome);
    }

    @Override
    public void onSimulationEnd(IRMethod method, SimulationResult result)
    {
        super.onSimulationEnd(method, result);

        for (BranchAnalysis analysis : branchAnalyses.values())
        {
            if (analysis.isOpaque())
            {
                confirmedOpaquePredicates.add(analysis);
            }
        }
    }

    /**
     * Lists every branch seen during the last simulation.
     *
     * @return a copy of the per-branch analyses
     */
    public List<BranchAnalysis> getAnalyzedBranches()
    {
        return List.copyOf(branchAnalyses.values());
    }

    /**
     * Lists the branches confirmed opaque when the last simulation ended.
     *
     * @return the opaque branches, unmodifiable
     */
    public List<BranchAnalysis> getOpaquePredicates()
    {
        return Collections.unmodifiableList(confirmedOpaquePredicates);
    }

    /**
     * Counts the confirmed opaque branches.
     *
     * @return the number of opaque predicates
     */
    public int getOpaquePredicateCount()
    {
        return confirmedOpaquePredicates.size();
    }

    /**
     * Reports whether any opaque branch was confirmed.
     *
     * @return true if at least one opaque predicate was found
     */
    public boolean hasOpaquePredicates()
    {
        return !confirmedOpaquePredicates.isEmpty();
    }

    /** The recorded outcomes of one branch instruction across a simulation. */
    @Getter
    public static class BranchAnalysis
    {
        private final BranchInstruction instruction;
        private int trueCount = 0;
        private int falseCount = 0;
        private int unknownCount = 0;
        private int executionCount = 0;

        /**
         * Creates an empty record for a branch.
         *
         * @param instruction the branch instruction
         */
        public BranchAnalysis(BranchInstruction instruction)
        {
            this.instruction = instruction;
        }

        /**
         * Records one visit of the branch.
         *
         * @param outcome true or false when constant operands decided the condition, null when it depended on runtime values
         */
        public void recordExecution(Boolean outcome)
        {
            executionCount++;
            if (outcome == null)
            {
                unknownCount++;
            }
            else if (outcome)
            {
                trueCount++;
            }
            else
            {
                falseCount++;
            }
        }

        /**
         * Reports whether every visit proved the condition and all went the same way.
         *
         * @return true if visited at least once, never undecided, and only true or only false
         */
        public boolean isOpaque()
        {
            return isAlwaysTrue() || isAlwaysFalse();
        }

        /**
         * Reports whether every visit proved the condition true.
         *
         * @return true if visited at least once with only proven-true outcomes
         */
        public boolean isAlwaysTrue()
        {
            return executionCount > 0 && trueCount == executionCount;
        }

        /**
         * Reports whether every visit proved the condition false.
         *
         * @return true if visited at least once with only proven-false outcomes
         */
        public boolean isAlwaysFalse()
        {
            return executionCount > 0 && falseCount == executionCount;
        }

        /**
         * Returns the id of the IR block holding the branch.
         *
         * @return the block id, or -1 if the instruction or its block is missing
         */
        public int getBlockId()
        {
            if (instruction != null && instruction.getBlock() != null)
            {
                return instruction.getBlock().getId();
            }
            return -1;
        }

        /**
         * Returns the branch instruction's bytecode offset.
         *
         * @return the offset the lifter stamped on the instruction, else its block's, else -1
         */
        public int getBytecodeOffset()
        {
            return SimulationFinding.offsetOf(instruction);
        }

        @Override
        public String toString()
        {
            return "BranchAnalysis[" +
                    "block=" + getBlockId() +
                    ", executions=" + executionCount +
                    ", true=" + trueCount +
                    ", false=" + falseCount +
                    ", undecided=" + unknownCount +
                    ", opaque=" + isOpaque() +
                    "]";
        }
    }
}
