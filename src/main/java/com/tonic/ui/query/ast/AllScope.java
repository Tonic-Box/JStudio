package com.tonic.ui.query.ast;

/**
 * Search all methods/classes in the project.
 */
public final class AllScope implements Scope {

    public static final AllScope INSTANCE = new AllScope();

    private AllScope() {
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AllScope;
    }

    @Override
    public int hashCode() {
        return AllScope.class.hashCode();
    }

    @Override
    public String toString() {
        return "AllScope{}";
    }
}
