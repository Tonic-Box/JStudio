package com.tonic.ui.editor.dual;

import lombok.Getter;

/** An instruction location resolved from a bytecode display line: the owning method's name and descriptor plus the instruction's bytecode offset. */
@Getter
public final class BcLocation
{

    private final String methodName;
    private final String methodDesc;
    private final int pc;

    /**
     * Creates a location.
     *
     * @param methodName the owning method's name
     * @param methodDesc the owning method's descriptor
     * @param pc the instruction's bytecode offset
     */
    public BcLocation(String methodName, String methodDesc, int pc)
    {
        this.methodName = methodName;
        this.methodDesc = methodDesc;
        this.pc = pc;
    }

    /**
     * Builds the method key the decompiler's per-method maps use.
     *
     * @return the method name followed by its descriptor
     */
    public String key()
    {
        return methodName + methodDesc;
    }
}
