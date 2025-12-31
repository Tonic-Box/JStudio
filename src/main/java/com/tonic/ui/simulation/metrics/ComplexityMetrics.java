package com.tonic.ui.simulation.metrics;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.BranchInstruction;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.SwitchInstruction;

public class ComplexityMetrics {

    private final int cyclomaticComplexity;
    private final int blockCount;
    private final int edgeCount;
    private final int branchCount;
    private final int switchCaseCount;
    private final int loopCount;
    private final int maxNestingDepth;
    private final int instructionCount;

    public ComplexityMetrics(IRMethod method) {
        if (method == null) {
            this.cyclomaticComplexity = 1;
            this.blockCount = 0;
            this.edgeCount = 0;
            this.branchCount = 0;
            this.switchCaseCount = 0;
            this.loopCount = 0;
            this.maxNestingDepth = 0;
            this.instructionCount = 0;
            return;
        }

        this.blockCount = method.getBlocks().size();
        this.instructionCount = countInstructions(method);

        int edges = 0;
        int branches = 0;
        int switchCases = 0;
        int loops = 0;

        for (IRBlock block : method.getBlocks()) {
            edges += block.getSuccessors().size();

            for (IRBlock succ : block.getSuccessors()) {
                if (succ.getId() < block.getId()) {
                    loops++;
                }
            }

            IRInstruction terminator = block.getTerminator();
            if (terminator instanceof BranchInstruction) {
                branches++;
            } else if (terminator instanceof SwitchInstruction) {
                SwitchInstruction sw = (SwitchInstruction) terminator;
                switchCases += sw.getCases().size();
            }
        }

        this.edgeCount = edges;
        this.branchCount = branches;
        this.switchCaseCount = switchCases;
        this.loopCount = loops;

        int n = blockCount;
        int e = edgeCount;
        int p = 1;
        this.cyclomaticComplexity = Math.max(1, e - n + 2 * p);

        this.maxNestingDepth = estimateMaxNesting(method);
    }

    private int countInstructions(IRMethod method) {
        int count = 0;
        for (IRBlock block : method.getBlocks()) {
            count += block.getInstructions().size();
            count += block.getPhiInstructions().size();
        }
        return count;
    }

    private int estimateMaxNesting(IRMethod method) {
        int maxDepth = 0;
        for (IRBlock block : method.getBlocks()) {
            int depth = block.getPredecessors().size();
            if (depth > maxDepth) {
                maxDepth = depth;
            }
        }
        return Math.min(maxDepth, 10);
    }

    public int getCyclomaticComplexity() {
        return cyclomaticComplexity;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public int getEdgeCount() {
        return edgeCount;
    }

    public int getBranchCount() {
        return branchCount;
    }

    public int getSwitchCaseCount() {
        return switchCaseCount;
    }

    public int getLoopCount() {
        return loopCount;
    }

    public int getMaxNestingDepth() {
        return maxNestingDepth;
    }

    public int getInstructionCount() {
        return instructionCount;
    }

    public String getComplexityRating() {
        if (cyclomaticComplexity <= 5) {
            return "Simple";
        } else if (cyclomaticComplexity <= 10) {
            return "Moderate";
        } else if (cyclomaticComplexity <= 20) {
            return "Complex";
        } else {
            return "Very Complex";
        }
    }

    public String getSummary() {
        return String.format("CC=%d (%s), Blocks=%d, Branches=%d, Loops=%d",
                cyclomaticComplexity, getComplexityRating(),
                blockCount, branchCount, loopCount);
    }

    @Override
    public String toString() {
        String sb = "ComplexityMetrics[\n" +
                "  Cyclomatic Complexity: " + cyclomaticComplexity +
                " (" + getComplexityRating() + ")\n" +
                "  Basic Blocks: " + blockCount + "\n" +
                "  Edges: " + edgeCount + "\n" +
                "  Branch Points: " + branchCount + "\n" +
                "  Switch Cases: " + switchCaseCount + "\n" +
                "  Estimated Loops: " + loopCount + "\n" +
                "  Max Nesting: " + maxNestingDepth + "\n" +
                "  Instructions: " + instructionCount + "\n" +
                "]";
        return sb;
    }
}
