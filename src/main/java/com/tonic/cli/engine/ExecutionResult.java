package com.tonic.cli.engine;

import com.tonic.plugin.result.Finding;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

@Getter
@Builder
public class ExecutionResult {

    private final boolean success;
    private final int classesProcessed;
    private final int methodsProcessed;
    private final long durationMs;
    @Builder.Default
    private final String summary = "";
    @Singular
    private final List<Finding> findings;
    private final String errorMessage;

    public int getFindingsCount() {
        return findings != null ? findings.size() : 0;
    }

    public static ExecutionResult success(int classes, int methods, long durationMs,
                                         String summary, List<Finding> findings) {
        return ExecutionResult.builder()
            .success(true)
            .classesProcessed(classes)
            .methodsProcessed(methods)
            .durationMs(durationMs)
            .summary(summary)
            .findings(findings)
            .build();
    }

    public static ExecutionResult failure(String errorMessage) {
        return ExecutionResult.builder()
            .success(false)
            .errorMessage(errorMessage)
            .build();
    }
}
