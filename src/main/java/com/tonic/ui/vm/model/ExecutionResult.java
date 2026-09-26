package com.tonic.ui.vm.model;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

/** The outcome of one VM run: success, return value or exception, timing, and any recorded calls and console output. */
@Getter
public class ExecutionResult
{

    private final boolean success;
    private final Object returnValue;
    private final String returnType;
    private final Throwable exception;
    private final long executionTimeMs;
    private final long instructionsExecuted;
    private final List<MethodCall> methodCalls;
    private final List<String> consoleOutput;

    private ExecutionResult(Builder builder)
    {
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

    /**
     * Formats the outcome for display: the exception on failure, void or null for no value, and Java literal syntax for strings, chars, longs, floats and doubles.
     *
     * @return the display text
     */
    public String getFormattedReturnValue()
    {
        if (!success)
        {
            if (exception != null)
            {
                return "Exception: " + exception.getClass().getSimpleName() + ": " + exception.getMessage();
            }
            return "Execution failed";
        }

        if (returnValue == null)
        {
            if ("V".equals(returnType))
            {
                return "void";
            }
            return "null";
        }

        if (returnValue instanceof String)
        {
            return "\"" + returnValue + "\"";
        }

        if (returnValue instanceof Character)
        {
            return "'" + returnValue + "'";
        }

        if (returnValue instanceof Long)
        {
            return returnValue + "L";
        }

        if (returnValue instanceof Float)
        {
            return returnValue + "f";
        }

        if (returnValue instanceof Double)
        {
            return returnValue + "d";
        }

        return String.valueOf(returnValue);
    }

    /**
     * Formats the run time and, when counted, the instruction count.
     *
     * @return text such as "Time: 12ms, Instructions: 400"
     */
    public String getFormattedStatistics()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Time: ").append(executionTimeMs).append("ms");
        if (instructionsExecuted > 0)
        {
            sb.append(", Instructions: ").append(instructionsExecuted);
        }
        return sb.toString();
    }

    /**
     * Starts building a result.
     *
     * @return a builder with every field unset
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /** Builds an execution result; unset lists become empty and set lists are copied. */
    public static class Builder
    {
        private boolean success;
        private Object returnValue;
        private String returnType;
        private Throwable exception;
        private long executionTimeMs;
        private long instructionsExecuted;
        private List<MethodCall> methodCalls;
        private List<String> consoleOutput;

        /**
         * Sets whether the run succeeded.
         *
         * @param success true if it completed normally
         * @return this builder
         */
        public Builder success(boolean success)
        {
            this.success = success;
            return this;
        }

        /**
         * Sets the value the method returned.
         *
         * @param returnValue the host value, or null
         * @return this builder
         */
        public Builder returnValue(Object returnValue)
        {
            this.returnValue = returnValue;
            return this;
        }

        /**
         * Sets the method's return type.
         *
         * @param returnType the return type descriptor; "V" formats a null value as void
         * @return this builder
         */
        public Builder returnType(String returnType)
        {
            this.returnType = returnType;
            return this;
        }

        /**
         * Sets the exception that ended the run.
         *
         * @param exception the exception, or null
         * @return this builder
         */
        public Builder exception(Throwable exception)
        {
            this.exception = exception;
            return this;
        }

        /**
         * Sets the wall-clock run time.
         *
         * @param executionTimeMs the time in milliseconds
         * @return this builder
         */
        public Builder executionTimeMs(long executionTimeMs)
        {
            this.executionTimeMs = executionTimeMs;
            return this;
        }

        /**
         * Sets the number of instructions executed.
         *
         * @param instructionsExecuted the count; zero leaves it out of the statistics
         * @return this builder
         */
        public Builder instructionsExecuted(long instructionsExecuted)
        {
            this.instructionsExecuted = instructionsExecuted;
            return this;
        }

        /**
         * Sets the calls recorded during the run.
         *
         * @param methodCalls the calls in the order they started, or null for none
         * @return this builder
         */
        public Builder methodCalls(List<MethodCall> methodCalls)
        {
            this.methodCalls = methodCalls;
            return this;
        }

        /**
         * Sets the console output captured during the run.
         *
         * @param consoleOutput the output lines, or null for none
         * @return this builder
         */
        public Builder consoleOutput(List<String> consoleOutput)
        {
            this.consoleOutput = consoleOutput;
            return this;
        }

        /**
         * Creates the result.
         *
         * @return the immutable result
         */
        public ExecutionResult build()
        {
            return new ExecutionResult(this);
        }
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("ExecutionResult{");
        sb.append("success=").append(success);
        if (returnValue != null)
        {
            sb.append(", returnValue=").append(getFormattedReturnValue());
        }
        if (exception != null)
        {
            sb.append(", exception=").append(exception.getClass().getSimpleName());
        }
        sb.append(", time=").append(executionTimeMs).append("ms");
        sb.append('}');
        return sb.toString();
    }
}
