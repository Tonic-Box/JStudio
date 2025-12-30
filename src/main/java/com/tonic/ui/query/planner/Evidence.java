package com.tonic.ui.query.planner;

import java.util.Objects;

/**
 * Evidence of a query match - a specific event that contributed to the result.
 */
public final class Evidence {

    public enum EventType {
        CALL,
        ALLOCATION,
        FIELD_READ,
        FIELD_WRITE,
        STRING_CREATE,
        EXCEPTION_THROW,
        EXCEPTION_CATCH,
        BRANCH,
        METHOD_ENTRY,
        METHOD_EXIT
    }

    private final long sequence;
    private final String methodSignature;
    private final int pc;
    private final EventType eventType;
    private final String description;
    private final ClickTarget target;

    public Evidence(long sequence, String methodSignature, int pc, EventType eventType,
                    String description, ClickTarget target) {
        this.sequence = sequence;
        this.methodSignature = methodSignature;
        this.pc = pc;
        this.eventType = eventType;
        this.description = description;
        this.target = target;
    }

    public long sequence() {
        return sequence;
    }

    public String methodSignature() {
        return methodSignature;
    }

    public int pc() {
        return pc;
    }

    public EventType eventType() {
        return eventType;
    }

    public String description() {
        return description;
    }

    public ClickTarget target() {
        return target;
    }

    public static Evidence call(long seq, String method, int pc, String targetMethod) {
        return new Evidence(seq, method, pc, EventType.CALL,
            "Call to " + targetMethod,
            new ClickTarget.PCTarget(
                extractClass(method), extractMethod(method), extractDesc(method), pc));
    }

    public static Evidence allocation(long seq, String method, int pc, String typeName, int objectId) {
        return new Evidence(seq, method, pc, EventType.ALLOCATION,
            "Allocated " + typeName + " @" + Integer.toHexString(objectId),
            new ClickTarget.PCTarget(
                extractClass(method), extractMethod(method), extractDesc(method), pc));
    }

    public static Evidence fieldWrite(long seq, String method, int pc, String fieldName, String newValue) {
        return new Evidence(seq, method, pc, EventType.FIELD_WRITE,
            fieldName + " = " + newValue,
            new ClickTarget.PCTarget(
                extractClass(method), extractMethod(method), extractDesc(method), pc));
    }

    public static Evidence stringCreate(long seq, String method, int pc, String value) {
        String preview = value.length() > 50 ? value.substring(0, 47) + "..." : value;
        return new Evidence(seq, method, pc, EventType.STRING_CREATE,
            "String: \"" + preview + "\"",
            new ClickTarget.PCTarget(
                extractClass(method), extractMethod(method), extractDesc(method), pc));
    }

    public static Evidence exceptionThrow(long seq, String method, int pc, String exceptionType) {
        return new Evidence(seq, method, pc, EventType.EXCEPTION_THROW,
            "Throw " + exceptionType,
            new ClickTarget.PCTarget(
                extractClass(method), extractMethod(method), extractDesc(method), pc));
    }

    private static String extractClass(String signature) {
        int dot = signature.lastIndexOf('.');
        if (dot < 0) return signature;
        return signature.substring(0, dot);
    }

    private static String extractMethod(String signature) {
        int dot = signature.lastIndexOf('.');
        int paren = signature.indexOf('(');
        if (dot < 0) return signature;
        if (paren < 0) return signature.substring(dot + 1);
        return signature.substring(dot + 1, paren);
    }

    private static String extractDesc(String signature) {
        int paren = signature.indexOf('(');
        if (paren < 0) return "()V";
        return signature.substring(paren);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Evidence)) return false;
        Evidence evidence = (Evidence) o;
        return sequence == evidence.sequence &&
               pc == evidence.pc &&
               Objects.equals(methodSignature, evidence.methodSignature) &&
               eventType == evidence.eventType &&
               Objects.equals(description, evidence.description) &&
               Objects.equals(target, evidence.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequence, methodSignature, pc, eventType, description, target);
    }

    @Override
    public String toString() {
        return "Evidence{seq=" + sequence + ", method=" + methodSignature +
               ", pc=" + pc + ", type=" + eventType + ", desc='" + description + "'}";
    }
}
