package com.tonic.ui.query.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates the bytecode locations that satisfied a query, so results stay navigable. A matching
 * instruction/call/arg records the owning method signature + the producing instruction's pc; the
 * planner turns these into {@code PCTarget} result rows (replacing the old xref thread-local).
 */
public final class EvidenceCollector {

    /** A single matched location. */
    public static final class Hit {
        private final String className;
        private final String methodName;
        private final String descriptor;
        private final int pc;
        private final String label;

        public Hit(String className, String methodName, String descriptor, int pc, String label) {
            this.className = className;
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.pc = pc;
            this.label = label;
        }

        public String className() { return className; }
        public String methodName() { return methodName; }
        public String descriptor() { return descriptor; }
        public int pc() { return pc; }
        public String label() { return label; }
    }

    private final List<Hit> hits = new ArrayList<>();

    public void record(String className, String methodName, String descriptor, int pc, String label) {
        hits.add(new Hit(className, methodName, descriptor, pc, label));
    }

    public List<Hit> hits() {
        return hits;
    }

    public boolean isEmpty() {
        return hits.isEmpty();
    }
}
