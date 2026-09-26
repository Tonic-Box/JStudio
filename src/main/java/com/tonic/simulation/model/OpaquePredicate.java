package com.tonic.simulation.model;

import com.tonic.analysis.ssa.ir.BranchInstruction;
import lombok.Getter;

/** A medium-severity finding for a branch whose condition always evaluated the same way during simulation. */
@Getter
public class OpaquePredicate extends SimulationFinding
{

    private final BranchInstruction branchInstruction;
    private final boolean alwaysTrue;
    private final int blockId;

    /**
     * Creates an opaque-predicate finding.
     *
     * @param className the owning class's internal name
     * @param methodName the method's name
     * @param methodDesc the method's descriptor
     * @param branchInstruction the branch
     * @param alwaysTrue whether the condition was always true rather than always false
     * @param blockId the id of the block holding the branch
     * @param bytecodeOffset the branch's offset
     */
    public OpaquePredicate(String className, String methodName, String methodDesc, BranchInstruction branchInstruction, boolean alwaysTrue, int blockId, int bytecodeOffset)
    {
        super(className, methodName, methodDesc, FindingType.OPAQUE_PREDICATE, Severity.MEDIUM, bytecodeOffset);
        this.branchInstruction = branchInstruction;
        this.alwaysTrue = alwaysTrue;
        this.blockId = blockId;
    }

    /**
     * Reports whether the condition was always false.
     *
     * @return true if the condition never evaluated true
     */
    public boolean isAlwaysFalse()
    {
        return !alwaysTrue;
    }

    /**
     * Names the branch's comparison.
     *
     * @return the condition's name, or GOTO when there is no branch or condition
     */
    public String getConditionType()
    {
        if (branchInstruction == null || branchInstruction.getCondition() == null)
        {
            return "GOTO";
        }
        return branchInstruction.getCondition().name();
    }

    @Override
    public String getTitle()
    {
        return "Opaque Predicate (always " + (alwaysTrue ? "true" : "false") + ")";
    }

    @Override
    public String getDescription()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Branch condition at block ").append(blockId);
        sb.append(" always evaluates to ").append(alwaysTrue ? "true" : "false");
        sb.append(".\n\n");

        if (branchInstruction != null && branchInstruction.getCondition() != null)
        {
            sb.append("Condition: ").append(branchInstruction.getCondition().name());
        }

        sb.append("\n\nThis may indicate:");
        sb.append("\n• Intentional obfuscation to confuse analysis");
        sb.append("\n• Anti-tampering or anti-debugging check");
        sb.append("\n• Dead code from optimization failure");
        sb.append("\n• Unused conditional compilation artifact");

        return sb.toString();
    }

    @Override
    public String getRecommendation()
    {
        if (alwaysTrue)
        {
            return "The branch can be replaced with an unconditional jump to the true target. " +
                    "The false branch is dead code and can be removed.";
        }
        else
        {
            return "The branch can be replaced with an unconditional jump to the false target. " +
                    "The true branch is dead code and can be removed.";
        }
    }

    @Override
    public String toString()
    {
        return "OpaquePredicate[block=" + blockId +
                ", always=" + (alwaysTrue ? "true" : "false") +
                ", offset=" + bytecodeOffset + "]";
    }
}
