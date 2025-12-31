package com.tonic.ui.vm.model;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class ExecutionResult {

    private final boolean success;
    private final Object returnValue;
    private final String returnType;
    private final Throwable exception;
    private final long executionTimeMs;
    private final long instructionsExecuted;
    private final List<MethodCall> methodCalls;
    private final List<String> consoleOutput;

    private ExecutionResult(Builder builder) {
        this.success = builder.success;
        this.returnValue = builder.returnValue;
        this.returnType = builder.returnType;
        this.exception = builder.exception;
        this.executionTimeMs = builder.executionTimeMs;
        this.instructionsExecuted = builder.instructionsExecuted;
        this.methodCalls = builder.methodCalls == null ?
            Collections.emptyList() : List.copyOf(builder.methodCalls);
        this.consoleOutput = builder.consoleOutput == null ?
            Collections.emptyList() : List.copyOf(builder.consoleOutput);
    }

    public String getFormattedReturnValue() {
        if (!success) {
            if (exception != null) {
                return "Exception: " + exception.getClass().getSimpleName() + ": " + exception.getMessage();
            }
            return "Execution failed";
        }

        if (returnValue == null) {
            if ("V".equals(returnType)) {
                return "void";
            }
            return "null";
        }

        if (returnValue instanceof String) {
            return "\"" + returnValue + "\"";
        }

        if (returnValue instanceof Character) {
            return "'" + returnValue + "'";
        }

        if (returnValue instanceof Long) {
            return returnValue + "L";
        }

        if (returnValue instanceof Float) {
            return returnValue + "f";
        }

        if (returnValue instanceof Double) {
            return returnValue + "d";
        }

        return String.valueOf(returnValue);
    }

    public String getFormattedStatistics() {
        StringBuilder sb = new StringBuilder();
        sb.append("Time: ").append(executionTimeMs).append("ms");
        if (instructionsExecuted > 0) {
            sb.append(", Instructions: ").append(instructionsExecuted);
        }
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean success;
        private Object returnValue;
        private String returnType;
        private Throwable exception;
        private long executionTimeMs;
        private long instructionsExecuted;
        private List<MethodCall> methodCalls;
        private List<String> consoleOutput;

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder returnValue(Object returnValue) {
            this.returnValue = returnValue;
            return this;
        }

        public Builder returnType(String returnType) {
            this.returnType = returnType;
            return this;
        }

        public Builder exception(Throwable exception) {
            this.exception = exception;
            return this;
        }

        public Builder executionTimeMs(long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
            return this;
        }

        public Builder instructionsExecuted(long instructionsExecuted) {
            this.instructionsExecuted = instructionsExecuted;
            return this;
        }

        public Builder methodCalls(List<MethodCall> methodCalls) {
            this.methodCalls = methodCalls;
            return this;
        }

        public Builder consoleOutput(List<String> consoleOutput) {
            this.consoleOutput = consoleOutput;
            return this;
        }

        public ExecutionResult build() {
            return new ExecutionResult(this);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ExecutionResult{");
        sb.append("success=").append(success);
        if (returnValue != null) {
            sb.append(", returnValue=").append(getFormattedReturnValue());
        }
        if (exception != null) {
            sb.append(", exception=").append(exception.getClass().getSimpleName());
        }
        sb.append(", time=").append(executionTimeMs).append("ms");
        sb.append('}');
        return sb.toString();
    }
}
