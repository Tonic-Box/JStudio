package com.tonic.ui.query.ast;

import java.util.Objects;

/**
 * Matches if target writes to a specific field.
 * Example: writesField("Auth", "token", "Ljava/lang/String;")
 */
public final class WritesFieldPredicate implements Predicate {

    private final String ownerClass;
    private final String fieldName;
    private final String descriptor;

    public WritesFieldPredicate(String ownerClass, String fieldName, String descriptor) {
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

    public static WritesFieldPredicate of(String fieldRef) {
        String[] parts = fieldRef.split("[.:]");
        switch (parts.length) {
            case 1: return new WritesFieldPredicate(null, parts[0], null);
            case 2: return new WritesFieldPredicate(parts[0], parts[1], null);
            case 3: return new WritesFieldPredicate(parts[0], parts[1], parts[2]);
            default: return new WritesFieldPredicate(null, fieldRef, null);
        }
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
        if (!(o instanceof WritesFieldPredicate)) return false;
        WritesFieldPredicate that = (WritesFieldPredicate) o;
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
        return "WritesFieldPredicate{ownerClass='" + ownerClass + "', fieldName='" + fieldName +
               "', descriptor='" + descriptor + "'}";
    }
}
