package com.tonic.ui.query.ast;

import java.util.Objects;

/**
 * FIND query - returns matching entities with evidence.
 * Example: FIND methods WHERE calls("Cipher.init")
 */
public final class FindQuery implements Query {

    private final Target target;
    private final Scope scope;
    private final Predicate predicate;
    private final RunSpec runSpec;
    private final Integer limit;
    private final OrderBy orderBy;

    public FindQuery(Target target, Scope scope, Predicate predicate,
                     RunSpec runSpec, Integer limit, OrderBy orderBy) {
        this.target = target;
        this.scope = scope;
        this.predicate = predicate;
        this.runSpec = runSpec;
        this.limit = limit;
        this.orderBy = orderBy;
    }

    public FindQuery(Target target) {
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

    public FindQuery withScope(Scope scope) {
        return new FindQuery(target, scope, predicate, runSpec, limit, orderBy);
    }

    public FindQuery withPredicate(Predicate predicate) {
        return new FindQuery(target, scope, predicate, runSpec, limit, orderBy);
    }

    public FindQuery withRunSpec(RunSpec runSpec) {
        return new FindQuery(target, scope, predicate, runSpec, limit, orderBy);
    }

    public FindQuery withLimit(Integer limit) {
        return new FindQuery(target, scope, predicate, runSpec, limit, orderBy);
    }

    public FindQuery withOrderBy(OrderBy orderBy) {
        return new FindQuery(target, scope, predicate, runSpec, limit, orderBy);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FindQuery)) return false;
        FindQuery that = (FindQuery) o;
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
        return "FindQuery{target=" + target + ", scope=" + scope +
               ", predicate=" + predicate + ", limit=" + limit + "}";
    }
}
