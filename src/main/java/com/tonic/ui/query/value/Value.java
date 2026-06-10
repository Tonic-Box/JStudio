package com.tonic.ui.query.value;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The universal scalar produced by attribute resolution and carried by query literals.
 *
 * <p>One closed value domain shared by the AST (literal operands) and the evaluator (resolved
 * attribute values), so a single comparator handles every comparison. {@link #ABSENT} is the
 * "statically unknown" value (distinct from {@link #ofNull() null}); comparisons against it are
 * always {@code false}, which keeps evaluation total and lets cheap static resolvers bail safely.
 *
 * <p>Implemented as an interface with nested final variants (Java 11 — no sealed/records); the set is
 * closed and {@link #kind()} drives comparison.
 */
public interface Value {

    ValueKind kind();

    /** The sentinel for "could not be determined statically". */
    Value ABSENT = new Absent();

    static Value of(long v) {
        return new IntValue(v);
    }

    static Value of(double v) {
        return new RealValue(v);
    }

    static Value of(boolean v) {
        return v ? BoolValue.TRUE : BoolValue.FALSE;
    }

    static Value of(String v) {
        return v == null ? ofNull() : new StrValue(v);
    }

    static Value ofType(String internalNameOrDescriptor) {
        return new TypeValue(internalNameOrDescriptor);
    }

    static Value ofRegex(Pattern pattern) {
        return new RegexValue(pattern);
    }

    static Value ofSet(List<Value> members) {
        return new SetValue(members);
    }

    static Value ofNull() {
        return NullValue.INSTANCE;
    }

    final class IntValue implements Value {
        private final long value;
        public IntValue(long value) { this.value = value; }
        public long get() { return value; }
        @Override public ValueKind kind() { return ValueKind.INT; }
        @Override public String toString() { return Long.toString(value); }
        @Override public boolean equals(Object o) { return o instanceof IntValue && ((IntValue) o).value == value; }
        @Override public int hashCode() { return Long.hashCode(value); }
    }

    final class RealValue implements Value {
        private final double value;
        public RealValue(double value) { this.value = value; }
        public double get() { return value; }
        @Override public ValueKind kind() { return ValueKind.REAL; }
        @Override public String toString() { return Double.toString(value); }
        @Override public boolean equals(Object o) { return o instanceof RealValue && Double.compare(((RealValue) o).value, value) == 0; }
        @Override public int hashCode() { return Double.hashCode(value); }
    }

    final class StrValue implements Value {
        private final String value;
        public StrValue(String value) { this.value = Objects.requireNonNull(value); }
        public String get() { return value; }
        @Override public ValueKind kind() { return ValueKind.STRING; }
        @Override public String toString() { return '"' + value + '"'; }
        @Override public boolean equals(Object o) { return o instanceof StrValue && ((StrValue) o).value.equals(value); }
        @Override public int hashCode() { return value.hashCode(); }
    }

    /** A type reference: either an internal name ({@code java/lang/String}) or a descriptor ({@code I}). */
    final class TypeValue implements Value {
        private final String type;
        public TypeValue(String type) { this.type = Objects.requireNonNull(type); }
        public String get() { return type; }
        @Override public ValueKind kind() { return ValueKind.TYPE; }
        @Override public String toString() { return type; }
        @Override public boolean equals(Object o) { return o instanceof TypeValue && ((TypeValue) o).type.equals(type); }
        @Override public int hashCode() { return type.hashCode(); }
    }

    final class BoolValue implements Value {
        public static final BoolValue TRUE = new BoolValue(true);
        public static final BoolValue FALSE = new BoolValue(false);
        private final boolean value;
        private BoolValue(boolean value) { this.value = value; }
        public boolean get() { return value; }
        @Override public ValueKind kind() { return ValueKind.BOOL; }
        @Override public String toString() { return Boolean.toString(value); }
    }

    final class RegexValue implements Value {
        private final Pattern pattern;
        public RegexValue(Pattern pattern) { this.pattern = Objects.requireNonNull(pattern); }
        public Pattern get() { return pattern; }
        @Override public ValueKind kind() { return ValueKind.REGEX; }
        @Override public String toString() { return "/" + pattern.pattern() + "/"; }
    }

    final class SetValue implements Value {
        private final List<Value> members;
        public SetValue(List<Value> members) { this.members = List.copyOf(members); }
        public List<Value> get() { return members; }
        @Override public ValueKind kind() { return ValueKind.SET; }
        @Override public String toString() { return members.toString(); }
    }

    final class NullValue implements Value {
        static final NullValue INSTANCE = new NullValue();
        private NullValue() { }
        @Override public ValueKind kind() { return ValueKind.NULL; }
        @Override public String toString() { return "null"; }
    }

    final class Absent implements Value {
        private Absent() { }
        @Override public ValueKind kind() { return ValueKind.ABSENT; }
        @Override public String toString() { return "<absent>"; }
    }
}
