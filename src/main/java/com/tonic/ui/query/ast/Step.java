package com.tonic.ui.query.ast;

import java.util.Objects;

/**
 * One segment of an {@link Accessor} path: a keyword atom optionally indexed (e.g. {@code value},
 * {@code arg(0)}). Whether a step resolves to a scalar attribute or a sub-subject stream is decided
 * by the attribute registry from {@code (subjectKind, keyword)} — the step itself carries no
 * behavior, which is what keeps the vocabulary open.
 */
public final class Step {

    private final String keyword;
    private final Integer index;   // explicit index, e.g. arg(0); null when absent

    private Step(String keyword, Integer index) {
        this.keyword = Objects.requireNonNull(keyword);
        this.index = index;
    }

    public static Step of(String keyword) {
        return new Step(keyword, null);
    }

    public static Step indexed(String keyword, int index) {
        return new Step(keyword, index);
    }

    public String keyword() {
        return keyword;
    }

    public boolean hasIndex() {
        return index != null;
    }

    public int index() {
        return index;
    }

    @Override
    public String toString() {
        return index != null ? keyword + "(" + index + ")" : keyword;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Step)) return false;
        Step s = (Step) o;
        return keyword.equals(s.keyword) && Objects.equals(index, s.index);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyword, index);
    }
}
