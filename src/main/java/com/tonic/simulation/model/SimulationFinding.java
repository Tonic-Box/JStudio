package com.tonic.simulation.model;

import com.tonic.analysis.ssa.ir.IRInstruction;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** The base of every simulation finding: where it is, what kind it is, how severe, and its display text. */
@Getter
@RequiredArgsConstructor
public abstract class SimulationFinding
{

    /** The kind of a finding. */
    public enum FindingType
    {
        OPAQUE_PREDICATE,
        DEAD_CODE,
        CONSTANT_VALUE,
        TAINTED_VALUE,
        DECRYPTED_STRING
    }

    /** How serious a finding is, from INFO up to CRITICAL. */
    public enum Severity
    {
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

    /**
     * Finds the bytecode offset of an IR instruction.
     *
     * @param instruction the instruction, or null
     * @return the offset the lifter stamped on it, else the offset of its block, else -1
     */
    public static int offsetOf(IRInstruction instruction)
    {
        if (instruction == null)
        {
            return -1;
        }
        if (instruction.getBytecodeOffset() >= 0)
        {
            return instruction.getBytecodeOffset();
        }
        return instruction.getBlock() != null ? instruction.getBlock().getBytecodeOffset() : -1;
    }

    /**
     * Formats the method the finding is in.
     *
     * @return the class name, a dot, then the method name and descriptor
     */
    public String getMethodSignature()
    {
        return className + "." + methodName + methodDesc;
    }

    /**
     * Returns the finding's one-line title.
     *
     * @return the title
     */
    public abstract String getTitle();

    /**
     * Returns the finding's multi-line explanation.
     *
     * @return the description
     */
    public abstract String getDescription();

    /**
     * Returns what to do about the finding.
     *
     * @return the recommendation
     */
    public abstract String getRecommendation();
}
