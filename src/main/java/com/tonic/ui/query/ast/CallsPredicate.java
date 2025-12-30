package com.tonic.ui.query.ast;

import java.util.Objects;

/**
 * Matches if the target calls a specific method.
 * Example: calls("MessageDigest.digest")
 * Example with argument filter: calls("PrintStream.println", dynamic)
 */
public final class CallsPredicate implements Predicate {

    private final String ownerClass;
    private final String methodName;
    private final String descriptor;
    private final ArgumentType argumentType;

    public CallsPredicate(String ownerClass, String methodName, String descriptor) {
        this(ownerClass, methodName, descriptor, ArgumentType.ANY);
    }

    public CallsPredicate(String ownerClass, String methodName, String descriptor, ArgumentType argumentType) {
        this.ownerClass = ownerClass;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.argumentType = argumentType != null ? argumentType : ArgumentType.ANY;
    }

    public String ownerClass() {
        return ownerClass;
    }

    public String methodName() {
        return methodName;
    }

    public String descriptor() {
        return descriptor;
    }

    public ArgumentType argumentType() {
        return argumentType;
    }

    public boolean hasArgumentFilter() {
        return argumentType != ArgumentType.ANY;
    }

    public static CallsPredicate of(String methodRef) {
        return of(methodRef, ArgumentType.ANY);
    }

    public static CallsPredicate of(String methodRef, ArgumentType argType) {
        int dotIdx = methodRef.lastIndexOf('.');
        if (dotIdx < 0) {
            return new CallsPredicate(null, methodRef, null, argType);
        }
        String owner = methodRef.substring(0, dotIdx);
        String rest = methodRef.substring(dotIdx + 1);
        int descIdx = rest.indexOf('(');
        if (descIdx < 0) {
            return new CallsPredicate(owner, rest, null, argType);
        }
        return new CallsPredicate(owner, rest.substring(0, descIdx), rest.substring(descIdx), argType);
    }

    public boolean matches(String owner, String name, String desc) {
        if (ownerClass != null && !ownerClass.equals(owner) && !owner.endsWith("/" + ownerClass)) {
            return false;
        }
        if (methodName != null && !methodName.equals(name)) {
            return false;
        }
        if (descriptor != null && !descriptor.equals(desc)) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isStaticallyResolvable() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CallsPredicate)) return false;
        CallsPredicate that = (CallsPredicate) o;
        return Objects.equals(ownerClass, that.ownerClass) &&
               Objects.equals(methodName, that.methodName) &&
               Objects.equals(descriptor, that.descriptor) &&
               argumentType == that.argumentType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerClass, methodName, descriptor, argumentType);
    }

    @Override
    public String toString() {
        return "CallsPredicate{ownerClass='" + ownerClass + "', methodName='" + methodName +
               "', descriptor='" + descriptor + "', argType=" + argumentType + "}";
    }
}
