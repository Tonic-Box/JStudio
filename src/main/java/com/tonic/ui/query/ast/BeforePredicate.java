package com.tonic.ui.query.ast;

import java.util.Objects;

/**
 * Temporal predicate - matches if event A occurs before event B.
 * Example: before(calls("send"))
 */
public final class BeforePredicate implements Predicate {

    private final Predicate event;

    public BeforePredicate(Predicate event) {
        this.event = event;
    }

    public Predicate event() {
        return event;
    }

    public static BeforePredicate before(Predicate event) {
        return new BeforePredicate(event);
    }

    @Override
    public <T> T accept(PredicateVisitor<T> visitor) {
        return visitor.visitBefore(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BeforePredicate)) return false;
        BeforePredicate that = (BeforePredicate) o;
        return Objects.equals(event, that.event);
    }

    @Override
    public int hashCode() {
        return Objects.hash(event);
    }

    @Override
    public String toString() {
        return "BeforePredicate{event=" + event + "}";
    }
}
