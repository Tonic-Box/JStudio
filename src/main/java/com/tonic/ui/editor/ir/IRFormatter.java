package com.tonic.ui.editor.ir;

import com.tonic.analysis.ssa.IRPrinter;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.PhiInstruction;
import com.tonic.parser.MethodEntry;
import lombok.Getter;

/** Formats one method's lifted SSA IR as block-structured text for the IR view. */
public class IRFormatter
{

    /**
     * -- GETTER --
     *  Get the method being formatted.
     */
    @Getter
    private final MethodEntry method;
    private final SSA ssa;

    /**
     * Creates a formatter for one method.
     *
     * @param method the method to lift and format
     * @param ssa the lifter, bound to the method's constant pool
     */
    public IRFormatter(MethodEntry method, SSA ssa)
    {
        this.method = method;
        this.ssa = ssa;
    }

    /**
     * Lifts the method to SSA IR and formats it.
     *
     * @return the IR text, or a comment line when the method has no code or lifting fails
     */
    public String format()
    {
        if (method.getCodeAttribute() == null)
        {
            return "// No code (abstract or native)\n";
        }

        try
        {
            IRMethod irMethod = ssa.lift(method);
            return formatIRMethod(irMethod);
        }
        catch (Exception e)
        {
            return "// Error lifting to IR: " + e.getMessage() + "\n";
        }
    }

    private String formatIRMethod(IRMethod irMethod)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("// Method: ").append(irMethod.getName()).append(irMethod.getDescriptor()).append("\n");
        sb.append("// Blocks: ").append(irMethod.getBlocks().size()).append("\n");
        sb.append("\n");

        for (IRBlock block : irMethod.getBlocksInOrder())
        {
            sb.append(formatBlock(block));
            sb.append("\n");
        }

        return sb.toString();
    }

    private String formatBlock(IRBlock block)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("BLOCK ").append(block.getName()).append(":\n");

        if (!block.getPredecessors().isEmpty())
        {
            sb.append("  // pred: ");
            boolean first = true;
            for (IRBlock pred : block.getPredecessors())
            {
                if (!first) sb.append(", ");
                sb.append(pred.getName());
                first = false;
            }
            sb.append("\n");
        }

        if (!block.getSuccessors().isEmpty())
        {
            sb.append("  // succ: ");
            boolean first = true;
            for (IRBlock succ : block.getSuccessors())
            {
                if (!first) sb.append(", ");
                sb.append(succ.getName());
                first = false;
            }
            sb.append("\n");
        }

        for (PhiInstruction phi : block.getPhiInstructions())
        {
            sb.append("  PHI: ").append(IRPrinter.format(phi)).append("\n");
        }

        for (IRInstruction instr : block.getInstructions())
        {
            sb.append("  ").append(IRPrinter.format(instr)).append("\n");
        }

        return sb.toString();
    }

}
