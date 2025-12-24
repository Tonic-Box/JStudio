package com.tonic.ui.vm.model;

import java.util.Arrays;

public class MethodCall {

    private final String ownerClass;
    private final String methodName;
    private final String descriptor;
    private final Object[] arguments;
    private final boolean isStatic;
    private final int depth;
    private Object returnValue;
    private long startTimeNanos;
    private long endTimeNanos;
    private boolean exceptional;

    public MethodCall(String ownerClass, String methodName, String descriptor,
                      Object[] arguments, boolean isStatic, int depth) {
        this.ownerClass = ownerClass;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.arguments = arguments != null ? arguments.clone() : new Object[0];
        this.isStatic = isStatic;
        this.depth = depth;
        this.startTimeNanos = System.nanoTime();
    }

    public String getOwnerClass() {
        return ownerClass;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public Object[] getArguments() {
        return arguments.clone();
    }

    public boolean isStatic() {
        return isStatic;
    }

    public int getDepth() {
        return depth;
    }

    public Object getReturnValue() {
        return returnValue;
    }

    public void setReturnValue(Object returnValue) {
        this.returnValue = returnValue;
    }

    public long getStartTimeNanos() {
        return startTimeNanos;
    }

    public void setStartTimeNanos(long startTimeNanos) {
        this.startTimeNanos = startTimeNanos;
    }

    public long getEndTimeNanos() {
        return endTimeNanos;
    }

    public void setEndTimeNanos(long endTimeNanos) {
        this.endTimeNanos = endTimeNanos;
    }

    public long getDurationNanos() {
        return endTimeNanos - startTimeNanos;
    }

    public boolean isExceptional() {
        return exceptional;
    }

    public void setExceptional(boolean exceptional) {
        this.exceptional = exceptional;
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
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        sb.append(getShortSignature());
        return sb.toString();
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
        private boolean isStatic;
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

        public Builder isStatic(boolean isStatic) {
            this.isStatic = isStatic;
            return this;
        }

        public Builder depth(int depth) {
            this.depth = depth;
            return this;
        }

        public MethodCall build() {
            return new MethodCall(ownerClass, methodName, descriptor, arguments, isStatic, depth);
        }
    }
}
