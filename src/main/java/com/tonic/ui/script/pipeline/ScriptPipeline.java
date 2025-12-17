package com.tonic.ui.script.pipeline;

import com.tonic.ui.script.engine.ScriptFunction;
import com.tonic.ui.script.engine.ScriptInterpreter;
import com.tonic.ui.script.engine.ScriptValue;

import java.util.*;
import java.util.function.Consumer;

/**
 * Multi-stage pipeline for script execution.
 * Allows defining and running sequential analysis workflows.
 */
public class ScriptPipeline {

    private final ScriptInterpreter interpreter;
    private final List<PipelineStage> stages = new ArrayList<>();
    private Consumer<String> logCallback;
    private boolean stopOnError = true;
    private ScriptValue context;
    private long totalExecutionTimeMs;

    public ScriptPipeline(ScriptInterpreter interpreter) {
        this.interpreter = interpreter;
        this.context = ScriptValue.object(new HashMap<>());
    }

    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    private void log(String message) {
        if (logCallback != null) {
            logCallback.accept(message);
        }
    }

    public ScriptValue createPipelineObject() {
        Map<String, ScriptValue> props = new HashMap<>();

        props.put("stage", ScriptValue.function(
            ScriptFunction.native2("stage", this::addStage)
        ));

        props.put("run", ScriptValue.function(
            ScriptFunction.native0("run", this::runPipeline)
        ));

        props.put("runAsync", ScriptValue.function(
            ScriptFunction.native0("runAsync", this::runPipelineAsync)
        ));

        props.put("clear", ScriptValue.function(
            ScriptFunction.native0("clear", this::clearStages)
        ));

        props.put("getStages", ScriptValue.function(
            ScriptFunction.native0("getStages", this::getStagesInfo)
        ));

        props.put("getStatus", ScriptValue.function(
            ScriptFunction.native0("getStatus", this::getPipelineStatus)
        ));

        props.put("setStopOnError", ScriptValue.function(
            ScriptFunction.native1("setStopOnError", this::setStopOnErrorValue)
        ));

        props.put("setContext", ScriptValue.function(
            ScriptFunction.native1("setContext", this::setContextValue)
        ));

        props.put("getContext", ScriptValue.function(
            ScriptFunction.native0("getContext", () -> context)
        ));

        props.put("getResults", ScriptValue.function(
            ScriptFunction.native0("getResults", this::getResults)
        ));

        props.put("getTotalTime", ScriptValue.function(
            ScriptFunction.native0("getTotalTime", () -> ScriptValue.number(totalExecutionTimeMs))
        ));

        props.put("create", ScriptValue.function(
            ScriptFunction.native0("create", this::createNewPipeline)
        ));

        return ScriptValue.object(props);
    }

    private ScriptValue addStage(ScriptValue nameVal, ScriptValue actionVal) {
        String name = nameVal.asString();
        if (!actionVal.isFunction()) {
            log("Stage action must be a function");
            return ScriptValue.bool(false);
        }

        stages.add(new PipelineStage(name, actionVal.asFunction()));
        log("Added stage: " + name);
        return ScriptValue.object(createSelfReference());
    }

    private Map<String, ScriptValue> createSelfReference() {
        Map<String, ScriptValue> self = new HashMap<>();
        self.put("stage", ScriptValue.function(
            ScriptFunction.native2("stage", this::addStage)
        ));
        self.put("run", ScriptValue.function(
            ScriptFunction.native0("run", this::runPipeline)
        ));
        return self;
    }

    private ScriptValue runPipeline() {
        if (stages.isEmpty()) {
            log("No stages to run");
            return ScriptValue.bool(false);
        }

        log("Starting pipeline with " + stages.size() + " stages");
        long startTime = System.currentTimeMillis();
        ScriptValue lastResult = ScriptValue.NULL;

        for (int i = 0; i < stages.size(); i++) {
            PipelineStage stage = stages.get(i);

            log("Running stage " + (i + 1) + "/" + stages.size() + ": " + stage.getName());
            stage.setStatus(PipelineStage.StageStatus.RUNNING);

            long stageStart = System.currentTimeMillis();

            try {
                List<ScriptValue> args = new ArrayList<>();
                args.add(lastResult);
                args.add(context);

                ScriptValue result = stage.getAction().call(interpreter, args);

                stage.setResult(result);
                stage.setStatus(PipelineStage.StageStatus.COMPLETED);
                stage.setExecutionTimeMs(System.currentTimeMillis() - stageStart);

                lastResult = result;
                log("Stage '" + stage.getName() + "' completed in " + stage.getExecutionTimeMs() + "ms");

            } catch (Exception e) {
                stage.setStatus(PipelineStage.StageStatus.FAILED);
                stage.setError(e.getMessage());
                stage.setExecutionTimeMs(System.currentTimeMillis() - stageStart);

                log("Stage '" + stage.getName() + "' failed: " + e.getMessage());

                if (stopOnError) {
                    for (int j = i + 1; j < stages.size(); j++) {
                        stages.get(j).setStatus(PipelineStage.StageStatus.SKIPPED);
                    }
                    break;
                }
            }
        }

        totalExecutionTimeMs = System.currentTimeMillis() - startTime;
        log("Pipeline completed in " + totalExecutionTimeMs + "ms");

        return lastResult;
    }

    private ScriptValue runPipelineAsync() {
        Thread pipelineThread = new Thread(() -> {
            runPipeline();
        });
        pipelineThread.setName("ScriptPipeline");
        pipelineThread.start();
        return ScriptValue.bool(true);
    }

    private ScriptValue clearStages() {
        stages.clear();
        context = ScriptValue.object(new HashMap<>());
        totalExecutionTimeMs = 0;
        log("Pipeline cleared");
        return ScriptValue.NULL;
    }

    private ScriptValue getStagesInfo() {
        List<ScriptValue> stageInfos = new ArrayList<>();
        for (int i = 0; i < stages.size(); i++) {
            PipelineStage stage = stages.get(i);
            Map<String, ScriptValue> info = new HashMap<>();
            info.put("index", ScriptValue.number(i));
            info.put("name", ScriptValue.string(stage.getName()));
            info.put("status", ScriptValue.string(stage.getStatus().name()));
            info.put("executionTimeMs", ScriptValue.number(stage.getExecutionTimeMs()));
            if (stage.getError() != null) {
                info.put("error", ScriptValue.string(stage.getError()));
            }
            stageInfos.add(ScriptValue.object(info));
        }
        return ScriptValue.array(stageInfos);
    }

    private ScriptValue getPipelineStatus() {
        Map<String, ScriptValue> status = new HashMap<>();

        int pending = 0, running = 0, completed = 0, failed = 0, skipped = 0;
        for (PipelineStage stage : stages) {
            switch (stage.getStatus()) {
                case PENDING: pending++; break;
                case RUNNING: running++; break;
                case COMPLETED: completed++; break;
                case FAILED: failed++; break;
                case SKIPPED: skipped++; break;
            }
        }

        status.put("totalStages", ScriptValue.number(stages.size()));
        status.put("pending", ScriptValue.number(pending));
        status.put("running", ScriptValue.number(running));
        status.put("completed", ScriptValue.number(completed));
        status.put("failed", ScriptValue.number(failed));
        status.put("skipped", ScriptValue.number(skipped));
        status.put("totalTimeMs", ScriptValue.number(totalExecutionTimeMs));

        String overallStatus;
        if (failed > 0) {
            overallStatus = "FAILED";
        } else if (running > 0) {
            overallStatus = "RUNNING";
        } else if (pending > 0) {
            overallStatus = "PENDING";
        } else if (completed == stages.size()) {
            overallStatus = "COMPLETED";
        } else {
            overallStatus = "UNKNOWN";
        }
        status.put("status", ScriptValue.string(overallStatus));

        return ScriptValue.object(status);
    }

    private ScriptValue setStopOnErrorValue(ScriptValue val) {
        this.stopOnError = val.asBoolean();
        return ScriptValue.NULL;
    }

    private ScriptValue setContextValue(ScriptValue val) {
        this.context = val;
        return ScriptValue.NULL;
    }

    private ScriptValue getResults() {
        Map<String, ScriptValue> results = new HashMap<>();
        for (PipelineStage stage : stages) {
            if (stage.getResult() != null) {
                results.put(stage.getName(), stage.getResult());
            }
        }
        return ScriptValue.object(results);
    }

    private ScriptValue createNewPipeline() {
        ScriptPipeline newPipeline = new ScriptPipeline(interpreter);
        newPipeline.setLogCallback(logCallback);
        return newPipeline.createPipelineObject();
    }

    public List<PipelineStage> getStages() {
        return stages;
    }
}
