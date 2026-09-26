package com.tonic.live.debug;

import lombok.Getter;

/** A resolved code location: the declaring class, the method, the bytecode index, and the source line. */
@Getter
public final class DebugLocation
{
    private final String className;
    private final String methodName;
    private final String methodDescriptor;
    private final long codeIndex;
    private final int lineNumber;

    /**
     * Creates a location.
     *
     * @param className the declaring class's binary name, with dots
     * @param methodName the method name
     * @param methodDescriptor the method's JVM descriptor
     * @param codeIndex the bytecode index within the method
     * @param lineNumber the source line, or -1 when there is no line information
     */
    public DebugLocation(String className, String methodName, String methodDescriptor, long codeIndex, int lineNumber)
    {
        this.className = className;
        this.methodName = methodName;
        this.methodDescriptor = methodDescriptor;
        this.codeIndex = codeIndex;
        this.lineNumber = lineNumber;
    }
}
