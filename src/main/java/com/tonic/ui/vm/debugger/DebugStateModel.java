package com.tonic.ui.vm.debugger;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
@Builder
public class DebugStateModel {
    @Builder.Default
    private final String className = "";
    @Builder.Default
    private final String methodName = "";
    @Builder.Default
    private final String descriptor = "";
    @Builder.Default
    private final int instructionIndex = 0;
    @Builder.Default
    private final int lineNumber = -1;
    @Builder.Default
    private final List<StackEntry> operandStack = Collections.emptyList();
    @Builder.Default
    private final List<LocalEntry> localVariables = Collections.emptyList();
    @Builder.Default
    private final List<FrameEntry> callStack = Collections.emptyList();

    public String getSimpleClassName() {
        int lastSlash = className.lastIndexOf('/');
        return lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
    }
}
