package com.tonic.ui.query.ast;

import java.util.Objects;

/**
 * Scope limited to execution paths between two events.
 * Example: BETWEEN field("Auth.token") becomes non-null AND call("send")
 */
public final class BetweenScope implements Scope {

    private final Predicate startEvent;
    private final Predicate endEvent;

    public BetweenScope(Predicate startEvent, Predicate endEvent) {
        this.startEvent = startEvent;
        this.endEvent = endEvent;
    }

    public Predicate startEvent() {
        return startEvent;
    }

    public Predicate endEvent() {
        return endEvent;
    }

    @Override
    public <T> T accept(ScopeVisitor<T> visitor) {
        return visitor.visitBetween(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BetweenScope)) return false;
        BetweenScope that = (BetweenScope) o;
        return Objects.equals(startEvent, that.startEvent) &&
               Objects.equals(endEvent, that.endEvent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startEvent, endEvent);
    }

    @Override
    public String toString() {
        return "BetweenScope{startEvent=" + startEvent + ", endEvent=" + endEvent + "}";
    }
}
