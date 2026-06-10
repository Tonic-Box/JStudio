package com.tonic.ui.query.ast;

import java.util.Objects;

/**
 * SHOW query - displays all matching items without filtering.
 * Example: SHOW all strings DURING clinit OF classes matching /Config/
 */
public final class ShowQuery implements Query {

    private final Target target;
    private final Scope scope;
    private final Condition condition;
    private final RunSpec runSpec;
    private final Integer limit;
    private final OrderBy orderBy;

    public ShowQuery(Target target, Scope scope, Condition condition,
                     RunSpec runSpec, Integer limit, OrderBy orderBy) {
        this.target = target;
        this.scope = scope;
        this.condition = condition;
        this.runSpec = runSpec;
        this.limit = limit;
        this.orderBy = orderBy;
    }

    @Override
    public Target target() {
        return target;
    }

    @Override
    public Scope scope() {
        return scope;
    }

    @Override
    public Condition condition() {
        return condition;
    }

    @Override
    public RunSpec runSpec() {
        return runSpec;
    }

    @Override
    public Integer limit() {
        return limit;
    }

    @Override
    public OrderBy orderBy() {
        return orderBy;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShowQuery)) return false;
        ShowQuery that = (ShowQuery) o;
        return Objects.equals(target, that.target) &&
               Objects.equals(scope, that.scope) &&
               Objects.equals(condition, that.condition) &&
               Objects.equals(runSpec, that.runSpec) &&
               Objects.equals(limit, that.limit) &&
               Objects.equals(orderBy, that.orderBy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, scope, condition, runSpec, limit, orderBy);
    }

    @Override
    public String toString() {
        return "ShowQuery{target=" + target + ", scope=" + scope +
               ", condition=" + condition + ", limit=" + limit + "}";
    }
}
