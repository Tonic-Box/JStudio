package com.tonic.ui.query.ast;

import java.util.Objects;

/**
 * Logical AND of two predicates.
 * Example: calls("Cipher.init") AND allocCount("String") > 100
 */
public final class AndPredicate implements Predicate {

    private final Predicate left;
    private final Predicate right;

    public AndPredicate(Predicate left, Predicate right) {
        this.left = left;
        this.right = right;
    }

    public Predicate left() {
        return left;
    }

    public Predicate right() {
        return right;
    }

    @Override
    public boolean isStaticallyResolvable() {
        return left.isStaticallyResolvable() && right.isStaticallyResolvable();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AndPredicate)) return false;
        AndPredicate that = (AndPredicate) o;
        return Objects.equals(left, that.left) && Objects.equals(right, that.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right);
    }

    @Override
    public String toString() {
        return "AndPredicate{left=" + left + ", right=" + right + "}";
    }
}
