package com.tonic.ui.simulation;

import com.tonic.analysis.simulation.core.SimulationResult;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.simulation.model.SimulationFinding;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Contains the complete results of simulation analysis for a single method.
 */
public class SimulationAnalysisResult {

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

    public SimulationAnalysisResult(MethodEntryModel method,
                                    SimulationResult engineResult,
                                    List<SimulationFinding> findings,
                                    Set<IRBlock> deadBlocks,
                                    int blocksVisited,
                                    int branchCount) {
        this.method = method;
        this.engineResult = engineResult;
        this.findings = findings != null ? findings : Collections.emptyList();
        this.deadBlocks = deadBlocks != null ? deadBlocks : Collections.emptySet();
        this.blocksVisited = blocksVisited;
        this.branchCount = branchCount;
    }

    public List<SimulationFinding> getFindings() {
        return Collections.unmodifiableList(findings);
    }

    public Set<IRBlock> getDeadBlocks() {
        return Collections.unmodifiableSet(deadBlocks);
    }

    public boolean hasFindings() {
        return !findings.isEmpty() || !deadBlocks.isEmpty();
    }

    public int getOpaquePredicateCount() {
        return (int) findings.stream()
                .filter(f -> f.getType() == SimulationFinding.FindingType.OPAQUE_PREDICATE)
                .count();
    }

    public int getDeadBlockCount() {
        return deadBlocks.size();
    }

    public int getTotalInstructions() {
        return engineResult != null ? engineResult.getTotalInstructions() : 0;
    }

    public int getMaxStackDepth() {
        return engineResult != null ? engineResult.getMaxStackDepth() : 0;
    }

    public double getSimulationTimeMillis() {
        return engineResult != null ? engineResult.getSimulationTimeMillis() : 0;
    }

    @Override
    public String toString() {
        return "SimulationAnalysisResult[" +
                "method=" + (method != null ? method.getDisplaySignature() : "null") +
                ", findings=" + findings.size() +
                ", deadBlocks=" + deadBlocks.size() +
                ", blocksVisited=" + blocksVisited +
                "]";
    }
}
