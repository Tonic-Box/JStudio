package com.tonic.ui.editor.ir;

import com.tonic.analysis.ssa.IRPrinter;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.PhiInstruction;
import com.tonic.parser.MethodEntry;
import lombok.Getter;

/**
 * Formats SSA IR for display in the UI.
 * Uses IRPrinter for instruction formatting but adds structure for UI display.
 */
public class IRFormatter {

    /**
     * -- GETTER --
     *  Get the method being formatted.
     */
    @Getter
    private final MethodEntry method;
    private final SSA ssa;

    public IRFormatter(MethodEntry method, SSA ssa) {
        this.method = method;
        this.ssa = ssa;
    }

    /**
     * Format the method's IR for display.
     */
    public String format() {
        if (method.getCodeAttribute() == null) {
            return "// No code (abstract or native)\n";
        }

        try {
            IRMethod irMethod = ssa.lift(method);
            return formatIRMethod(irMethod);
        } catch (Exception e) {
            return "// Error lifting to IR: " + e.getMessage() + "\n";
        }
    }

    /**
     * Format an IRMethod for display with block structure.
     */
    private String formatIRMethod(IRMethod irMethod) {
        StringBuilder sb = new StringBuilder();

        // Method header
        sb.append("// Method: ").append(irMethod.getName()).append(irMethod.getDescriptor()).append("\n");
        sb.append("// Blocks: ").append(irMethod.getBlocks().size()).append("\n");
        sb.append("\n");

        // Each block
        for (IRBlock block : irMethod.getBlocksInOrder()) {
            sb.append(formatBlock(block));
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Format a single block with predecessors/successors and instructions.
     */
    private String formatBlock(IRBlock block) {
        StringBuilder sb = new StringBuilder();

        // Block header
        sb.append("BLOCK ").append(block.getName()).append(":\n");

        // Predecessors
        if (!block.getPredecessors().isEmpty()) {
            sb.append("  // pred: ");
            boolean first = true;
            for (IRBlock pred : block.getPredecessors()) {
                if (!first) sb.append(", ");
                sb.append(pred.getName());
                first = false;
            }
            sb.append("\n");
        }

        // Successors
        if (!block.getSuccessors().isEmpty()) {
            sb.append("  // succ: ");
            boolean first = true;
            for (IRBlock succ : block.getSuccessors()) {
                if (!first) sb.append(", ");
                sb.append(succ.getName());
                first = false;
            }
            sb.append("\n");
        }

        // Phi instructions
        for (PhiInstruction phi : block.getPhiInstructions()) {
            sb.append("  PHI: ").append(IRPrinter.format(phi)).append("\n");
        }

        // Regular instructions
        for (IRInstruction instr : block.getInstructions()) {
            sb.append("  ").append(IRPrinter.format(instr)).append("\n");
        }

        return sb.toString();
    }

}
