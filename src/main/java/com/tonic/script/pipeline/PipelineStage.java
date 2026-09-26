package com.tonic.script.pipeline;

import com.tonic.script.engine.ScriptFunction;
import com.tonic.script.engine.ScriptValue;
import lombok.Getter;

/** One named stage of a script pipeline: its action and, once run, its status, result, error and timing. */
@Getter
public class PipelineStage
{

    private final String name;
    private final ScriptFunction action;
    private StageStatus status = StageStatus.PENDING;
    private ScriptValue result;
    private String error;
    private long executionTimeMs;

    /**
     * Creates a pending stage.
     *
     * @param name the stage name
     * @param action the script function the stage runs
     */
    public PipelineStage(String name, ScriptFunction action)
    {
        this.name = name;
        this.action = action;
    }

    /**
     * Sets the stage status.
     *
     * @param status the new status
     */
    public void setStatus(StageStatus status)
    {
        this.status = status;
    }

    /**
     * Sets the value the stage produced.
     *
     * @param result the action's return value
     */
    public void setResult(ScriptValue result)
    {
        this.result = result;
    }

    /**
     * Sets why the stage failed.
     *
     * @param error the error message
     */
    public void setError(String error)
    {
        this.error = error;
    }

    /**
     * Sets how long the stage ran.
     *
     * @param executionTimeMs the run time in milliseconds
     */
    public void setExecutionTimeMs(long executionTimeMs)
    {
        this.executionTimeMs = executionTimeMs;
    }

    /** Where a stage is in its run: pending, running, completed, failed or skipped. */
    public enum StageStatus
    {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        SKIPPED
    }
}
