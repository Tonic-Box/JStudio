package com.tonic.ui.simulation;

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
import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.StatusMessageEvent;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.service.ProjectService;

import com.tonic.ui.simulation.listener.OpaquePredicateListener;
import com.tonic.ui.simulation.listener.StringDecryptionListener;
import com.tonic.ui.simulation.listener.TaintTrackingListener;
import com.tonic.ui.simulation.model.DeadCodeBlock;
import com.tonic.ui.simulation.model.DecryptedString;
import com.tonic.ui.simulation.model.SimulationFinding;
import com.tonic.ui.simulation.model.OpaquePredicate;
import com.tonic.ui.simulation.model.TaintFlow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
    private final List<SimulationServiceListener> listeners = new ArrayList<>();

    private SimulationService() {
    }

    public static SimulationService getInstance() {
        return INSTANCE;
    }

    public void addListener(SimulationServiceListener listener) {
        listeners.add(listener);
    }

    public void removeListener(SimulationServiceListener listener) {
        listeners.remove(listener);
    }

    /**
     * Runs opaque predicate analysis on a single method.
     */
    public List<OpaquePredicate> analyzeOpaquePredicates(MethodEntryModel methodModel) {
        if (methodModel == null) {
            return Collections.emptyList();
        }

        ClassEntryModel classModel = methodModel.getOwner();
        MethodEntry method = methodModel.getMethodEntry();

        if (method.getCodeAttribute() == null) {
            return Collections.emptyList();
        }

        try {
            IRMethod irMethod = liftToIR(classModel.getClassFile(), method);
            if (irMethod == null) {
                return Collections.emptyList();
            }

            return runOpaquePredicateAnalysis(irMethod, classModel, methodModel);
        } catch (Exception e) {
            postStatus("Simulation error: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Runs opaque predicate analysis on all methods in a class.
     */
    public List<OpaquePredicate> analyzeOpaquePredicates(ClassEntryModel classModel) {
        List<OpaquePredicate> allFindings = new ArrayList<>();

        for (MethodEntryModel methodModel : classModel.getMethods()) {
            List<OpaquePredicate> findings = analyzeOpaquePredicates(methodModel);
            allFindings.addAll(findings);
        }

        return allFindings;
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

            notifyAnalysisComplete(methodModel, result);
            return result;
        } catch (Exception e) {
            postStatus("Analysis error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Runs analysis on all methods in the current project.
     */
    public Map<MethodEntryModel, SimulationAnalysisResult> analyzeProject(ProgressCallback callback) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            return Collections.emptyMap();
        }

        Map<MethodEntryModel, SimulationAnalysisResult> results = new HashMap<>();
        List<ClassEntryModel> classes = project.getAllClasses();

        int total = classes.stream().mapToInt(c -> c.getMethods().size()).sum();
        int current = 0;

        for (ClassEntryModel classModel : classes) {
            for (MethodEntryModel methodModel : classModel.getMethods()) {
                SimulationAnalysisResult result = runAnalysis(methodModel);
                if (result != null && result.hasFindings()) {
                    results.put(methodModel, result);
                }

                current++;
                if (callback != null) {
                    callback.onProgress(current, total, methodModel.getDisplaySignature());
                }
            }
        }

        return results;
    }

    /**
     * Gets dead code blocks for a method.
     */
    public Set<IRBlock> findDeadCode(MethodEntryModel methodModel) {
        if (methodModel == null) {
            return Collections.emptySet();
        }

        ClassEntryModel classModel = methodModel.getOwner();
        MethodEntry method = methodModel.getMethodEntry();

        if (method.getCodeAttribute() == null) {
            return Collections.emptySet();
        }

        try {
            IRMethod irMethod = liftToIR(classModel.getClassFile(), method);
            if (irMethod == null) {
                return Collections.emptySet();
            }

            return computeDeadCode(irMethod);
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    /**
     * Clears the result cache.
     */
    public void clearCache() {
        resultCache.clear();
    }

    /**
     * Clears cached results for a specific class.
     */
    public void clearCache(ClassEntryModel classModel) {
        String prefix = classModel.getClassName() + "#";
        resultCache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private IRMethod liftToIR(ClassFile classFile, MethodEntry method) {
        try {
            SSA ssa = new SSA(classFile.getConstPool());
            return ssa.lift(method);
        } catch (Exception e) {
            return null;
        }
    }

    private List<OpaquePredicate> runOpaquePredicateAnalysis(IRMethod irMethod,
                                                             ClassEntryModel classModel,
                                                             MethodEntryModel methodModel) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        ClassPool pool = project != null ? project.getClassPool() : null;

        SimulationContext ctx = SimulationContext.defaults()
                .withClassPool(pool)
                .withValueTracking(true);

        SimulationEngine engine = new SimulationEngine(ctx);

        OpaquePredicateListener opaqueListener = new OpaquePredicateListener();
        engine.addListener(opaqueListener);

        engine.simulate(irMethod);

        List<OpaquePredicate> findings = new ArrayList<>();
        for (OpaquePredicateListener.BranchAnalysis analysis : opaqueListener.getAnalyzedBranches()) {
            if (analysis.isOpaque()) {
                OpaquePredicate finding = new OpaquePredicate(
                        classModel.getClassName(),
                        methodModel.getMethodEntry().getName(),
                        methodModel.getMethodEntry().getDesc(),
                        analysis.getInstruction(),
                        analysis.isAlwaysTrue(),
                        analysis.getBlockId(),
                        analysis.getBytecodeOffset()
                );
                findings.add(finding);
            }
        }

        return findings;
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

    private Set<IRBlock> computeDeadCode(IRMethod irMethod) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        ClassPool pool = project != null ? project.getClassPool() : null;

        SimulationContext ctx = SimulationContext.defaults()
                .withClassPool(pool);

        SimulationEngine engine = new SimulationEngine(ctx);

        ControlFlowListener cfListener = new ControlFlowListener();
        engine.addListener(cfListener);

        engine.simulate(irMethod);

        return computeDeadCodeFromListener(irMethod, cfListener);
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

    private void notifyAnalysisComplete(MethodEntryModel methodModel, SimulationAnalysisResult result) {
        for (SimulationServiceListener listener : listeners) {
            listener.onAnalysisComplete(methodModel, result);
        }
    }

    /**
     * Progress callback for batch analysis.
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int current, int total, String currentMethod);
    }

    /**
     * Listener for simulation service events.
     */
    public interface SimulationServiceListener {
        void onAnalysisComplete(MethodEntryModel method, SimulationAnalysisResult result);
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
