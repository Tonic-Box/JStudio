package com.tonic.ui.vm.heap.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import java.util.ArrayList;
import java.util.List;

/** Where a heap event happened: the method, pc and source line, with the call stack leading to it. */
@Getter
public class ProvenanceInfo
{
    private final String methodSignature;
    private final String className;
    private final String methodName;
    private final String descriptor;
    private final int pc;
    private final int lineNumber;
    private final List<StackFrameInfo> callStack;

    private ProvenanceInfo(Builder builder)
    {
        this.methodSignature = builder.methodSignature;
        this.className = builder.className;
        this.methodName = builder.methodName;
        this.descriptor = builder.descriptor;
        this.pc = builder.pc;
        this.lineNumber = builder.lineNumber;
        this.callStack = List.copyOf(builder.callStack);
    }

    /**
     * Tells whether a call stack was recorded.
     *
     * @return true when the call stack is not empty
     */
    public boolean hasCallStack()
    {
        return !callStack.isEmpty();
    }

    /**
     * Formats the location as a simple class name and method.
     *
     * @return SimpleClass.method()
     */
    public String getShortLocation()
    {
        String shortClass = className;
        int lastSlash = className.lastIndexOf('/');
        if (lastSlash >= 0)
        {
            shortClass = className.substring(lastSlash + 1);
        }
        return shortClass + "." + methodName + "()";
    }

    /**
     * Formats the location with the full class name, descriptor and pc.
     *
     * @return owner.methodDescriptor @ PC n
     */
    public String getFullLocation()
    {
        return className + "." + methodName + descriptor + " @ PC " + pc;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(getFullLocation());
        if (lineNumber > 0)
        {
            sb.append(" (line ").append(lineNumber).append(")");
        }
        return sb.toString();
    }

    /**
     * Starts a new provenance builder.
     *
     * @return an empty builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /** Builder for provenance records; strings default to empty, the line number to -1 and the call stack to empty. */
    public static class Builder
    {
        private String methodSignature = "";
        private String className = "";
        private String methodName = "";
        private String descriptor = "";
        private int pc;
        private int lineNumber = -1;
        private List<StackFrameInfo> callStack = new ArrayList<>();

        /**
         * Sets the method signature.
         *
         * @param methodSignature the method's full signature
         * @return this builder
         */
        public Builder methodSignature(String methodSignature)
        {
            this.methodSignature = methodSignature;
            return this;
        }

        /**
         * Sets the class.
         *
         * @param className the class's internal name
         * @return this builder
         */
        public Builder className(String className)
        {
            this.className = className;
            return this;
        }

        /**
         * Sets the method name.
         *
         * @param methodName the method name
         * @return this builder
         */
        public Builder methodName(String methodName)
        {
            this.methodName = methodName;
            return this;
        }

        /**
         * Sets the method descriptor.
         *
         * @param descriptor the method descriptor
         * @return this builder
         */
        public Builder descriptor(String descriptor)
        {
            this.descriptor = descriptor;
            return this;
        }

        /**
         * Sets the bytecode offset.
         *
         * @param pc the instruction's offset in the method
         * @return this builder
         */
        public Builder pc(int pc)
        {
            this.pc = pc;
            return this;
        }

        /**
         * Sets the source line.
         *
         * @param lineNumber the source line, or -1 if unknown
         * @return this builder
         */
        public Builder lineNumber(int lineNumber)
        {
            this.lineNumber = lineNumber;
            return this;
        }

        /**
         * Replaces the call stack; the list is used directly, not copied.
         *
         * @param callStack the frames, innermost first, or null for none
         * @return this builder
         */
        public Builder callStack(List<StackFrameInfo> callStack)
        {
            this.callStack = callStack != null ? callStack : new ArrayList<>();
            return this;
        }

        /**
         * Appends one call stack frame.
         *
         * @param frame the frame
         * @return this builder
         */
        public Builder addStackFrame(StackFrameInfo frame)
        {
            this.callStack.add(frame);
            return this;
        }

        /**
         * Builds the provenance record, copying the call stack.
         *
         * @return the provenance record
         */
        public ProvenanceInfo build()
        {
            return new ProvenanceInfo(this);
        }
    }

    /** One frame of a provenance call stack: method, pc and source line. */
    @Getter
    @RequiredArgsConstructor
    public static class StackFrameInfo
    {
        private final String className;
        private final String methodName;
        private final String descriptor;
        private final int pc;
        private final int lineNumber;

        @Override
        public String toString()
        {
            String shortClass = className;
            int lastSlash = className.lastIndexOf('/');
            if (lastSlash >= 0)
            {
                shortClass = className.substring(lastSlash + 1);
            }
            String result = shortClass + "." + methodName + "() @ PC " + pc;
            if (lineNumber > 0)
            {
                result += " (line " + lineNumber + ")";
            }
            return result;
        }
    }
}
