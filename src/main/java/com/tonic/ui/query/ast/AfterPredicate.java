package com.tonic.ui.query.ast;

import java.util.Objects;

/**
 * Temporal predicate - matches if event A occurs after event B.
 * Example: after(field("Auth.token") becomes non-null)
 */
public final class AfterPredicate implements Predicate {

    private final Predicate event;

    public AfterPredicate(Predicate event) {
        this.event = event;
    }

    public Predicate event() {
        return event;
    }

    public static AfterPredicate after(Predicate event) {
        return new AfterPredicate(event);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AfterPredicate)) return false;
        AfterPredicate that = (AfterPredicate) o;
        return Objects.equals(event, that.event);
    }

    @Override
    public int hashCode() {
        return Objects.hash(event);
    }

    @Override
    public String toString() {
        return "AfterPredicate{event=" + event + "}";
    }
}
