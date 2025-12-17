package com.tonic.ui.script.pipeline;

import com.tonic.ui.script.engine.ScriptFunction;
import com.tonic.ui.script.engine.ScriptValue;

/**
 * Represents a single stage in a script pipeline.
 */
public class PipelineStage {

    private final String name;
    private final ScriptFunction action;
    private StageStatus status = StageStatus.PENDING;
    private ScriptValue result;
    private String error;
    private long executionTimeMs;

    public PipelineStage(String name, ScriptFunction action) {
        this.name = name;
        this.action = action;
    }

    public String getName() {
        return name;
    }

    public ScriptFunction getAction() {
        return action;
    }

    public StageStatus getStatus() {
        return status;
    }

    public void setStatus(StageStatus status) {
        this.status = status;
    }

    public ScriptValue getResult() {
        return result;
    }

    public void setResult(ScriptValue result) {
        this.result = result;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public enum StageStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        SKIPPED
    }
}
