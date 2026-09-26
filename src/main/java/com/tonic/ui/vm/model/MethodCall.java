package com.tonic.ui.vm.model;

import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.Array;

/** One call recorded while tracing a VM run: the target method, its arguments and depth, and its return value, timing and whether it threw. */
@Getter
public class MethodCall
{

    private final String ownerClass;
    private final String methodName;
    private final String descriptor;
    private final Object[] arguments;
    private final boolean staticMethod;
    private final int depth;
    @Setter
    private Object returnValue;
    @Setter
    private long startTimeNanos;
    @Setter
    private long endTimeNanos;
    @Setter
    private boolean exceptional;

    /**
     * Creates a call record whose start time is now.
     *
     * @param ownerClass the owner's internal name, with slashes
     * @param methodName the method name
     * @param descriptor the method descriptor
     * @param arguments the argument values, copied; null for none
     * @param isStatic whether the method is static
     * @param depth the call depth, zero for the traced method
     */
    public MethodCall(String ownerClass, String methodName, String descriptor, Object[] arguments, boolean isStatic, int depth)
    {
        this.ownerClass = ownerClass;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.arguments = arguments != null ? arguments.clone() : new Object[0];
        this.staticMethod = isStatic;
        this.depth = depth;
        this.startTimeNanos = System.nanoTime();
    }

    /**
     * Copies the argument values.
     *
     * @return a copy of the arguments
     */
    public Object[] getArguments()
    {
        return arguments.clone();
    }

    /**
     * Computes how long the call ran.
     *
     * @return the end time minus the start time in nanoseconds; meaningless until the end time is set
     */
    public long getDurationNanos()
    {
        return endTimeNanos - startTimeNanos;
    }

    /**
     * Strips the package from the owner's name.
     *
     * @return the owner's simple name
     */
    public String getSimpleOwnerName()
    {
        int lastSlash = ownerClass.lastIndexOf('/');
        return lastSlash >= 0 ? ownerClass.substring(lastSlash + 1) : ownerClass;
    }

    /**
     * Builds the full signature.
     *
     * @return the dotted owner name, method name and descriptor
     */
    public String getSignature()
    {
        return ownerClass.replace('/', '.') + "." + methodName + descriptor;
    }

    /**
     * Builds a short signature without parameter types.
     *
     * @return the simple owner name and method name followed by empty parentheses
     */
    public String getShortSignature()
    {
        return getSimpleOwnerName() + "." + methodName + "()";
    }

    /**
     * Builds the short signature indented two spaces per call depth.
     *
     * @return the indented text
     */
    public String getIndentedString()
    {
        return "  ".repeat(Math.max(0, depth)) +
                getShortSignature();
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(ownerClass.replace('/', '.'));
        sb.append('.').append(methodName);
        sb.append('(');
        for (int i = 0; i < arguments.length; i++)
        {
            if (i > 0) sb.append(", ");
            sb.append(formatValue(arguments[i]));
        }
        sb.append(')');
        return sb.toString();
    }

    /**
     * Formats a recorded value for display: strings quoted and cut at 50 characters, chars quoted, and arrays of any element type listed element by element.
     *
     * @param value the value, or null
     * @return the display text
     */
    public static String formatValue(Object value)
    {
        if (value == null)
        {
            return "null";
        }
        if (value instanceof String)
        {
            String s = (String) value;
            if (s.length() > 50)
            {
                return "\"" + s.substring(0, 47) + "...\"";
            }
            return "\"" + s + "\"";
        }
        if (value instanceof Character)
        {
            return "'" + value + "'";
        }
        if (value.getClass().isArray())
        {
            StringBuilder sb = new StringBuilder(value.getClass().getComponentType().getSimpleName()).append("[]{");
            for (int i = 0; i < Array.getLength(value); i++)
            {
                if (i > 0)
                {
                    sb.append(", ");
                }
                sb.append(formatValue(Array.get(value, i)));
            }
            return sb.append('}').toString();
        }
        return String.valueOf(value);
    }

    /**
     * Starts building a call record.
     *
     * @return a builder with every field unset
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /** Builds a method call record with the start time taken at build time. */
    public static class Builder
    {
        private String ownerClass;
        private String methodName;
        private String descriptor;
        private Object[] arguments;
        private boolean staticMethod;
        private int depth;

        /**
         * Sets the owner class.
         *
         * @param ownerClass the owner's internal name, with slashes
         * @return this builder
         */
        public Builder ownerClass(String ownerClass)
        {
            this.ownerClass = ownerClass;
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
         * Sets the argument values.
         *
         * @param arguments the argument values, or null for none
         * @return this builder
         */
        public Builder arguments(Object[] arguments)
        {
            this.arguments = arguments;
            return this;
        }

        /**
         * Sets whether the method is static.
         *
         * @param staticMethod true for a static method
         * @return this builder
         */
        public Builder staticMethod(boolean staticMethod)
        {
            this.staticMethod = staticMethod;
            return this;
        }

        /**
         * Sets the call depth.
         *
         * @param depth the call depth, zero for the traced method
         * @return this builder
         */
        public Builder depth(int depth)
        {
            this.depth = depth;
            return this;
        }

        /**
         * Creates the call record.
         *
         * @return the record, with its start time set to now
         */
        public MethodCall build()
        {
            return new MethodCall(ownerClass, methodName, descriptor, arguments, staticMethod, depth);
        }
    }
}
