package com.tonic.ui.debug;

import java.util.Objects;

/**
 * A breakpoint identified by its canonical location: the declaring class (dotted), the method (name + JVM
 * descriptor), and the bytecode offset. The offset is the single source of truth, so the same breakpoint maps
 * to a source line (via the decompiler line map) and to a bytecode line (via the disassembly index).
 */
public final class Breakpoint {

    public final String className;
    public final String methodName;
    public final String methodDesc;
    public final long pc;

    public Breakpoint(String className, String methodName, String methodDesc, long pc) {
        this.className = className;
        this.methodName = methodName;
        this.methodDesc = methodDesc;
        this.pc = pc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Breakpoint)) {
            return false;
        }
        Breakpoint b = (Breakpoint) o;
        return pc == b.pc && className.equals(b.className) && methodName.equals(b.methodName)
                && methodDesc.equals(b.methodDesc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, methodName, methodDesc, pc);
    }
}
