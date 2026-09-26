package com.tonic.plugin.result;

import lombok.Getter;

import java.util.Objects;

/** Where a finding is: a class, optionally a method with its descriptor or a field, and a line and instruction index, which are -1 when unknown. */
@Getter
public class Location
{

    private final String className;
    private final String methodName;
    private final String methodDescriptor;
    private final int lineNumber;
    private final int instructionIndex;
    private final String fieldName;

    private Location(Builder builder)
    {
        this.className = builder.className;
        this.methodName = builder.methodName;
        this.methodDescriptor = builder.methodDescriptor;
        this.lineNumber = builder.lineNumber;
        this.instructionIndex = builder.instructionIndex;
        this.fieldName = builder.fieldName;
    }

    /**
     * Returns the class name without its package.
     *
     * @return the simple name, or null when no class is set
     */
    public String getSimpleClassName()
    {
        if (className == null) return null;
        int lastDot = className.lastIndexOf('.');
        int lastSlash = className.lastIndexOf('/');
        int index = Math.max(lastDot, lastSlash);
        return index >= 0 ? className.substring(index + 1) : className;
    }

    /**
     * Returns the class's package.
     *
     * @return the dotted package name, an empty string for the default package, or null when no class is set
     */
    public String getPackageName()
    {
        if (className == null) return null;
        int lastDot = className.lastIndexOf('.');
        int lastSlash = className.lastIndexOf('/');
        int index = Math.max(lastDot, lastSlash);
        return index >= 0 ? className.substring(0, index).replace('/', '.') : "";
    }

    /**
     * Reports whether the location names a method.
     *
     * @return true when a non-empty method name is set
     */
    public boolean isMethodLocation()
    {
        return methodName != null && !methodName.isEmpty();
    }

    /**
     * Reports whether the location names a field.
     *
     * @return true when a non-empty field name is set
     */
    public boolean isFieldLocation()
    {
        return fieldName != null && !fieldName.isEmpty();
    }

    /**
     * Reports whether the location is a whole class.
     *
     * @return true when a class is set and neither a method nor a field
     */
    public boolean isClassLocation()
    {
        return className != null && !isMethodLocation() && !isFieldLocation();
    }

    /**
     * Starts a builder with every part unset and line and instruction index -1.
     *
     * @return a new builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Creates a location for a whole class.
     *
     * @param className the class name, internal or dotted
     * @return the location
     */
    public static Location ofClass(String className)
    {
        return builder().className(className).build();
    }

    /**
     * Creates a location for a method.
     *
     * @param className the class name, internal or dotted
     * @param methodName the method's name
     * @param descriptor the method's JVM descriptor, or null
     * @return the location
     */
    public static Location ofMethod(String className, String methodName, String descriptor)
    {
        return builder()
                .className(className)
                .methodName(methodName)
                .methodDescriptor(descriptor)
                .build();
    }

    /**
     * Creates a location for a field.
     *
     * @param className the class name, internal or dotted
     * @param fieldName the field's name
     * @return the location
     */
    public static Location ofField(String className, String fieldName)
    {
        return builder()
                .className(className)
                .fieldName(fieldName)
                .build();
    }

    /** The builder for Location; every part is optional. */
    public static class Builder
    {
        private String className;
        private String methodName;
        private String methodDescriptor;
        private int lineNumber = -1;
        private int instructionIndex = -1;
        private String fieldName;

        /**
         * Sets the class.
         *
         * @param className the class name, internal or dotted
         * @return this builder
         */
        public Builder className(String className)
        {
            this.className = className;
            return this;
        }

        /**
         * Sets the method.
         *
         * @param methodName the method's name
         * @return this builder
         */
        public Builder methodName(String methodName)
        {
            this.methodName = methodName;
            return this;
        }

        /**
         * Sets the method's descriptor.
         *
         * @param methodDescriptor the JVM descriptor
         * @return this builder
         */
        public Builder methodDescriptor(String methodDescriptor)
        {
            this.methodDescriptor = methodDescriptor;
            return this;
        }

        /**
         * Sets the source line.
         *
         * @param lineNumber the line, or -1 when unknown
         * @return this builder
         */
        public Builder lineNumber(int lineNumber)
        {
            this.lineNumber = lineNumber;
            return this;
        }

        /**
         * Sets the bytecode instruction index.
         *
         * @param instructionIndex the index, or -1 when unknown
         * @return this builder
         */
        public Builder instructionIndex(int instructionIndex)
        {
            this.instructionIndex = instructionIndex;
            return this;
        }

        /**
         * Sets the field.
         *
         * @param fieldName the field's name
         * @return this builder
         */
        public Builder fieldName(String fieldName)
        {
            this.fieldName = fieldName;
            return this;
        }

        /**
         * Builds the location.
         *
         * @return the location
         */
        public Location build()
        {
            return new Location(this);
        }
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        if (className != null)
        {
            sb.append(className.replace('/', '.'));
        }
        if (methodName != null)
        {
            sb.append(".").append(methodName);
            if (methodDescriptor != null)
            {
                sb.append(methodDescriptor);
            }
        }
        else if (fieldName != null)
        {
            sb.append(".").append(fieldName);
        }
        if (lineNumber > 0)
        {
            sb.append(":").append(lineNumber);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return lineNumber == location.lineNumber &&
                instructionIndex == location.instructionIndex &&
                Objects.equals(className, location.className) &&
                Objects.equals(methodName, location.methodName) &&
                Objects.equals(methodDescriptor, location.methodDescriptor) &&
                Objects.equals(fieldName, location.fieldName);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(className, methodName, methodDescriptor, lineNumber, instructionIndex, fieldName);
    }
}
