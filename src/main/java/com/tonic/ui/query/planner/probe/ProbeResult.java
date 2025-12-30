package com.tonic.ui.query.planner.probe;

import com.tonic.ui.query.planner.ClickTarget;
import com.tonic.ui.query.planner.Evidence;
import com.tonic.ui.query.planner.ResultRow;

import java.util.*;

/**
 * Aggregated results from all probes during a single execution run.
 */
public class ProbeResult {

    private final String methodSignature;
    private final long instructionCount;
    private final long executionTimeNs;

    private final Map<String, Integer> allocationCounts;
    private final List<CallEvent> callEvents;
    private final List<FieldEvent> fieldEvents;
    private final List<StringEvent> stringEvents;
    private final List<ExceptionEvent> exceptionEvents;
    private final List<BranchEvent> branchEvents;

    private ProbeResult(Builder builder) {
        this.methodSignature = builder.methodSignature;
        this.instructionCount = builder.instructionCount;
        this.executionTimeNs = builder.executionTimeNs;
        this.allocationCounts = new HashMap<>(builder.allocationCounts);
        this.callEvents = new ArrayList<>(builder.callEvents);
        this.fieldEvents = new ArrayList<>(builder.fieldEvents);
        this.stringEvents = new ArrayList<>(builder.stringEvents);
        this.exceptionEvents = new ArrayList<>(builder.exceptionEvents);
        this.branchEvents = new ArrayList<>(builder.branchEvents);
    }

    public String getMethodSignature() {
        return methodSignature;
    }

    public long getInstructionCount() {
        return instructionCount;
    }

    public long getExecutionTimeNs() {
        return executionTimeNs;
    }

    public int getAllocationCount(String typeName) {
        return allocationCounts.getOrDefault(typeName, 0);
    }

    public Map<String, Integer> getAllocationCounts() {
        return Collections.unmodifiableMap(allocationCounts);
    }

    public List<CallEvent> getCallEvents() {
        return Collections.unmodifiableList(callEvents);
    }

    public List<FieldEvent> getFieldEvents() {
        return Collections.unmodifiableList(fieldEvents);
    }

    public List<StringEvent> getStringEvents() {
        return Collections.unmodifiableList(stringEvents);
    }

    public List<ExceptionEvent> getExceptionEvents() {
        return Collections.unmodifiableList(exceptionEvents);
    }

    public boolean hasCallTo(String owner, String name) {
        return callEvents.stream().anyMatch(e ->
            (owner == null || e.targetOwner().contains(owner)) &&
            (name == null || e.targetName().equals(name)));
    }

    public List<ResultRow> toMethodRows() {
        List<Evidence> evidence = new ArrayList<>();
        for (CallEvent ce : callEvents) {
            evidence.add(Evidence.call(ce.sequence(), methodSignature, ce.pc(),
                ce.targetOwner() + "." + ce.targetName() + ce.targetDesc()));
        }

        ClickTarget target = parseMethodTarget(methodSignature);
        return Arrays.asList(ResultRow.builder(methodSignature)
            .target(target)
            .column("instructionCount", instructionCount)
            .column("allocations", allocationCounts)
            .evidence(evidence)
            .build());
    }

    public List<ResultRow> toClassRows() {
        String className = extractClass(methodSignature);
        ClickTarget target = new ClickTarget.ClassTarget(className);
        return Arrays.asList(ResultRow.builder(className)
            .target(target)
            .column("methods", 1)
            .column("allocations", allocationCounts)
            .build());
    }

    public List<ResultRow> toPathRows() {
        return Collections.emptyList();
    }

    public List<ResultRow> toEventRows() {
        List<ResultRow> rows = new ArrayList<>();
        for (CallEvent ce : callEvents) {
            ClickTarget target = parsePCTarget(methodSignature, ce.pc());
            rows.add(ResultRow.builder("CALL " + ce.targetOwner() + "." + ce.targetName())
                .target(target)
                .column("sequence", ce.sequence())
                .column("pc", ce.pc())
                .build());
        }
        for (FieldEvent fe : fieldEvents) {
            ClickTarget target = parsePCTarget(methodSignature, fe.pc());
            rows.add(ResultRow.builder((fe.isWrite() ? "WRITE " : "READ ") + fe.fieldName())
                .target(target)
                .column("sequence", fe.sequence())
                .column("pc", fe.pc())
                .build());
        }
        return rows;
    }

    public List<ResultRow> toStringRows() {
        List<ResultRow> rows = new ArrayList<>();
        for (StringEvent se : stringEvents) {
            String preview = se.value().length() > 100 ?
                se.value().substring(0, 97) + "..." : se.value();
            ClickTarget target = parsePCTarget(methodSignature, se.pc());
            rows.add(ResultRow.builder(preview)
                .target(target)
                .column("sequence", se.sequence())
                .column("pc", se.pc())
                .column("origin", se.origin())
                .build());
        }
        return rows;
    }

    public List<ResultRow> toObjectRows() {
        List<ResultRow> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : allocationCounts.entrySet()) {
            rows.add(ResultRow.builder(entry.getKey())
                .column("count", entry.getValue())
                .build());
        }
        return rows;
    }

    private String extractClass(String signature) {
        int dot = signature.lastIndexOf('.');
        return dot > 0 ? signature.substring(0, dot) : signature;
    }

    private ClickTarget parseMethodTarget(String signature) {
        if (signature == null || signature.isEmpty()) {
            return null;
        }
        int parenIdx = signature.indexOf('(');
        if (parenIdx < 0) {
            return null;
        }
        String classAndMethod = signature.substring(0, parenIdx);
        String descriptor = signature.substring(parenIdx);

        int dotIdx = classAndMethod.lastIndexOf('.');
        if (dotIdx < 0) {
            return null;
        }
        String className = classAndMethod.substring(0, dotIdx);
        String methodName = classAndMethod.substring(dotIdx + 1);

        return new ClickTarget.MethodTarget(className, methodName, descriptor);
    }

    private ClickTarget parsePCTarget(String signature, int pc) {
        if (signature == null || signature.isEmpty()) {
            return null;
        }
        int parenIdx = signature.indexOf('(');
        if (parenIdx < 0) {
            return null;
        }
        String classAndMethod = signature.substring(0, parenIdx);
        String descriptor = signature.substring(parenIdx);

        int dotIdx = classAndMethod.lastIndexOf('.');
        if (dotIdx < 0) {
            return null;
        }
        String className = classAndMethod.substring(0, dotIdx);
        String methodName = classAndMethod.substring(dotIdx + 1);

        return new ClickTarget.PCTarget(className, methodName, descriptor, pc);
    }

    public static Builder builder(String methodSignature) {
        return new Builder(methodSignature);
    }

    public static final class CallEvent {
        private final long sequence;
        private final int pc;
        private final String targetOwner;
        private final String targetName;
        private final String targetDesc;

        public CallEvent(long sequence, int pc, String targetOwner, String targetName, String targetDesc) {
            this.sequence = sequence;
            this.pc = pc;
            this.targetOwner = targetOwner;
            this.targetName = targetName;
            this.targetDesc = targetDesc;
        }

        public long sequence() { return sequence; }
        public int pc() { return pc; }
        public String targetOwner() { return targetOwner; }
        public String targetName() { return targetName; }
        public String targetDesc() { return targetDesc; }
    }

    public static final class FieldEvent {
        private final long sequence;
        private final int pc;
        private final String owner;
        private final String fieldName;
        private final String desc;
        private final boolean isWrite;

        public FieldEvent(long sequence, int pc, String owner, String fieldName, String desc, boolean isWrite) {
            this.sequence = sequence;
            this.pc = pc;
            this.owner = owner;
            this.fieldName = fieldName;
            this.desc = desc;
            this.isWrite = isWrite;
        }

        public long sequence() { return sequence; }
        public int pc() { return pc; }
        public String owner() { return owner; }
        public String fieldName() { return fieldName; }
        public String desc() { return desc; }
        public boolean isWrite() { return isWrite; }
    }

    public static final class StringEvent {
        private final long sequence;
        private final int pc;
        private final String value;
        private final String origin;

        public StringEvent(long sequence, int pc, String value, String origin) {
            this.sequence = sequence;
            this.pc = pc;
            this.value = value;
            this.origin = origin;
        }

        public long sequence() { return sequence; }
        public int pc() { return pc; }
        public String value() { return value; }
        public String origin() { return origin; }
    }

    public static final class ExceptionEvent {
        private final long sequence;
        private final int pc;
        private final String exceptionType;
        private final boolean caught;

        public ExceptionEvent(long sequence, int pc, String exceptionType, boolean caught) {
            this.sequence = sequence;
            this.pc = pc;
            this.exceptionType = exceptionType;
            this.caught = caught;
        }

        public long sequence() { return sequence; }
        public int pc() { return pc; }
        public String exceptionType() { return exceptionType; }
        public boolean caught() { return caught; }
    }

    public static final class BranchEvent {
        private final long sequence;
        private final int fromPc;
        private final int toPc;
        private final boolean taken;

        public BranchEvent(long sequence, int fromPc, int toPc, boolean taken) {
            this.sequence = sequence;
            this.fromPc = fromPc;
            this.toPc = toPc;
            this.taken = taken;
        }

        public long sequence() { return sequence; }
        public int fromPc() { return fromPc; }
        public int toPc() { return toPc; }
        public boolean taken() { return taken; }
    }

    public static class Builder {
        private final String methodSignature;
        private long instructionCount;
        private long executionTimeNs;
        private final Map<String, Integer> allocationCounts = new HashMap<>();
        private final List<CallEvent> callEvents = new ArrayList<>();
        private final List<FieldEvent> fieldEvents = new ArrayList<>();
        private final List<StringEvent> stringEvents = new ArrayList<>();
        private final List<ExceptionEvent> exceptionEvents = new ArrayList<>();
        private final List<BranchEvent> branchEvents = new ArrayList<>();

        public Builder(String methodSignature) {
            this.methodSignature = methodSignature;
        }

        public Builder instructionCount(long count) {
            this.instructionCount = count;
            return this;
        }

        public Builder executionTime(long ns) {
            this.executionTimeNs = ns;
            return this;
        }

        public Builder recordAllocation(String typeName) {
            allocationCounts.merge(typeName, 1, Integer::sum);
            return this;
        }

        public Builder recordCall(long seq, int pc, String owner, String name, String desc) {
            callEvents.add(new CallEvent(seq, pc, owner, name, desc));
            return this;
        }

        public Builder recordFieldAccess(long seq, int pc, String owner, String name, String desc, boolean isWrite) {
            fieldEvents.add(new FieldEvent(seq, pc, owner, name, desc, isWrite));
            return this;
        }

        public Builder recordString(long seq, int pc, String value, String origin) {
            stringEvents.add(new StringEvent(seq, pc, value, origin));
            return this;
        }

        public Builder recordException(long seq, int pc, String type, boolean caught) {
            exceptionEvents.add(new ExceptionEvent(seq, pc, type, caught));
            return this;
        }

        public Builder recordBranch(long seq, int from, int to, boolean taken) {
            branchEvents.add(new BranchEvent(seq, from, to, taken));
            return this;
        }

        public ProbeResult build() {
            return new ProbeResult(this);
        }
    }
}
