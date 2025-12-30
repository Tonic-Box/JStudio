package com.tonic.ui.query.ast;

import com.tonic.ui.core.util.MemberReference;

import java.util.Objects;

/**
 * Matches if the target calls a specific method.
 * Example: calls("MessageDigest.digest")
 * Example with argument filter: calls("PrintStream.println", dynamic)
 */
public final class CallsPredicate implements Predicate {

    private final MemberReference methodRef;
    private final ArgumentType argumentType;

    public CallsPredicate(String ownerClass, String methodName, String descriptor) {
        this(ownerClass, methodName, descriptor, ArgumentType.ANY);
    }

    public CallsPredicate(String ownerClass, String methodName, String descriptor, ArgumentType argumentType) {
        this.methodRef = new MemberReference(ownerClass, methodName, descriptor);
        this.argumentType = argumentType != null ? argumentType : ArgumentType.ANY;
    }

    private CallsPredicate(MemberReference ref, ArgumentType argumentType) {
        this.methodRef = ref;
        this.argumentType = argumentType != null ? argumentType : ArgumentType.ANY;
    }

    public String ownerClass() {
        return methodRef.getOwnerClass();
    }

    public String methodName() {
        return methodRef.getMemberName();
    }

    public String descriptor() {
        return methodRef.getDescriptor();
    }

    public ArgumentType argumentType() {
        return argumentType;
    }

    public boolean hasArgumentFilter() {
        return argumentType != ArgumentType.ANY;
    }

    public static CallsPredicate of(String methodRefStr) {
        return of(methodRefStr, ArgumentType.ANY);
    }

    public static CallsPredicate of(String methodRefStr, ArgumentType argType) {
        MemberReference ref = MemberReference.parseMethodRef(methodRefStr);
        return new CallsPredicate(ref, argType);
    }

    public boolean matches(String owner, String name, String desc) {
        return methodRef.matches(owner, name, desc);
    }

    @Override
    public <T> T accept(PredicateVisitor<T> visitor) {
        return visitor.visitCalls(this);
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
        return Objects.equals(methodRef, that.methodRef) &&
               argumentType == that.argumentType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodRef, argumentType);
    }

    @Override
    public String toString() {
        return "CallsPredicate{methodRef=" + methodRef + ", argType=" + argumentType + "}";
    }
}
