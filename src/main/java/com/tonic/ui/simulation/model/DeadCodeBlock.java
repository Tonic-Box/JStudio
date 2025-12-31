package com.tonic.ui.simulation.model;

import com.tonic.analysis.ssa.cfg.IRBlock;
import lombok.Getter;

@Getter
public class DeadCodeBlock extends SimulationFinding {

    private final int blockId;
    private final int instructionCount;
    private final String blockLabel;
    private final boolean exceptionHandler;

    public DeadCodeBlock(String className, String methodName, String methodDesc,
                         IRBlock block, boolean isExceptionHandler) {
        super(className, methodName, methodDesc, FindingType.DEAD_CODE,
                isExceptionHandler ? Severity.LOW : Severity.INFO,
                -1);
        this.blockId = block.getId();
        this.instructionCount = block.getInstructions().size();
        this.blockLabel = block.getName();
        this.exceptionHandler = isExceptionHandler;
    }

    @Override
    public String getTitle() {
        if (exceptionHandler) {
            return "Unreachable Exception Handler (Block " + blockId + ")";
        }
        return "Dead Code Block (Block " + blockId + ")";
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Block ").append(blockId);
        if (blockLabel != null && !blockLabel.isEmpty()) {
            sb.append(" (").append(blockLabel).append(")");
        }
        sb.append(" was never reached during simulation.\n\n");
        sb.append("Instructions in block: ").append(instructionCount).append("\n\n");

        if (exceptionHandler) {
            sb.append("This block appears to be an exception handler that was never triggered.\n");
            sb.append("This is often normal - exception handlers are only reached when exceptions occur.\n");
        } else {
            sb.append("This may indicate:\n");
            sb.append("• Dead code from failed optimization\n");
            sb.append("• Unreachable code after opaque predicates\n");
            sb.append("• Obfuscation artifacts\n");
            sb.append("• Defensive code that can't be triggered\n");
        }

        return sb.toString();
    }

    @Override
    public String getRecommendation() {
        if (exceptionHandler) {
            return "Exception handlers are often unreachable in normal execution. " +
                    "Consider if this handler is still needed, but it may be valid defensive code.";
        }
        return "This block can potentially be removed as dead code. " +
                "Verify it's not reachable via paths not covered by simulation " +
                "(e.g., reflection, native calls, exception handlers).";
    }

    @Override
    public String toString() {
        return "DeadCodeBlock[block=" + blockId +
                ", instructions=" + instructionCount +
                ", exHandler=" + exceptionHandler + "]";
    }
}
