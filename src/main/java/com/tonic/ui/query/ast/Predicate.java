package com.tonic.ui.query.ast;

/**
 * Query predicate - conditions that filter results.
 * Predicates can be evaluated statically, dynamically, or post-run.
 */
public interface Predicate {

    default Predicate and(Predicate other) {
        return new AndPredicate(this, other);
    }

    default Predicate or(Predicate other) {
        return new OrPredicate(this, other);
    }

    default Predicate negate() {
        return new NotPredicate(this);
    }

    default boolean isStaticallyResolvable() {
        return false;
    }
}
