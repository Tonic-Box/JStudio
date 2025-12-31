package com.tonic.ui.simulation.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public class MethodPurity extends SimulationFinding {

    public enum PurityLevel {
        PURE,
        READS_FIELDS,
        WRITES_FIELDS,
        HAS_SIDE_EFFECTS
    }

    private final PurityLevel purityLevel;
    private final int fieldReadCount;
    private final int fieldWriteCount;
    private final int arrayWriteCount;
    private final int methodCallCount;
    private final List<String> impureReasons;

    public MethodPurity(String className, String methodName, String methodDesc,
                        PurityLevel purityLevel, int fieldReadCount, int fieldWriteCount,
                        int arrayWriteCount, int methodCallCount, List<String> impureReasons) {
        super(className, methodName, methodDesc, FindingType.CONSTANT_VALUE,
                getSeverityForPurity(purityLevel), -1);
        this.purityLevel = purityLevel;
        this.fieldReadCount = fieldReadCount;
        this.fieldWriteCount = fieldWriteCount;
        this.arrayWriteCount = arrayWriteCount;
        this.methodCallCount = methodCallCount;
        this.impureReasons = impureReasons != null
                ? Collections.unmodifiableList(new ArrayList<>(impureReasons))
                : Collections.emptyList();
    }

    private static Severity getSeverityForPurity(PurityLevel level) {
        switch (level) {
            case PURE:
                return Severity.INFO;
            case READS_FIELDS:
                return Severity.INFO;
            case WRITES_FIELDS:
            case HAS_SIDE_EFFECTS:
                return Severity.LOW;
            default:
                return Severity.INFO;
        }
    }

    public boolean isPure() {
        return purityLevel == PurityLevel.PURE;
    }

    @Override
    public String getTitle() {
        return "Method Purity: " + purityLevel.name().replace("_", " ");
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Method purity analysis result: ").append(purityLevel.name()).append("\n\n");
        sb.append("Statistics:\n");
        sb.append("  Field reads: ").append(fieldReadCount).append("\n");
        sb.append("  Field writes: ").append(fieldWriteCount).append("\n");
        sb.append("  Array writes: ").append(arrayWriteCount).append("\n");
        sb.append("  Method calls: ").append(methodCallCount).append("\n");

        if (!impureReasons.isEmpty()) {
            sb.append("\nImpurity reasons:\n");
            for (String reason : impureReasons) {
                sb.append("  - ").append(reason).append("\n");
            }
        }

        return sb.toString();
    }

    @Override
    public String getRecommendation() {
        switch (purityLevel) {
            case PURE:
                return "This method is pure - its result depends only on its parameters. " +
                        "It can be safely memoized or called multiple times without side effects.";
            case READS_FIELDS:
                return "This method reads fields but doesn't modify state. " +
                        "Consider making it pure by passing required data as parameters.";
            case WRITES_FIELDS:
                return "This method modifies fields. Consider refactoring to separate " +
                        "computation from mutation for better testability.";
            case HAS_SIDE_EFFECTS:
                return "This method has side effects (I/O, native calls, etc.). " +
                        "Document the side effects and ensure they are intentional.";
            default:
                return "Review method implementation for side effects.";
        }
    }

    @Override
    public String toString() {
        return "MethodPurity[" + purityLevel +
                ", fieldReads=" + fieldReadCount +
                ", fieldWrites=" + fieldWriteCount + "]";
    }
}
