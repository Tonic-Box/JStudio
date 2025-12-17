package com.tonic.cli.engine;

import com.tonic.plugin.result.Finding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExecutionResult {

    private final boolean success;
    private final int classesProcessed;
    private final int methodsProcessed;
    private final long durationMs;
    private final String summary;
    private final List<Finding> findings;
    private final String errorMessage;

    private ExecutionResult(Builder builder) {
        this.success = builder.success;
        this.classesProcessed = builder.classesProcessed;
        this.methodsProcessed = builder.methodsProcessed;
        this.durationMs = builder.durationMs;
        this.summary = builder.summary;
        this.findings = Collections.unmodifiableList(builder.findings);
        this.errorMessage = builder.errorMessage;
    }

    public boolean isSuccess() { return success; }
    public int getClassesProcessed() { return classesProcessed; }
    public int getMethodsProcessed() { return methodsProcessed; }
    public long getDurationMs() { return durationMs; }
    public String getSummary() { return summary; }
    public List<Finding> getFindings() { return findings; }
    public String getErrorMessage() { return errorMessage; }
    public int getFindingsCount() { return findings.size(); }

    public static ExecutionResult success(int classes, int methods, long durationMs,
                                         String summary, List<Finding> findings) {
        return new Builder()
            .success(true)
            .classesProcessed(classes)
            .methodsProcessed(methods)
            .durationMs(durationMs)
            .summary(summary)
            .findings(findings)
            .build();
    }

    public static ExecutionResult failure(String errorMessage) {
        return new Builder()
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }

    public static class Builder {
        private boolean success;
        private int classesProcessed;
        private int methodsProcessed;
        private long durationMs;
        private String summary = "";
        private List<Finding> findings = new ArrayList<>();
        private String errorMessage;

        public Builder success(boolean success) { this.success = success; return this; }
        public Builder classesProcessed(int count) { this.classesProcessed = count; return this; }
        public Builder methodsProcessed(int count) { this.methodsProcessed = count; return this; }
        public Builder durationMs(long ms) { this.durationMs = ms; return this; }
        public Builder summary(String summary) { this.summary = summary; return this; }
        public Builder findings(List<Finding> findings) { this.findings = new ArrayList<>(findings); return this; }
        public Builder errorMessage(String msg) { this.errorMessage = msg; return this; }

        public ExecutionResult build() {
            return new ExecutionResult(this);
        }
    }
}
