package com.tonic.ui.editor.dual;

import lombok.Getter;

/**
 * An instruction location resolved from a bytecode display line: the owning method's name and
 * descriptor plus the instruction's bytecode offset.
 */
@Getter
public final class BcLocation {

    private final String methodName;
    private final String methodDesc;
    private final int pc;

    public BcLocation(String methodName, String methodDesc, int pc) {
        this.methodName = methodName;
        this.methodDesc = methodDesc;
        this.pc = pc;
    }

    /** The {@code name + desc} key used by the decompiler's per-method maps. */
    public String key() {
        return methodName + methodDesc;
    }
}
