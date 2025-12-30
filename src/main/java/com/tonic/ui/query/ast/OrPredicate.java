package com.tonic.ui.query.ast;

import java.util.Objects;

/**
 * Logical OR of two predicates.
 * Example: throws("NullPointerException") OR throws("IllegalArgumentException")
 */
public final class OrPredicate implements Predicate {

    private final Predicate left;
    private final Predicate right;

    public OrPredicate(Predicate left, Predicate right) {
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
    public <T> T accept(PredicateVisitor<T> visitor) {
        return visitor.visitOr(this);
    }

    @Override
    public boolean isStaticallyResolvable() {
        return left.isStaticallyResolvable() && right.isStaticallyResolvable();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrPredicate)) return false;
        OrPredicate that = (OrPredicate) o;
        return Objects.equals(left, that.left) && Objects.equals(right, that.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right);
    }

    @Override
    public String toString() {
        return "OrPredicate{left=" + left + ", right=" + right + "}";
    }
}
