package com.tonic.ui.vm.debugger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DebugStateModel {
    private final String className;
    private final String methodName;
    private final String descriptor;
    private final int instructionIndex;
    private final int lineNumber;
    private final List<StackEntry> operandStack;
    private final List<LocalEntry> localVariables;
    private final List<FrameEntry> callStack;

    private DebugStateModel(Builder builder) {
        this.className = builder.className;
        this.methodName = builder.methodName;
        this.descriptor = builder.descriptor;
        this.instructionIndex = builder.instructionIndex;
        this.lineNumber = builder.lineNumber;
        this.operandStack = Collections.unmodifiableList(new ArrayList<>(builder.operandStack));
        this.localVariables = Collections.unmodifiableList(new ArrayList<>(builder.localVariables));
        this.callStack = Collections.unmodifiableList(new ArrayList<>(builder.callStack));
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

    public List<StackEntry> getOperandStack() {
        return operandStack;
    }

    public List<LocalEntry> getLocalVariables() {
        return localVariables;
    }

    public List<FrameEntry> getCallStack() {
        return callStack;
    }

    public String getSimpleClassName() {
        int lastSlash = className.lastIndexOf('/');
        return lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String className = "";
        private String methodName = "";
        private String descriptor = "";
        private int instructionIndex = 0;
        private int lineNumber = -1;
        private List<StackEntry> operandStack = new ArrayList<>();
        private List<LocalEntry> localVariables = new ArrayList<>();
        private List<FrameEntry> callStack = new ArrayList<>();

        public Builder className(String className) {
            this.className = className;
            return this;
        }

        public Builder methodName(String methodName) {
            this.methodName = methodName;
            return this;
        }

        public Builder descriptor(String descriptor) {
            this.descriptor = descriptor;
            return this;
        }

        public Builder instructionIndex(int instructionIndex) {
            this.instructionIndex = instructionIndex;
            return this;
        }

        public Builder lineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }

        public Builder operandStack(List<StackEntry> operandStack) {
            this.operandStack = operandStack;
            return this;
        }

        public Builder localVariables(List<LocalEntry> localVariables) {
            this.localVariables = localVariables;
            return this;
        }

        public Builder callStack(List<FrameEntry> callStack) {
            this.callStack = callStack;
            return this;
        }

        public DebugStateModel build() {
            return new DebugStateModel(this);
        }
    }
}
