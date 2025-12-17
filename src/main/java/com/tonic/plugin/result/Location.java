package com.tonic.plugin.result;

import java.util.Objects;

public class Location {

    private final String className;
    private final String methodName;
    private final String methodDescriptor;
    private final int lineNumber;
    private final int instructionIndex;
    private final String fieldName;

    private Location(Builder builder) {
        this.className = builder.className;
        this.methodName = builder.methodName;
        this.methodDescriptor = builder.methodDescriptor;
        this.lineNumber = builder.lineNumber;
        this.instructionIndex = builder.instructionIndex;
        this.fieldName = builder.fieldName;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getMethodDescriptor() {
        return methodDescriptor;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public int getInstructionIndex() {
        return instructionIndex;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getSimpleClassName() {
        if (className == null) return null;
        int lastDot = className.lastIndexOf('.');
        int lastSlash = className.lastIndexOf('/');
        int index = Math.max(lastDot, lastSlash);
        return index >= 0 ? className.substring(index + 1) : className;
    }

    public String getPackageName() {
        if (className == null) return null;
        int lastDot = className.lastIndexOf('.');
        int lastSlash = className.lastIndexOf('/');
        int index = Math.max(lastDot, lastSlash);
        return index >= 0 ? className.substring(0, index).replace('/', '.') : "";
    }

    public boolean isMethodLocation() {
        return methodName != null && !methodName.isEmpty();
    }

    public boolean isFieldLocation() {
        return fieldName != null && !fieldName.isEmpty();
    }

    public boolean isClassLocation() {
        return className != null && !isMethodLocation() && !isFieldLocation();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Location ofClass(String className) {
        return builder().className(className).build();
    }

    public static Location ofMethod(String className, String methodName, String descriptor) {
        return builder()
            .className(className)
            .methodName(methodName)
            .methodDescriptor(descriptor)
            .build();
    }

    public static Location ofField(String className, String fieldName) {
        return builder()
            .className(className)
            .fieldName(fieldName)
            .build();
    }

    public static class Builder {
        private String className;
        private String methodName;
        private String methodDescriptor;
        private int lineNumber = -1;
        private int instructionIndex = -1;
        private String fieldName;

        public Builder className(String className) {
            this.className = className;
            return this;
        }

        public Builder methodName(String methodName) {
            this.methodName = methodName;
            return this;
        }

        public Builder methodDescriptor(String methodDescriptor) {
            this.methodDescriptor = methodDescriptor;
            return this;
        }

        public Builder lineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }

        public Builder instructionIndex(int instructionIndex) {
            this.instructionIndex = instructionIndex;
            return this;
        }

        public Builder fieldName(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public Location build() {
            return new Location(this);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (className != null) {
            sb.append(className.replace('/', '.'));
        }
        if (methodName != null) {
            sb.append(".").append(methodName);
            if (methodDescriptor != null) {
                sb.append(methodDescriptor);
            }
        } else if (fieldName != null) {
            sb.append(".").append(fieldName);
        }
        if (lineNumber > 0) {
            sb.append(":").append(lineNumber);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
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
    public int hashCode() {
        return Objects.hash(className, methodName, methodDescriptor, lineNumber, instructionIndex, fieldName);
    }
}
