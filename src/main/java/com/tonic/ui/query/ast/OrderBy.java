package com.tonic.ui.query.ast;

import java.util.Objects;

/**
 * ORDER BY clause for query results.
 */
public final class OrderBy {

    private final String key;
    private final boolean ascending;

    public OrderBy(String key, boolean ascending) {
        this.key = key;
        this.ascending = ascending;
    }

    public String key() {
        return key;
    }

    public boolean ascending() {
        return ascending;
    }

    public static OrderBy asc(String key) {
        return new OrderBy(key, true);
    }

    public static OrderBy desc(String key) {
        return new OrderBy(key, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderBy)) return false;
        OrderBy orderBy = (OrderBy) o;
        return ascending == orderBy.ascending && Objects.equals(key, orderBy.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, ascending);
    }

    @Override
    public String toString() {
        return "OrderBy{key='" + key + "', ascending=" + ascending + "}";
    }
}
