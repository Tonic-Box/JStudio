package com.tonic.ui.script.pipeline;

import com.tonic.ui.script.engine.ScriptFunction;
import com.tonic.ui.script.engine.ScriptValue;
import lombok.Getter;

/**
 * Represents a single stage in a script pipeline.
 */
@Getter
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

    public void setStatus(StageStatus status) {
        this.status = status;
    }

    public void setResult(ScriptValue result) {
        this.result = result;
    }

    public void setError(String error) {
        this.error = error;
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
