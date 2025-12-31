package com.tonic.ui.vm.heap.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public class ProvenanceInfo {
    private final String methodSignature;
    private final String className;
    private final String methodName;
    private final String descriptor;
    private final int pc;
    private final int lineNumber;
    private final List<StackFrameInfo> callStack;

    private ProvenanceInfo(Builder builder) {
        this.methodSignature = builder.methodSignature;
        this.className = builder.className;
        this.methodName = builder.methodName;
        this.descriptor = builder.descriptor;
        this.pc = builder.pc;
        this.lineNumber = builder.lineNumber;
        this.callStack = Collections.unmodifiableList(new ArrayList<>(builder.callStack));
    }

    public boolean hasCallStack() {
        return !callStack.isEmpty();
    }

    public String getShortLocation() {
        String shortClass = className;
        int lastSlash = className.lastIndexOf('/');
        if (lastSlash >= 0) {
            shortClass = className.substring(lastSlash + 1);
        }
        return shortClass + "." + methodName + "()";
    }

    public String getFullLocation() {
        return className + "." + methodName + descriptor + " @ PC " + pc;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getFullLocation());
        if (lineNumber > 0) {
            sb.append(" (line ").append(lineNumber).append(")");
        }
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String methodSignature = "";
        private String className = "";
        private String methodName = "";
        private String descriptor = "";
        private int pc;
        private int lineNumber = -1;
        private List<StackFrameInfo> callStack = new ArrayList<>();

        public Builder methodSignature(String methodSignature) {
            this.methodSignature = methodSignature;
            return this;
        }

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

        public Builder pc(int pc) {
            this.pc = pc;
            return this;
        }

        public Builder lineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }

        public Builder callStack(List<StackFrameInfo> callStack) {
            this.callStack = callStack != null ? callStack : new ArrayList<>();
            return this;
        }

        public Builder addStackFrame(StackFrameInfo frame) {
            this.callStack.add(frame);
            return this;
        }

        public ProvenanceInfo build() {
            return new ProvenanceInfo(this);
        }
    }

    @Getter
    @RequiredArgsConstructor
    public static class StackFrameInfo {
        private final String className;
        private final String methodName;
        private final String descriptor;
        private final int pc;
        private final int lineNumber;

        @Override
        public String toString() {
            String shortClass = className;
            int lastSlash = className.lastIndexOf('/');
            if (lastSlash >= 0) {
                shortClass = className.substring(lastSlash + 1);
            }
            String result = shortClass + "." + methodName + "() @ PC " + pc;
            if (lineNumber > 0) {
                result += " (line " + lineNumber + ")";
            }
            return result;
        }
    }
}
