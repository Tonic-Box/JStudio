package com.tonic.plugin.api;

import lombok.Getter;

/** A plugin that analyses the project; its execute runs analyze over the whole project and discards the result. */
public interface AnalyzerPlugin extends Plugin
{

    /**
     * Analyses the given scope, typically reporting findings through the context's ResultCollector.
     *
     * @param scope how much of the project to analyse
     * @return the outcome and a summary
     */
    AnalysisResult analyze(AnalysisScope scope);

    @Override
    default void execute()
    {
        analyze(AnalysisScope.PROJECT);
    }

    /** How much of the project an analysis covers. */
    enum AnalysisScope
    {
        PROJECT,
        PACKAGE,
        CLASS,
        METHOD
    }

    /** The outcome of an analysis: whether it succeeded, how many findings it produced, how long it took and a summary. */
    @Getter
    final class AnalysisResult
    {
        private final boolean success;
        private final int findingsCount;
        private final long durationMs;
        private final String summary;

        /**
         * Creates a result.
         *
         * @param success whether the analysis completed
         * @param findingsCount how many findings it produced
         * @param durationMs how long it took, in milliseconds
         * @param summary a one-line summary, or the failure reason
         */
        public AnalysisResult(boolean success, int findingsCount, long durationMs, String summary)
        {
            this.success = success;
            this.findingsCount = findingsCount;
            this.durationMs = durationMs;
            this.summary = summary;
        }

        /**
         * Creates a successful result.
         *
         * @param findings how many findings the analysis produced
         * @param durationMs how long it took, in milliseconds
         * @param summary a one-line summary
         * @return the result
         */
        public static AnalysisResult success(int findings, long durationMs, String summary)
        {
            return new AnalysisResult(true, findings, durationMs, summary);
        }

        /**
         * Creates a failed result with no findings and zero duration.
         *
         * @param reason why the analysis failed; becomes the summary
         * @return the result
         */
        public static AnalysisResult failure(String reason)
        {
            return new AnalysisResult(false, 0, 0, reason);
        }
    }
}
