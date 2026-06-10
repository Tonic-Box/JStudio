package com.tonic.ui.query.ast;

/**
 * Root query AST node. A query is either FIND or SHOW.
 */
public interface Query {
    Target target();
    Scope scope();
    Condition condition();
    RunSpec runSpec();
    Integer limit();
    OrderBy orderBy();
}
