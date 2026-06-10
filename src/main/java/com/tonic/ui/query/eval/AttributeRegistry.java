package com.tonic.ui.query.eval;

import com.tonic.ui.query.ast.Step;
import com.tonic.ui.query.value.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Maps {@code (SubjectKind, keyword)} to a resolver — either a scalar {@link Attribute} or a
 * sub-subject {@link Selector}. This is the one place the query vocabulary grows: a new queryable
 * fact is a single {@code registerScalar}/{@code registerStream} call (see {@link DefaultAttributes}),
 * never a new AST node or visitor method.
 */
public final class AttributeRegistry {

    /** Resolves a scalar value from a subject. */
    @FunctionalInterface
    public interface Attribute {
        Value get(Subject subject);
    }

    /** Expands a subject into a stream of sub-subjects (honoring the step's index/wildcard). */
    @FunctionalInterface
    public interface Selector {
        Stream<Subject> expand(Subject subject, Step step);
    }

    /** A registered resolver: exactly one of {@code scalar}/{@code stream} is non-null. */
    public static final class Entry {
        private final Attribute scalar;
        private final Selector stream;

        private Entry(Attribute scalar, Selector stream) {
            this.scalar = scalar;
            this.stream = stream;
        }

        public boolean isStream() { return stream != null; }
        public Attribute scalar() { return scalar; }
        public Selector stream() { return stream; }
    }

    private final Map<String, Entry> entries = new HashMap<>();

    public void registerScalar(SubjectKind kind, String keyword, Attribute attribute) {
        put(kind, keyword, new Entry(attribute, null));
    }

    public void registerStream(SubjectKind kind, String keyword, Selector selector) {
        put(kind, keyword, new Entry(null, selector));
    }

    public Entry lookup(SubjectKind kind, String keyword) {
        return entries.get(key(kind, keyword));
    }

    private void put(SubjectKind kind, String keyword, Entry entry) {
        if (entries.putIfAbsent(key(kind, keyword), entry) != null) {
            throw new IllegalStateException("Duplicate attribute: " + kind + "#" + keyword);
        }
    }

    private static String key(SubjectKind kind, String keyword) {
        return kind.name() + '#' + keyword.toLowerCase();
    }
}
