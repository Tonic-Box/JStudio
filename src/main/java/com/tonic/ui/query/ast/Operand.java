package com.tonic.ui.query.ast;

import com.tonic.ui.query.value.Value;

/**
 * The right-hand side of a {@link Condition.Comparison}: either a literal {@link Value} or another
 * {@link Accessor} (so {@code arg(0).value == arg(1).value} works). Closed set, two nested variants.
 */
public interface Operand {

    static Operand literal(Value value) {
        return new Literal(value);
    }

    static Operand accessor(Accessor accessor) {
        return new Ref(accessor);
    }

    final class Literal implements Operand {
        private final Value value;
        public Literal(Value value) { this.value = value; }
        public Value value() { return value; }
        @Override public String toString() { return value.toString(); }
    }

    final class Ref implements Operand {
        private final Accessor accessor;
        public Ref(Accessor accessor) { this.accessor = accessor; }
        public Accessor accessor() { return accessor; }
        @Override public String toString() { return accessor.toString(); }
    }
}
