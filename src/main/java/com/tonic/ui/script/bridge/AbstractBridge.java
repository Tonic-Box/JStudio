package com.tonic.ui.script.bridge;

import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.script.engine.ScriptInterpreter;
import com.tonic.ui.script.engine.ScriptValue;

import java.util.function.Consumer;

public abstract class AbstractBridge {

    protected final ProjectModel projectModel;
    protected final ScriptInterpreter interpreter;
    protected Consumer<String> logCallback;

    protected AbstractBridge(ProjectModel projectModel) {
        this(null, projectModel);
    }

    protected AbstractBridge(ScriptInterpreter interpreter, ProjectModel projectModel) {
        this.interpreter = interpreter;
        this.projectModel = projectModel;
    }

    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    protected void log(String message) {
        if (logCallback != null) {
            logCallback.accept(message);
        }
    }

    public abstract ScriptValue createBridgeObject();
}
