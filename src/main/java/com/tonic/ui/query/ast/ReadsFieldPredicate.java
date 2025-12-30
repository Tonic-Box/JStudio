package com.tonic.ui.query.ast;

import com.tonic.ui.core.util.MemberReference;

import java.util.Objects;

/**
 * Matches if target reads from a specific field.
 * Example: readsField("Config", "apiKey")
 */
public final class ReadsFieldPredicate implements Predicate {

    private final MemberReference fieldRef;

    public ReadsFieldPredicate(String ownerClass, String fieldName, String descriptor) {
        this.fieldRef = new MemberReference(ownerClass, fieldName, descriptor);
    }

    private ReadsFieldPredicate(MemberReference ref) {
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

    public static ReadsFieldPredicate of(String fieldRefStr) {
        MemberReference ref = MemberReference.parseFieldRef(fieldRefStr);
        return new ReadsFieldPredicate(ref);
    }

    public static ReadsFieldPredicate all() {
        return new ReadsFieldPredicate(null, null, null);
    }

    public boolean isWildcard() {
        return fieldRef.isWildcard();
    }

    public boolean matches(String owner, String name, String desc) {
        return fieldRef.matches(owner, name, desc);
    }

    @Override
    public <T> T accept(PredicateVisitor<T> visitor) {
        return visitor.visitReadsField(this);
    }

    @Override
    public boolean isStaticallyResolvable() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReadsFieldPredicate)) return false;
        ReadsFieldPredicate that = (ReadsFieldPredicate) o;
        return Objects.equals(fieldRef, that.fieldRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldRef);
    }

    @Override
    public String toString() {
        return "ReadsFieldPredicate{fieldRef=" + fieldRef + "}";
    }
}
