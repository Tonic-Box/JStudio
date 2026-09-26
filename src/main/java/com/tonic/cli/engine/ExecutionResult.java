package com.tonic.cli.engine;

import com.tonic.plugin.result.Finding;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/** The outcome of a headless run: success flag, class and method counts, duration, summary, findings and error message. */
@Getter
@Builder
public class ExecutionResult
{

    private final boolean success;
    private final int classesProcessed;
    private final int methodsProcessed;
    private final long durationMs;
    @Builder.Default
    private final String summary = "";
    @Singular
    private final List<Finding> findings;
    private final String errorMessage;

    /**
     * Counts the findings.
     *
     * @return the number of findings, or 0 when there is no findings list
     */
    public int getFindingsCount()
    {
        return findings != null ? findings.size() : 0;
    }

    /**
     * Creates a successful result.
     *
     * @param classes the number of classes processed
     * @param methods the number of methods processed
     * @param durationMs the run time in milliseconds
     * @param summary the human-readable summary
     * @param findings the findings the plugin reported
     * @return the result
     */
    public static ExecutionResult success(int classes, int methods, long durationMs, String summary, List<Finding> findings)
    {
        return ExecutionResult.builder()
                .success(true)
                .classesProcessed(classes)
                .methodsProcessed(methods)
                .durationMs(durationMs)
                .summary(summary)
                .findings(findings)
                .build();
    }

    /**
     * Creates a failed result with no counts or findings.
     *
     * @param errorMessage why the run failed
     * @return the result
     */
    public static ExecutionResult failure(String errorMessage)
    {
        return ExecutionResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
