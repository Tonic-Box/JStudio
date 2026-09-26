package com.tonic.script.bridge;

import com.tonic.live.LiveSession;
import com.tonic.model.ProjectModel;
import com.tonic.script.engine.ScriptInterpreter;
import com.tonic.script.pipeline.ScriptPipeline;
import lombok.Getter;

import java.util.function.Consumer;

/** Creates the project and analysis bridges and binds each as a global constant in an interpreter. */
public class BridgeRegistry
{

    private final ScriptInterpreter interpreter;
    private final ProjectModel projectModel;
    private Consumer<String> logCallback;

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
    @Getter
    private LiveBridge liveBridge;

    /**
     * Creates a registry that has registered nothing yet.
     *
     * @param interpreter the interpreter to bind globals in
     * @param projectModel the project the bridges read, or null to skip the project-backed bridges
     */
    public BridgeRegistry(ScriptInterpreter interpreter, ProjectModel projectModel)
    {
        this.interpreter = interpreter;
        this.projectModel = projectModel;
    }

    /**
     * Sets the log callback handed to bridges registered after this call.
     *
     * @param log receives each log message, or null for none
     */
    public void setLogCallback(Consumer<String> log)
    {
        this.logCallback = log;
    }

    /** Registers every bridge except live: results and pipeline always, and the project-backed bridges when a project is set. */
    public void registerAll()
    {
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

    /**
     * Binds the live global to an attached JVM; does nothing when the session is null.
     *
     * @param session the live session, or null
     */
    public void registerLiveBridge(LiveSession session)
    {
        if (session != null)
        {
            liveBridge = new LiveBridge(interpreter, session);
            if (logCallback != null)
            {
                liveBridge.setLogCallback(logCallback);
            }
            interpreter.getGlobalContext().defineConstant("live", liveBridge.createBridgeObject());
        }
    }

    /** Binds the results global. */
    public void registerResultsBridge()
    {
        resultsBridge = new ResultsBridge(interpreter);
        if (logCallback != null)
        {
            resultsBridge.setLogCallback(logCallback);
        }
        interpreter.getGlobalContext().defineConstant("results", resultsBridge.createResultsObject());
    }

    /** Binds the project global when a project is set. */
    public void registerProjectBridge()
    {
        if (projectModel != null)
        {
            projectBridge = new ProjectBridge(interpreter, projectModel);
            if (logCallback != null)
            {
                projectBridge.setLogCallback(logCallback);
            }
            interpreter.getGlobalContext().defineConstant("project", projectBridge.createProjectObject());
        }
    }

    /** Binds the callgraph global when a project is set. */
    public void registerCallGraphBridge()
    {
        if (projectModel != null)
        {
            callGraphBridge = new CallGraphBridge(interpreter, projectModel);
            if (logCallback != null)
            {
                callGraphBridge.setLogCallback(logCallback);
            }
            interpreter.getGlobalContext().defineConstant("callgraph", callGraphBridge.createCallGraphObject());
        }
    }

    /** Binds the dataflow global when a project is set. */
    public void registerDataFlowBridge()
    {
        if (projectModel != null)
        {
            dataFlowBridge = new DataFlowBridge(projectModel);
            if (logCallback != null)
            {
                dataFlowBridge.setLogCallback(logCallback);
            }
            interpreter.getGlobalContext().defineConstant("dataflow", dataFlowBridge.createDataFlowObject());
        }
    }

    /** Binds the dependencies global when a project is set. */
    public void registerDependencyBridge()
    {
        if (projectModel != null)
        {
            dependencyBridge = new DependencyBridge(projectModel);
            if (logCallback != null)
            {
                dependencyBridge.setLogCallback(logCallback);
            }
            interpreter.getGlobalContext().defineConstant("dependencies", dependencyBridge.createDependencyObject());
        }
    }

    /** Binds the simulation global when a project is set. */
    public void registerSimulationBridge()
    {
        if (projectModel != null)
        {
            simulationBridge = new SimulationBridge(interpreter, projectModel);
            if (logCallback != null)
            {
                simulationBridge.setLogCallback(logCallback);
            }
            interpreter.getGlobalContext().defineConstant("simulation", simulationBridge.createSimulationObject());
        }
    }

    /** Binds the instrument global when a project is set. */
    public void registerInstrumentationBridge()
    {
        if (projectModel != null)
        {
            instrumentationBridge = new InstrumentationBridge(interpreter, projectModel);
            if (logCallback != null)
            {
                instrumentationBridge.setLogCallback(logCallback);
            }
            interpreter.getGlobalContext().defineConstant("instrument", instrumentationBridge.createInstrumentObject());
        }
    }

    /** Binds the patterns global when a project is set. */
    public void registerPatternBridge()
    {
        if (projectModel != null)
        {
            patternBridge = new PatternBridge(projectModel);
            if (logCallback != null)
            {
                patternBridge.setLogCallback(logCallback);
            }
            interpreter.getGlobalContext().defineConstant("patterns", patternBridge.createPatternObject());
        }
    }

    /** Binds the types global when a project is set. */
    public void registerTypeBridge()
    {
        if (projectModel != null)
        {
            typeBridge = new TypeBridge(projectModel);
            if (logCallback != null)
            {
                typeBridge.setLogCallback(logCallback);
            }
            interpreter.getGlobalContext().defineConstant("types", typeBridge.createTypesObject());
        }
    }

    /** Binds the strings global when a project is set. */
    public void registerStringBridge()
    {
        if (projectModel != null)
        {
            stringBridge = new StringBridge(projectModel);
            if (logCallback != null)
            {
                stringBridge.setLogCallback(logCallback);
            }
            interpreter.getGlobalContext().defineConstant("strings", stringBridge.createStringsObject());
        }
    }

    /** Binds the pipeline global. */
    public void registerPipeline()
    {
        scriptPipeline = new ScriptPipeline(interpreter);
        if (logCallback != null)
        {
            scriptPipeline.setLogCallback(logCallback);
        }
        interpreter.getGlobalContext().defineConstant("pipeline", scriptPipeline.createPipelineObject());
    }

}
