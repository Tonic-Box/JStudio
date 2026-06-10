package com.tonic.ui.query.eval;

/**
 * The type of entity an evaluation is currently bound to. Attribute resolvers are registered per
 * {@code (SubjectKind, keyword)}, so the same keyword (e.g. {@code name}, {@code type}) reuses across
 * kinds with kind-appropriate behavior.
 */
public enum SubjectKind {
    CLASS,
    METHOD,
    INSTRUCTION,
    CALL,
    ARG,
    FIELD_ACCESS
}
