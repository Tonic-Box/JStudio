package com.tonic.script.bridge;

import com.tonic.model.ProjectModel;
import com.tonic.script.engine.ScriptInterpreter;
import com.tonic.script.engine.ScriptValue;

import java.util.function.Consumer;

/** The base of script bridges that expose one project-backed global object, with an optional log callback. */
public abstract class AbstractBridge
{

    protected final ProjectModel projectModel;
    protected final ScriptInterpreter interpreter;
    protected Consumer<String> logCallback;

    protected AbstractBridge(ProjectModel projectModel)
    {
        this(null, projectModel);
    }

    protected AbstractBridge(ScriptInterpreter interpreter, ProjectModel projectModel)
    {
        this.interpreter = interpreter;
        this.projectModel = projectModel;
    }

    /**
     * Sets where the bridge's log messages go.
     *
     * @param callback receives each log message, or null to drop them
     */
    public void setLogCallback(Consumer<String> callback)
    {
        this.logCallback = callback;
    }

    protected void log(String message)
    {
        if (logCallback != null)
        {
            logCallback.accept(message);
        }
    }

    /**
     * Builds the object this bridge exposes to scripts.
     *
     * @return the object, ready to bind as a global
     */
    public abstract ScriptValue createBridgeObject();
}
