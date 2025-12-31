package com.tonic.ui.vm.debugger;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class FrameEntry {
    private final String className;
    private final String methodName;
    private final String descriptor;
    private final int instructionIndex;
    private final int lineNumber;
    private final boolean current;

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
