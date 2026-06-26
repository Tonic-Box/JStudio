package com.tonic.live.debug;

import lombok.Getter;

/**
 * A resolved code location: the declaring class (dotted), the method (name + JVM descriptor), the bytecode
 * index, and the source line (-1 when no line information is available).
 */
@Getter
public final class DebugLocation {
    private final String className;
    private final String methodName;
    private final String methodDescriptor;
    private final long codeIndex;
    private final int lineNumber;

    public DebugLocation(String className, String methodName, String methodDescriptor, long codeIndex, int lineNumber) {
        this.className = className;
        this.methodName = methodName;
        this.methodDescriptor = methodDescriptor;
        this.codeIndex = codeIndex;
        this.lineNumber = lineNumber;
    }
}
