package com.tonic.ui.query.ast;

import com.tonic.ui.core.util.MemberReference;

import java.util.Objects;

/**
 * Matches if target writes to a specific field.
 * Example: writesField("Auth", "token", "Ljava/lang/String;")
 */
public final class WritesFieldPredicate implements Predicate {

    private final MemberReference fieldRef;

    public WritesFieldPredicate(String ownerClass, String fieldName, String descriptor) {
        this.fieldRef = new MemberReference(ownerClass, fieldName, descriptor);
    }

    private WritesFieldPredicate(MemberReference ref) {
        this.fieldRef = ref;
    }

    public String ownerClass() {
        return fieldRef.getOwnerClass();
    }

    public String fieldName() {
        return fieldRef.getMemberName();
    }

    public String descriptor() {
        return fieldRef.getDescriptor();
    }

    public static WritesFieldPredicate of(String fieldRefStr) {
        MemberReference ref = MemberReference.parseFieldRef(fieldRefStr);
        return new WritesFieldPredicate(ref);
    }

    public boolean matches(String owner, String name, String desc) {
        return fieldRef.matches(owner, name, desc);
    }

    @Override
    public <T> T accept(PredicateVisitor<T> visitor) {
        return visitor.visitWritesField(this);
    }

    @Override
    public boolean isStaticallyResolvable() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WritesFieldPredicate)) return false;
        WritesFieldPredicate that = (WritesFieldPredicate) o;
        return Objects.equals(fieldRef, that.fieldRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldRef);
    }

    @Override
    public String toString() {
        return "WritesFieldPredicate{fieldRef=" + fieldRef + "}";
    }
}
