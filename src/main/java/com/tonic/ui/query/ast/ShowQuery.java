package com.tonic.ui.query.ast;

import java.util.Objects;

/**
 * SHOW query - displays all matching items without filtering.
 * Example: SHOW all strings DURING clinit OF classes matching /Config/
 */
public final class ShowQuery implements Query {

    private final Target target;
    private final Scope scope;
    private final Predicate predicate;
    private final RunSpec runSpec;
    private final Integer limit;
    private final OrderBy orderBy;

    public ShowQuery(Target target, Scope scope, Predicate predicate,
                     RunSpec runSpec, Integer limit, OrderBy orderBy) {
        this.target = target;
        this.scope = scope;
        this.predicate = predicate;
        this.runSpec = runSpec;
        this.limit = limit;
        this.orderBy = orderBy;
    }

    public ShowQuery(Target target) {
        this(target, AllScope.INSTANCE, null, null, null, null);
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
    public Predicate predicate() {
        return predicate;
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

    public ShowQuery withScope(Scope scope) {
        return new ShowQuery(target, scope, predicate, runSpec, limit, orderBy);
    }

    public ShowQuery withPredicate(Predicate predicate) {
        return new ShowQuery(target, scope, predicate, runSpec, limit, orderBy);
    }

    public ShowQuery withRunSpec(RunSpec runSpec) {
        return new ShowQuery(target, scope, predicate, runSpec, limit, orderBy);
    }

    public ShowQuery withLimit(Integer limit) {
        return new ShowQuery(target, scope, predicate, runSpec, limit, orderBy);
    }

    public ShowQuery withOrderBy(OrderBy orderBy) {
        return new ShowQuery(target, scope, predicate, runSpec, limit, orderBy);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShowQuery)) return false;
        ShowQuery that = (ShowQuery) o;
        return Objects.equals(target, that.target) &&
               Objects.equals(scope, that.scope) &&
               Objects.equals(predicate, that.predicate) &&
               Objects.equals(runSpec, that.runSpec) &&
               Objects.equals(limit, that.limit) &&
               Objects.equals(orderBy, that.orderBy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, scope, predicate, runSpec, limit, orderBy);
    }

    @Override
    public String toString() {
        return "ShowQuery{target=" + target + ", scope=" + scope +
               ", predicate=" + predicate + ", limit=" + limit + "}";
    }
}
