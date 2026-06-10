package com.tonic.ui.query.ast;

import com.tonic.ui.query.value.Operator;

import java.util.List;

/**
 * The boolean expression tree of a query's {@code WHERE} clause — the only place booleans live.
 * A closed composite ({@link And}, {@link Or}, {@link Not}, {@link Group}, {@link Comparison},
 * {@link Quantifier}, {@link True}) visited via {@link Visitor} for evaluation/planning. Adding a new
 * queryable fact adds a registry entry, never a node here.
 */
public interface Condition {

    <R> R accept(Visitor<R> visitor);

    interface Visitor<R> {
        R visitAnd(And c);
        R visitOr(Or c);
        R visitNot(Not c);
        R visitGroup(Group c);
        R visitComparison(Comparison c);
        R visitQuantifier(Quantifier c);
        R visitCount(Count c);
        R visitSequence(Sequence c);
        R visitTrue(True c);
    }

    /** Existence/universality of a sub-condition over a sub-subject stream. {@code ANY == has}. */
    enum Quant { ANY, ALL, NONE }

    final class And implements Condition {
        private final List<Condition> terms;
        public And(List<Condition> terms) { this.terms = List.copyOf(terms); }
        public List<Condition> terms() { return terms; }
        @Override public <R> R accept(Visitor<R> v) { return v.visitAnd(this); }
        @Override public String toString() { return "(" + join(terms, " and ") + ")"; }
    }

    final class Or implements Condition {
        private final List<Condition> terms;
        public Or(List<Condition> terms) { this.terms = List.copyOf(terms); }
        public List<Condition> terms() { return terms; }
        @Override public <R> R accept(Visitor<R> v) { return v.visitOr(this); }
        @Override public String toString() { return "(" + join(terms, " or ") + ")"; }
    }

    final class Not implements Condition {
        private final Condition inner;
        public Not(Condition inner) { this.inner = inner; }
        public Condition inner() { return inner; }
        @Override public <R> R accept(Visitor<R> v) { return v.visitNot(this); }
        @Override public String toString() { return "not " + inner; }
    }

    final class Group implements Condition {
        private final Condition inner;
        public Group(Condition inner) { this.inner = inner; }
        public Condition inner() { return inner; }
        @Override public <R> R accept(Visitor<R> v) { return v.visitGroup(this); }
        @Override public String toString() { return "(" + inner + ")"; }
    }

    /** {@code accessor OP operand} — the single generic leaf. A bare accessor uses op == null. */
    final class Comparison implements Condition {
        private final Accessor accessor;
        private final Operator op;     // null => boolean coercion of the accessor (truthy)
        private final Operand operand; // null when op is null
        public Comparison(Accessor accessor, Operator op, Operand operand) {
            this.accessor = accessor;
            this.op = op;
            this.operand = operand;
        }
        public Accessor accessor() { return accessor; }
        public Operator op() { return op; }
        public Operand operand() { return operand; }
        public boolean isBoolean() { return op == null; }
        @Override public <R> R accept(Visitor<R> v) { return v.visitComparison(this); }
        @Override public String toString() { return op == null ? accessor.toString() : accessor + " " + op + " " + operand; }
    }

    /** {@code has|any|all|none <stream> where (body)}. {@code count(...) OP n} desugars to a Comparison. */
    final class Quantifier implements Condition {
        private final Quant quant;
        private final Accessor stream;
        private final Condition body;   // null => no `where`, i.e. plain existence
        public Quantifier(Quant quant, Accessor stream, Condition body) {
            this.quant = quant;
            this.stream = stream;
            this.body = body;
        }
        public Quant quant() { return quant; }
        public Accessor stream() { return stream; }
        public Condition body() { return body; }
        @Override public <R> R accept(Visitor<R> v) { return v.visitQuantifier(this); }
        @Override public String toString() {
            return quant.name().toLowerCase() + " " + stream + (body == null ? "" : " where (" + body + ")");
        }
    }

    /** {@code count(<stream> [where (body)]) OP n} — cardinality of a (optionally filtered) stream. */
    final class Count implements Condition {
        private final Accessor stream;
        private final Condition body;   // null => count all
        private final Operator op;
        private final Operand operand;
        public Count(Accessor stream, Condition body, Operator op, Operand operand) {
            this.stream = stream;
            this.body = body;
            this.op = op;
            this.operand = operand;
        }
        public Accessor stream() { return stream; }
        public Condition body() { return body; }
        public Operator op() { return op; }
        public Operand operand() { return operand; }
        @Override public <R> R accept(Visitor<R> v) { return v.visitCount(this); }
        @Override public String toString() {
            return "count(" + stream + (body == null ? "" : " where (" + body + ")") + ") " + op + " " + operand;
        }
    }

    /**
     * {@code SEQUENCE [ e1, e2, .. ]} — an ordered, unanchored regex over the method's instruction
     * stream. Each {@link Element} carries a per-instruction matcher condition plus repetition bounds;
     * a gap ({@code ..}) and any-one ({@code _}) are just {@link True} matchers with different bounds.
     */
    final class Sequence implements Condition {
        private final List<Element> elements;
        public Sequence(List<Element> elements) { this.elements = List.copyOf(elements); }
        public List<Element> elements() { return elements; }
        @Override public <R> R accept(Visitor<R> v) { return v.visitSequence(this); }
        @Override public String toString() {
            StringBuilder sb = new StringBuilder("sequence [");
            for (int i = 0; i < elements.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(elements.get(i));
            }
            return sb.append("]").toString();
        }

        /** One pattern element: a matcher evaluated against an instruction, repeated {@code [min,max]} times. */
        public static final class Element {
            public static final int UNBOUNDED = Integer.MAX_VALUE;
            private final Condition matcher;
            private final int min;
            private final int max;
            public Element(Condition matcher, int min, int max) {
                this.matcher = matcher;
                this.min = min;
                this.max = max;
            }
            public Condition matcher() { return matcher; }
            public int min() { return min; }
            public int max() { return max; }
            public static Element gap() { return new Element(True.INSTANCE, 0, UNBOUNDED); }
            @Override public String toString() {
                String base = matcher == True.INSTANCE && min == 0 ? ".." : matcher.toString();
                if (matcher == True.INSTANCE) {
                    return min == 0 ? ".." : "_";
                }
                String rep = (min == 1 && max == 1) ? ""
                        : (min == 0 && max == UNBOUNDED) ? "*"
                        : (min == 1 && max == UNBOUNDED) ? "+"
                        : "{" + min + "," + (max == UNBOUNDED ? "" : max) + "}";
                return "(" + base + ")" + rep;
            }
        }
    }

    /** Constant truth, used as an identity element when simplifying/splitting trees. */
    final class True implements Condition {
        public static final True INSTANCE = new True();
        private True() { }
        @Override public <R> R accept(Visitor<R> v) { return v.visitTrue(this); }
        @Override public String toString() { return "true"; }
    }

    static String join(List<Condition> terms, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < terms.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(terms.get(i));
        }
        return sb.toString();
    }
}
