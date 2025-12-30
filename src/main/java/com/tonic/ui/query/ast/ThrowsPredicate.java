package com.tonic.ui.query.ast;

import java.util.Objects;

/**
 * Matches if execution throws a specific exception type.
 * Example: throws("java/lang/NullPointerException")
 */
public final class ThrowsPredicate implements Predicate {

    private final String exceptionType;
    private final boolean includeSubtypes;

    public ThrowsPredicate(String exceptionType, boolean includeSubtypes) {
        this.exceptionType = exceptionType;
        this.includeSubtypes = includeSubtypes;
    }

    public String exceptionType() {
        return exceptionType;
    }

    public boolean includeSubtypes() {
        return includeSubtypes;
    }

    public static ThrowsPredicate of(String type) {
        return new ThrowsPredicate(type, true);
    }

    public static ThrowsPredicate exact(String type) {
        return new ThrowsPredicate(type, false);
    }

    public boolean matches(String thrownType) {
        if (exceptionType.equals(thrownType)) {
            return true;
        }
        return includeSubtypes && isSubtype(thrownType, exceptionType);
    }

    private boolean isSubtype(String subtype, String supertype) {
        return subtype.contains(supertype) || supertype.endsWith("Exception") || supertype.endsWith("Error");
    }

    @Override
    public <T> T accept(PredicateVisitor<T> visitor) {
        return visitor.visitThrows(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ThrowsPredicate)) return false;
        ThrowsPredicate that = (ThrowsPredicate) o;
        return includeSubtypes == that.includeSubtypes &&
               Objects.equals(exceptionType, that.exceptionType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exceptionType, includeSubtypes);
    }

    @Override
    public String toString() {
        return "ThrowsPredicate{exceptionType='" + exceptionType + "', includeSubtypes=" + includeSubtypes + "}";
    }
}
