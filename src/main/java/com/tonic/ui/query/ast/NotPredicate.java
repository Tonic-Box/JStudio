package com.tonic.ui.query.ast;

import java.util.Objects;

/**
 * Logical NOT of a predicate.
 * Example: NOT calls("System.exit")
 */
public final class NotPredicate implements Predicate {

    private final Predicate inner;

    public NotPredicate(Predicate inner) {
        this.inner = inner;
    }

    public Predicate inner() {
        return inner;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NotPredicate)) return false;
        NotPredicate that = (NotPredicate) o;
        return Objects.equals(inner, that.inner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inner);
    }

    @Override
    public String toString() {
        return "NotPredicate{inner=" + inner + "}";
    }
}
