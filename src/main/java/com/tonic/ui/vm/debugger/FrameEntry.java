package com.tonic.ui.vm.debugger;

public class FrameEntry {
    private final String className;
    private final String methodName;
    private final String descriptor;
    private final int instructionIndex;
    private final int lineNumber;
    private final boolean current;

    public FrameEntry(String className, String methodName, String descriptor,
                      int instructionIndex, int lineNumber, boolean current) {
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.instructionIndex = instructionIndex;
        this.lineNumber = lineNumber;
        this.current = current;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public int getInstructionIndex() {
        return instructionIndex;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public boolean isCurrent() {
        return current;
    }

    public String getSimpleClassName() {
        int lastSlash = className.lastIndexOf('/');
        return lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (current) sb.append("> ");
        sb.append(getSimpleClassName()).append(".").append(methodName);
        if (lineNumber > 0) {
            sb.append(":").append(lineNumber);
        } else {
            sb.append(" @").append(instructionIndex);
        }
        return sb.toString();
    }
}
