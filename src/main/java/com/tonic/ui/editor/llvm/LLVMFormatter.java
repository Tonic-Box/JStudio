package com.tonic.ui.editor.llvm;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.llvm.LlvmLowering;
import com.tonic.analysis.ssa.llvm.LlvmLoweringConfig;
import com.tonic.parser.MethodEntry;
import lombok.Getter;

/**
 * Lowers a single method's SSA IR to textual LLVM IR for display in the UI.
 * Methods outside the lowerer's computational subset are reported as a comment rather than
 * aborting the whole view.
 */
public class LLVMFormatter {

    /**
     * -- GETTER --
     *  Get the method being formatted.
     */
    @Getter
    private final MethodEntry method;
    private final SSA ssa;
    private final LlvmLowering lowering;

    public LLVMFormatter(MethodEntry method, SSA ssa) {
        this.method = method;
        this.ssa = ssa;
        this.lowering = new LlvmLowering(LlvmLoweringConfig.fullObjectModel());
    }

    /**
     * Format the method's LLVM IR for display.
     */
    public String format() {
        if (method.getCodeAttribute() == null) {
            return "; No code (abstract or native)\n";
        }

        try {
            IRMethod irMethod = ssa.lift(method);
            return lowering.lower(irMethod);
        } catch (UnsupportedOperationException e) {
            return "; [not lowerable to LLVM: " + e.getMessage() + "]\n";
        } catch (Exception e) {
            return "; Error lowering to LLVM: " + e.getMessage() + "\n";
        }
    }
}
