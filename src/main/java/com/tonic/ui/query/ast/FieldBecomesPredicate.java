package com.tonic.ui.query.ast;

import java.util.Objects;

/**
 * Matches when a field transitions to a specific state.
 * Example: field("Auth.token") becomes non-null
 */
public final class FieldBecomesPredicate implements Predicate {

    public enum Transition {
        NON_NULL,
        NULL,
        CHANGED
    }

    private final String ownerClass;
    private final String fieldName;
    private final String descriptor;
    private final Transition transition;

    public FieldBecomesPredicate(String ownerClass, String fieldName, String descriptor, Transition transition) {
        this.ownerClass = ownerClass;
        this.fieldName = fieldName;
        this.descriptor = descriptor;
        this.transition = transition;
    }

    public String ownerClass() {
        return ownerClass;
    }

    public String fieldName() {
        return fieldName;
    }

    public String descriptor() {
        return descriptor;
    }

    public Transition transition() {
        return transition;
    }

    public static FieldBecomesPredicate becomesNonNull(String fieldRef) {
        String[] parts = fieldRef.split("[.:]");
        switch (parts.length) {
            case 1: return new FieldBecomesPredicate(null, parts[0], null, Transition.NON_NULL);
            case 2: return new FieldBecomesPredicate(parts[0], parts[1], null, Transition.NON_NULL);
            default: return new FieldBecomesPredicate(null, fieldRef, null, Transition.NON_NULL);
        }
    }

    public static FieldBecomesPredicate becomesNull(String fieldRef) {
        String[] parts = fieldRef.split("[.:]");
        switch (parts.length) {
            case 1: return new FieldBecomesPredicate(null, parts[0], null, Transition.NULL);
            case 2: return new FieldBecomesPredicate(parts[0], parts[1], null, Transition.NULL);
            default: return new FieldBecomesPredicate(null, fieldRef, null, Transition.NULL);
        }
    }

    @Override
    public <T> T accept(PredicateVisitor<T> visitor) {
        return visitor.visitFieldBecomes(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FieldBecomesPredicate)) return false;
        FieldBecomesPredicate that = (FieldBecomesPredicate) o;
        return Objects.equals(ownerClass, that.ownerClass) &&
               Objects.equals(fieldName, that.fieldName) &&
               Objects.equals(descriptor, that.descriptor) &&
               transition == that.transition;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerClass, fieldName, descriptor, transition);
    }

    @Override
    public String toString() {
        return "FieldBecomesPredicate{ownerClass='" + ownerClass + "', fieldName='" + fieldName +
               "', transition=" + transition + "}";
    }
}
