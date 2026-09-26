package com.tonic.simulation;

import com.tonic.analysis.simulation.core.SimulationResult;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.model.MethodEntryModel;
import com.tonic.simulation.model.SimulationFinding;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/** The results of simulating one method: its findings, dead blocks and engine statistics. */
public class SimulationAnalysisResult
{

    @Getter
    private final MethodEntryModel method;
    @Getter
    private final SimulationResult engineResult;
    private final List<SimulationFinding> findings;
    private final Set<IRBlock> deadBlocks;
    @Getter
    private final int blocksVisited;
    @Getter
    private final int branchCount;

    /**
     * Creates a result; null findings or dead blocks become empty.
     *
     * @param method the analyzed method
     * @param engineResult the simulation engine's raw result, or null
     * @param findings the findings raised, or null for none
     * @param deadBlocks the blocks never reached, or null for none
     * @param blocksVisited how many blocks the simulation visited
     * @param branchCount how many branches the simulation saw
     */
    public SimulationAnalysisResult(MethodEntryModel method, SimulationResult engineResult, List<SimulationFinding> findings, Set<IRBlock> deadBlocks, int blocksVisited, int branchCount)
    {
        this.method = method;
        this.engineResult = engineResult;
        this.findings = findings != null ? findings : Collections.emptyList();
        this.deadBlocks = deadBlocks != null ? deadBlocks : Collections.emptySet();
        this.blocksVisited = blocksVisited;
        this.branchCount = branchCount;
    }

    /**
     * Returns the findings raised.
     *
     * @return the findings, unmodifiable
     */
    public List<SimulationFinding> getFindings()
    {
        return Collections.unmodifiableList(findings);
    }

    /**
     * Returns the blocks the simulation never reached.
     *
     * @return the dead blocks, unmodifiable
     */
    public Set<IRBlock> getDeadBlocks()
    {
        return Collections.unmodifiableSet(deadBlocks);
    }

    /**
     * Reports whether the analysis found anything.
     *
     * @return true if there are findings or dead blocks
     */
    public boolean hasFindings()
    {
        return !findings.isEmpty() || !deadBlocks.isEmpty();
    }

    /**
     * Counts the opaque-predicate findings.
     *
     * @return the number of findings of type OPAQUE_PREDICATE
     */
    public int getOpaquePredicateCount()
    {
        return (int) findings.stream()
                .filter(f -> f.getType() == SimulationFinding.FindingType.OPAQUE_PREDICATE)
                .count();
    }

    /**
     * Counts the unreached blocks.
     *
     * @return the number of dead blocks
     */
    public int getDeadBlockCount()
    {
        return deadBlocks.size();
    }

    /**
     * Reports how many instructions the simulation executed.
     *
     * @return the instruction count, or 0 without an engine result
     */
    public int getTotalInstructions()
    {
        return engineResult != null ? engineResult.getTotalInstructions() : 0;
    }

    /**
     * Reports the deepest operand stack seen during simulation.
     *
     * @return the maximum stack depth, or 0 without an engine result
     */
    public int getMaxStackDepth()
    {
        return engineResult != null ? engineResult.getMaxStackDepth() : 0;
    }

    /**
     * Reports how long the simulation took.
     *
     * @return the simulation time in milliseconds, or 0 without an engine result
     */
    public double getSimulationTimeMillis()
    {
        return engineResult != null ? engineResult.getSimulationTimeMillis() : 0;
    }

    @Override
    public String toString()
    {
        return "SimulationAnalysisResult[" +
                "method=" + (method != null ? method.getDisplaySignature() : "null") +
                ", findings=" + findings.size() +
                ", deadBlocks=" + deadBlocks.size() +
                ", blocksVisited=" + blocksVisited +
                "]";
    }
}
