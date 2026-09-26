package com.tonic.ui.editor.llvm;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.llvm.LlvmLowering;
import com.tonic.analysis.ssa.llvm.LlvmLoweringConfig;
import com.tonic.parser.MethodEntry;
import lombok.Getter;

/** Lowers one method's SSA IR to textual LLVM IR for display; a method the lowerer cannot handle becomes a comment instead of failing the view. */
public class LLVMFormatter
{

    /**
     * -- GETTER --
     *  Get the method being formatted.
     */
    @Getter
    private final MethodEntry method;
    private final SSA ssa;
    private final LlvmLowering lowering;

    /**
     * Creates a formatter for one method, lowering with the full object model.
     *
     * @param method the method to lower
     * @param ssa the lifter, bound to the method's constant pool
     */
    public LLVMFormatter(MethodEntry method, SSA ssa)
    {
        this.method = method;
        this.ssa = ssa;
        this.lowering = new LlvmLowering(LlvmLoweringConfig.fullObjectModel());
    }

    /**
     * Lifts and lowers the method to LLVM IR.
     *
     * @return the LLVM IR text, or a comment line when the method has no code, is not lowerable, or lowering fails
     */
    public String format()
    {
        if (method.getCodeAttribute() == null)
        {
            return "; No code (abstract or native)\n";
        }

        try
        {
            IRMethod irMethod = ssa.lift(method);
            return lowering.lower(irMethod);
        }
        catch (UnsupportedOperationException e)
        {
            return "; [not lowerable to LLVM: " + e.getMessage() + "]\n";
        }
        catch (Exception e)
        {
            return "; Error lowering to LLVM: " + e.getMessage() + "\n";
        }
    }
}
