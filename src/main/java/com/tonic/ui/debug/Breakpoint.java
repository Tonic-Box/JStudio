package com.tonic.ui.debug;

import java.util.Objects;

/** A breakpoint keyed by dotted class, method name and descriptor, and bytecode offset; the offset is what maps it to both a source line and a bytecode line. */
public final class Breakpoint
{

    public final String className;
    public final String methodName;
    public final String methodDesc;
    public final long pc;

    /**
     * Creates a breakpoint at a bytecode offset.
     *
     * @param className the declaring class, dotted
     * @param methodName the method's name
     * @param methodDesc the method's JVM descriptor
     * @param pc the bytecode offset within the method
     */
    public Breakpoint(String className, String methodName, String methodDesc, long pc)
    {
        this.className = className;
        this.methodName = methodName;
        this.methodDesc = methodDesc;
        this.pc = pc;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof Breakpoint))
        {
            return false;
        }
        Breakpoint b = (Breakpoint) o;
        return pc == b.pc && className.equals(b.className) && methodName.equals(b.methodName)
                && methodDesc.equals(b.methodDesc);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(className, methodName, methodDesc, pc);
    }
}
