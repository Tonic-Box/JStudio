package com.tonic.ui.script.bridge;

import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.script.engine.ScriptInterpreter;
import com.tonic.ui.script.pipeline.ScriptPipeline;
import lombok.Getter;

import java.util.function.Consumer;

/**
 * Central registry for all script bridges.
 * Provides unified registration of global objects into the interpreter.
 */
public class BridgeRegistry {

    private final ScriptInterpreter interpreter;
    private final ProjectModel projectModel;
    private Consumer<String> logCallback;

    @Getter
    private ASTBridge astBridge;
    @Getter
    private IRBridge irBridge;
    @Getter
    private AnnotationBridge annotationBridge;
    @Getter
    private ResultsBridge resultsBridge;
    @Getter
    private ProjectBridge projectBridge;
    @Getter
    private CallGraphBridge callGraphBridge;
    @Getter
    private DataFlowBridge dataFlowBridge;
    @Getter
    private DependencyBridge dependencyBridge;
    @Getter
    private SimulationBridge simulationBridge;
    @Getter
    private InstrumentationBridge instrumentationBridge;
    @Getter
    private PatternBridge patternBridge;
    @Getter
    private TypeBridge typeBridge;
    @Getter
    private StringBridge stringBridge;
    @Getter
    private ScriptPipeline scriptPipeline;

    public BridgeRegistry(ScriptInterpreter interpreter, ProjectModel projectModel) {
        this.interpreter = interpreter;
        this.projectModel = projectModel;
    }

    public void setLogCallback(Consumer<String> log) {
        this.logCallback = log;
    }

    public void registerAll() {
        registerResultsBridge();
        registerProjectBridge();
        registerCallGraphBridge();
        registerDataFlowBridge();
        registerDependencyBridge();
        registerSimulationBridge();
        registerInstrumentationBridge();
        registerPatternBridge();
        registerTypeBridge();
        registerStringBridge();
        registerPipeline();
    }

    public void registerASTBridge() {
        astBridge = new ASTBridge(interpreter);
        if (logCallback != null) {
            astBridge.setLogCallback(logCallback);
        }
        interpreter.getGlobalContext().defineConstant("ast", astBridge.createAstObject());
    }

    public void registerIRBridge() {
        irBridge = new IRBridge(interpreter);
        if (logCallback != null) {
            irBridge.setLogCallback(logCallback);
        }
        interpreter.getGlobalContext().defineConstant("ir", irBridge.createIRObject());
    }

    public void registerAnnotationBridge() {
        annotationBridge = new AnnotationBridge(interpreter);
        if (logCallback != null) {
            annotationBridge.setLogCallback(logCallback);
        }
        interpreter.getGlobalContext().defineConstant("annotations", annotationBridge.createAnnotationObject());
    }

    public void registerResultsBridge() {
        resultsBridge = new ResultsBridge(interpreter);
        if (logCallback != null) {
            resultsBridge.setLogCallback(logCallback);
        }
        interpreter.getGlobalContext().defineConstant("results", resultsBridge.createResultsObject());
    }

    public void registerProjectBridge() {
        if (projectModel != null) {
            projectBridge = new ProjectBridge(interpreter, projectModel);
            if (logCallback != null) {
                projectBridge.setLogCallback(logCallback);
            }
            interpreter.getGlobalContext().defineConstant("project", projectBridge.createProjectObject());
        }
    }

    public void registerCallGraphBridge() {
        if (projectModel != null) {
            callGraphBridge = new CallGraphBridge(interpreter, projectModel);
            if (logCallback != null) {
                callGraphBridge.setLogCallback(logCallback);
            }
            interpreter.getGlobalContext().defineConstant("callgraph", callGraphBridge.createCallGraphObject());
        }
    }

    public void registerDataFlowBridge() {
        if (projectModel != null) {
            dataFlowBridge = new DataFlowBridge(projectModel);
            if (logCallback != null) {
                dataFlowBridge.setLogCallback(logCallback);
            }
            interpreter.getGlobalContext().defineConstant("dataflow", dataFlowBridge.createDataFlowObject());
        }
    }

    public void registerDependencyBridge() {
        if (projectModel != null) {
            dependencyBridge = new DependencyBridge(projectModel);
            if (logCallback != null) {
                dependencyBridge.setLogCallback(logCallback);
            }
            interpreter.getGlobalContext().defineConstant("dependencies", dependencyBridge.createDependencyObject());
        }
    }

    public void registerSimulationBridge() {
        if (projectModel != null) {
            simulationBridge = new SimulationBridge(interpreter, projectModel);
            if (logCallback != null) {
                simulationBridge.setLogCallback(logCallback);
            }
            interpreter.getGlobalContext().defineConstant("simulation", simulationBridge.createSimulationObject());
        }
    }

    public void registerInstrumentationBridge() {
        if (projectModel != null && irBridge != null) {
            instrumentationBridge = new InstrumentationBridge(interpreter, projectModel, irBridge);
            if (logCallback != null) {
                instrumentationBridge.setLogCallback(logCallback);
            }
            interpreter.getGlobalContext().defineConstant("instrument", instrumentationBridge.createInstrumentObject());
        } else if (projectModel != null) {
            IRBridge tempIrBridge = new IRBridge(interpreter);
            instrumentationBridge = new InstrumentationBridge(interpreter, projectModel, tempIrBridge);
            if (logCallback != null) {
                instrumentationBridge.setLogCallback(logCallback);
            }
            interpreter.getGlobalContext().defineConstant("instrument", instrumentationBridge.createInstrumentObject());
        }
    }

    public void registerPatternBridge() {
        if (projectModel != null) {
            patternBridge = new PatternBridge(projectModel);
            if (logCallback != null) {
                patternBridge.setLogCallback(logCallback);
            }
            interpreter.getGlobalContext().defineConstant("patterns", patternBridge.createPatternObject());
        }
    }

    public void registerTypeBridge() {
        if (projectModel != null) {
            typeBridge = new TypeBridge(projectModel);
            if (logCallback != null) {
                typeBridge.setLogCallback(logCallback);
            }
            interpreter.getGlobalContext().defineConstant("types", typeBridge.createTypesObject());
        }
    }

    public void registerStringBridge() {
        if (projectModel != null) {
            stringBridge = new StringBridge(projectModel);
            if (logCallback != null) {
                stringBridge.setLogCallback(logCallback);
            }
            interpreter.getGlobalContext().defineConstant("strings", stringBridge.createStringsObject());
        }
    }

    public void registerPipeline() {
        scriptPipeline = new ScriptPipeline(interpreter);
        if (logCallback != null) {
            scriptPipeline.setLogCallback(logCallback);
        }
        interpreter.getGlobalContext().defineConstant("pipeline", scriptPipeline.createPipelineObject());
    }

}
