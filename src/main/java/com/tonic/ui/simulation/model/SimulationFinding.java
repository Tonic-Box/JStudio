package com.tonic.ui.simulation.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public abstract class SimulationFinding {

    public enum FindingType {
        OPAQUE_PREDICATE,
        DEAD_CODE,
        CONSTANT_VALUE,
        TAINTED_VALUE,
        DECRYPTED_STRING
    }

    public enum Severity {
        INFO,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    protected final String className;
    protected final String methodName;
    protected final String methodDesc;
    protected final FindingType type;
    protected final Severity severity;
    protected final int bytecodeOffset;

    public String getMethodSignature() {
        return className + "." + methodName + methodDesc;
    }

    public abstract String getTitle();

    public abstract String getDescription();

    public abstract String getRecommendation();
}
