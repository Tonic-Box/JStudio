package com.tonic.ui.vm.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;

@Getter
public class MethodCall {

    private final String ownerClass;
    private final String methodName;
    private final String descriptor;
    private final Object[] arguments;
    private final boolean staticMethod;
    private final int depth;
    @Setter private Object returnValue;
    @Setter private long startTimeNanos;
    @Setter private long endTimeNanos;
    @Setter private boolean exceptional;

    public MethodCall(String ownerClass, String methodName, String descriptor,
                      Object[] arguments, boolean isStatic, int depth) {
        this.ownerClass = ownerClass;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.arguments = arguments != null ? arguments.clone() : new Object[0];
        this.staticMethod = isStatic;
        this.depth = depth;
        this.startTimeNanos = System.nanoTime();
    }

    public Object[] getArguments() {
        return arguments.clone();
    }

    public long getDurationNanos() {
        return endTimeNanos - startTimeNanos;
    }

    public String getSimpleOwnerName() {
        int lastSlash = ownerClass.lastIndexOf('/');
        return lastSlash >= 0 ? ownerClass.substring(lastSlash + 1) : ownerClass;
    }

    public String getSignature() {
        return ownerClass.replace('/', '.') + "." + methodName + descriptor;
    }

    public String getShortSignature() {
        return getSimpleOwnerName() + "." + methodName + "()";
    }

    public String getIndentedString() {
        return "  ".repeat(Math.max(0, depth)) +
                getShortSignature();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(ownerClass.replace('/', '.'));
        sb.append('.').append(methodName);
        sb.append('(');
        for (int i = 0; i < arguments.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(formatArgument(arguments[i]));
        }
        sb.append(')');
        return sb.toString();
    }

    private String formatArgument(Object arg) {
        if (arg == null) {
            return "null";
        }
        if (arg instanceof String) {
            String s = (String) arg;
            if (s.length() > 50) {
                return "\"" + s.substring(0, 47) + "...\"";
            }
            return "\"" + s + "\"";
        }
        if (arg instanceof Character) {
            return "'" + arg + "'";
        }
        if (arg.getClass().isArray()) {
            return arg.getClass().getSimpleName() + Arrays.toString((Object[]) arg);
        }
        return String.valueOf(arg);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String ownerClass;
        private String methodName;
        private String descriptor;
        private Object[] arguments;
        private boolean staticMethod;
        private int depth;

        public Builder ownerClass(String ownerClass) {
            this.ownerClass = ownerClass;
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

        public Builder arguments(Object[] arguments) {
            this.arguments = arguments;
            return this;
        }

        public Builder staticMethod(boolean staticMethod) {
            this.staticMethod = staticMethod;
            return this;
        }

        public Builder depth(int depth) {
            this.depth = depth;
            return this;
        }

        public MethodCall build() {
            return new MethodCall(ownerClass, methodName, descriptor, arguments, staticMethod, depth);
        }
    }
}
