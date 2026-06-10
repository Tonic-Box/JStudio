package com.tonic.ui.query.ast;

import java.util.List;
import java.util.stream.Collectors;

/**
 * An immutable, fluent path of {@link Step}s that projects a value (or a sub-subject stream) from a
 * subject — e.g. {@code arg(0).value}, {@code call.descriptor}, {@code method.modifiers}. Pure data:
 * the evaluator interprets it against the attribute registry.
 */
public final class Accessor {

    private final List<Step> steps;

    public Accessor(List<Step> steps) {
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("accessor must have at least one step");
        }
        this.steps = List.copyOf(steps);
    }

    public List<Step> steps() {
        return steps;
    }

    public Step first() {
        return steps.get(0);
    }


    @Override
    public String toString() {
        return steps.stream().map(Step::toString).collect(Collectors.joining("."));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Accessor && ((Accessor) o).steps.equals(steps);
    }

    @Override
    public int hashCode() {
        return steps.hashCode();
    }
}
