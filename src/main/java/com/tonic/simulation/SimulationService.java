package com.tonic.simulation;

import com.tonic.analysis.simulation.core.SimulationContext;
import com.tonic.analysis.simulation.core.SimulationEngine;
import com.tonic.analysis.simulation.core.SimulationResult;
import com.tonic.analysis.simulation.listener.ControlFlowListener;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.event.EventBus;
import com.tonic.event.events.StatusMessageEvent;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.service.ProjectService;

import com.tonic.simulation.listener.OpaquePredicateListener;
import com.tonic.simulation.listener.StringDecryptionListener;
import com.tonic.simulation.listener.TaintTrackingListener;
import com.tonic.simulation.model.DeadCodeBlock;
import com.tonic.simulation.model.DecryptedString;
import com.tonic.simulation.model.SimulationFinding;
import com.tonic.simulation.model.OpaquePredicate;
import com.tonic.simulation.model.TaintFlow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for running symbolic simulation analysis on bytecode methods.
 * Wraps YABR's simulation engine and provides JStudio-specific analysis features.
 */
public class SimulationService {

    private static final SimulationService INSTANCE = new SimulationService();

    private final Map<String, SimulationResultCache> resultCache = new ConcurrentHashMap<>();

    private SimulationService() {
    }

    public static SimulationService getInstance() {
        return INSTANCE;
    }

    /**
     * Runs full simulation analysis on a method and caches the result.
     */
    public SimulationAnalysisResult runAnalysis(MethodEntryModel methodModel) {
        if (methodModel == null) {
            return null;
        }

        String cacheKey = getCacheKey(methodModel);
        SimulationResultCache cached = resultCache.get(cacheKey);
        if (cached != null && !cached.isStale()) {
            return cached.getResult();
        }

        ClassEntryModel classModel = methodModel.getOwner();
        MethodEntry method = methodModel.getMethodEntry();

        if (method.getCodeAttribute() == null) {
            return null;
        }

        try {
            IRMethod irMethod = liftToIR(classModel.getClassFile(), method);
            if (irMethod == null) {
                return null;
            }

            SimulationAnalysisResult result = runFullAnalysis(irMethod, classModel, methodModel);
            resultCache.put(cacheKey, new SimulationResultCache(result));

            return result;
        } catch (Exception e) {
            postStatus("Analysis error: " + e.getMessage());
            return null;
        }
    }

    private IRMethod liftToIR(ClassFile classFile, MethodEntry method) {
        try {
            SSA ssa = new SSA(classFile.getConstPool());
            return ssa.lift(method);
        } catch (Exception e) {
            return null;
        }
    }

    private SimulationAnalysisResult runFullAnalysis(IRMethod irMethod,
                                                      ClassEntryModel classModel,
                                                      MethodEntryModel methodModel) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        ClassPool pool = project != null ? project.getClassPool() : null;

        SimulationContext ctx = SimulationContext.defaults()
                .withClassPool(pool)
                .withValueTracking(true)
                .withStackOperationTracking(true);

        SimulationEngine engine = new SimulationEngine(ctx);

        ControlFlowListener cfListener = new ControlFlowListener(true);
        OpaquePredicateListener opaqueListener = new OpaquePredicateListener();
        StringDecryptionListener decryptionListener = new StringDecryptionListener();
        TaintTrackingListener taintListener = new TaintTrackingListener();

        engine.addListener(cfListener);
        engine.addListener(opaqueListener);
        engine.addListener(decryptionListener);
        engine.addListener(taintListener);

        SimulationResult result = engine.simulate(irMethod);

        List<SimulationFinding> findings = new ArrayList<>();

        for (OpaquePredicateListener.BranchAnalysis analysis : opaqueListener.getAnalyzedBranches()) {
            if (analysis.isOpaque()) {
                findings.add(new OpaquePredicate(
                        classModel.getClassName(),
                        methodModel.getMethodEntry().getName(),
                        methodModel.getMethodEntry().getDesc(),
                        analysis.getInstruction(),
                        analysis.isAlwaysTrue(),
                        analysis.getBlockId(),
                        analysis.getBytecodeOffset()
                ));
            }
        }

        Set<IRBlock> deadBlocks = computeDeadCodeFromListener(irMethod, cfListener);

        for (IRBlock deadBlock : deadBlocks) {
            boolean isExceptionHandler = isExceptionHandlerBlock(deadBlock, irMethod);
            findings.add(new DeadCodeBlock(
                    classModel.getClassName(),
                    methodModel.getMethodEntry().getName(),
                    methodModel.getMethodEntry().getDesc(),
                    deadBlock,
                    isExceptionHandler
            ));
        }

        for (StringDecryptionListener.DecryptionResult dr : decryptionListener.getDecryptionResults()) {
            findings.add(new DecryptedString(
                    classModel.getClassName(),
                    methodModel.getMethodEntry().getName(),
                    methodModel.getMethodEntry().getDesc(),
                    dr.getInstruction(),
                    dr.getDecryptedValue(),
                    dr.getEncryptedInput(),
                    dr.getMethodUsed()
            ));
        }

        for (TaintTrackingListener.TaintFlowResult tf : taintListener.getTaintFlows()) {
            findings.add(new TaintFlow(
                    classModel.getClassName(),
                    methodModel.getMethodEntry().getName(),
                    methodModel.getMethodEntry().getDesc(),
                    tf.getSinkInstruction(),
                    tf.getSourceDescription(),
                    tf.getSinkDescription(),
                    tf.getFlowPath(),
                    tf.getCategory()
            ));
        }

        return new SimulationAnalysisResult(
                methodModel,
                result,
                findings,
                deadBlocks,
                cfListener.getBlocksVisited(),
                cfListener.getBranchCount()
        );
    }

    private Set<IRBlock> computeDeadCodeFromListener(IRMethod irMethod, ControlFlowListener cfListener) {
        Set<IRBlock> deadBlocks = new HashSet<>();

        for (IRBlock block : irMethod.getBlocks()) {
            if (!cfListener.wasVisited(block)) {
                deadBlocks.add(block);
            }
        }

        return deadBlocks;
    }

    private boolean isExceptionHandlerBlock(IRBlock block, IRMethod irMethod) {
        for (IRBlock methodBlock : irMethod.getBlocks()) {
            for (var handler : methodBlock.getExceptionHandlers()) {
                if (handler.getHandlerBlock() == block) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getCacheKey(MethodEntryModel methodModel) {
        ClassEntryModel classModel = methodModel.getOwner();
        MethodEntry method = methodModel.getMethodEntry();
        return classModel.getClassName() + "#" + method.getName() + method.getDesc();
    }

    private void postStatus(String message) {
        EventBus.getInstance().post(new StatusMessageEvent(this, message));
    }

    /**
     * Cache entry for simulation results.
     */
    private static class SimulationResultCache {
        private final SimulationAnalysisResult result;
        private final long timestamp;

        SimulationResultCache(SimulationAnalysisResult result) {
            this.result = result;
            this.timestamp = System.currentTimeMillis();
        }

        SimulationAnalysisResult getResult() {
            return result;
        }

        boolean isStale() {
            return System.currentTimeMillis() - timestamp > 5 * 60 * 1000;
        }
    }
}
