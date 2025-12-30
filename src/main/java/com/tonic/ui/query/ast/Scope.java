package com.tonic.ui.query.ast;

/**
 * Query scope - limits which code locations to analyze.
 */
public interface Scope {

    <T> T accept(ScopeVisitor<T> visitor);

    default boolean isAll() {
        return false;
    }
}
