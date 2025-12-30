package com.tonic.ui.query.ast;

public interface ScopeVisitor<T> {

    T visitAll(AllScope scope);

    T visitClass(ClassScope scope);

    T visitMethod(MethodScope scope);

    T visitDuring(DuringScope scope);

    T visitBetween(BetweenScope scope);
}
