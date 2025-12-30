package com.tonic.ui.query.planner;

import java.util.Objects;

/**
 * Navigation target for clickable result elements.
 */
public interface ClickTarget {

    final class MethodTarget implements ClickTarget {
        private final String className;
        private final String methodName;
        private final String descriptor;

        public MethodTarget(String className, String methodName, String descriptor) {
            this.className = className;
            this.methodName = methodName;
            this.descriptor = descriptor;
        }

        public String className() {
            return className;
        }

        public String methodName() {
            return methodName;
        }

        public String descriptor() {
            return descriptor;
        }

        public String getSignature() {
            return className + "." + methodName + descriptor;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MethodTarget)) return false;
            MethodTarget that = (MethodTarget) o;
            return Objects.equals(className, that.className) &&
                   Objects.equals(methodName, that.methodName) &&
                   Objects.equals(descriptor, that.descriptor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(className, methodName, descriptor);
        }

        @Override
        public String toString() {
            return "MethodTarget{" + getSignature() + "}";
        }
    }

    final class PCTarget implements ClickTarget {
        private final String className;
        private final String methodName;
        private final String descriptor;
        private final int pc;

        public PCTarget(String className, String methodName, String descriptor, int pc) {
            this.className = className;
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.pc = pc;
        }

        public String className() {
            return className;
        }

        public String methodName() {
            return methodName;
        }

        public String descriptor() {
            return descriptor;
        }

        public int pc() {
            return pc;
        }

        public String getSignature() {
            return className + "." + methodName + descriptor + "@" + pc;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PCTarget)) return false;
            PCTarget pcTarget = (PCTarget) o;
            return pc == pcTarget.pc &&
                   Objects.equals(className, pcTarget.className) &&
                   Objects.equals(methodName, pcTarget.methodName) &&
                   Objects.equals(descriptor, pcTarget.descriptor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(className, methodName, descriptor, pc);
        }

        @Override
        public String toString() {
            return "PCTarget{" + getSignature() + "}";
        }
    }

    final class ClassTarget implements ClickTarget {
        private final String className;

        public ClassTarget(String className) {
            this.className = className;
        }

        public String className() {
            return className;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ClassTarget)) return false;
            ClassTarget that = (ClassTarget) o;
            return Objects.equals(className, that.className);
        }

        @Override
        public int hashCode() {
            return Objects.hash(className);
        }

        @Override
        public String toString() {
            return "ClassTarget{" + className + "}";
        }
    }

    final class FieldTarget implements ClickTarget {
        private final String className;
        private final String fieldName;
        private final String descriptor;

        public FieldTarget(String className, String fieldName, String descriptor) {
            this.className = className;
            this.fieldName = fieldName;
            this.descriptor = descriptor;
        }

        public String className() {
            return className;
        }

        public String fieldName() {
            return fieldName;
        }

        public String descriptor() {
            return descriptor;
        }

        public String getSignature() {
            return className + "." + fieldName + ":" + descriptor;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FieldTarget)) return false;
            FieldTarget that = (FieldTarget) o;
            return Objects.equals(className, that.className) &&
                   Objects.equals(fieldName, that.fieldName) &&
                   Objects.equals(descriptor, that.descriptor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(className, fieldName, descriptor);
        }

        @Override
        public String toString() {
            return "FieldTarget{" + getSignature() + "}";
        }
    }
}
