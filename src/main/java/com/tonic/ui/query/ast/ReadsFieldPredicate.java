package com.tonic.ui.query.ast;

import java.util.Objects;

/**
 * Matches if target reads from a specific field.
 * Example: readsField("Config", "apiKey")
 */
public final class ReadsFieldPredicate implements Predicate {

    private final String ownerClass;
    private final String fieldName;
    private final String descriptor;

    public ReadsFieldPredicate(String ownerClass, String fieldName, String descriptor) {
        this.ownerClass = ownerClass;
        this.fieldName = fieldName;
        this.descriptor = descriptor;
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

    public static ReadsFieldPredicate of(String fieldRef) {
        if (fieldRef == null || fieldRef.equals("*")) {
            return new ReadsFieldPredicate(null, null, null);
        }
        String[] parts = fieldRef.split("[.:]");
        switch (parts.length) {
            case 1: return new ReadsFieldPredicate(null, parts[0], null);
            case 2: return new ReadsFieldPredicate(parts[0], parts[1], null);
            case 3: return new ReadsFieldPredicate(parts[0], parts[1], parts[2]);
            default: return new ReadsFieldPredicate(null, fieldRef, null);
        }
    }

    public static ReadsFieldPredicate all() {
        return new ReadsFieldPredicate(null, null, null);
    }

    public boolean isWildcard() {
        return ownerClass == null && fieldName == null;
    }

    public boolean matches(String owner, String name, String desc) {
        if (ownerClass != null && !ownerClass.equals(owner) && !owner.endsWith("/" + ownerClass)) {
            return false;
        }
        if (fieldName != null && !fieldName.equals(name)) {
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
        if (!(o instanceof ReadsFieldPredicate)) return false;
        ReadsFieldPredicate that = (ReadsFieldPredicate) o;
        return Objects.equals(ownerClass, that.ownerClass) &&
               Objects.equals(fieldName, that.fieldName) &&
               Objects.equals(descriptor, that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerClass, fieldName, descriptor);
    }

    @Override
    public String toString() {
        return "ReadsFieldPredicate{ownerClass='" + ownerClass + "', fieldName='" + fieldName +
               "', descriptor='" + descriptor + "'}";
    }
}
