package com.tonic.plugin.api;

public interface AnalyzerPlugin extends Plugin {

    AnalysisResult analyze(AnalysisScope scope);

    @Override
    default void execute() {
        analyze(AnalysisScope.PROJECT);
    }

    enum AnalysisScope {
        PROJECT,
        PACKAGE,
        CLASS,
        METHOD
    }

    final class AnalysisResult {
        private final boolean success;
        private final int findingsCount;
        private final long durationMs;
        private final String summary;

        public AnalysisResult(boolean success, int findingsCount, long durationMs, String summary) {
            this.success = success;
            this.findingsCount = findingsCount;
            this.durationMs = durationMs;
            this.summary = summary;
        }

        public boolean isSuccess() { return success; }
        public int getFindingsCount() { return findingsCount; }
        public long getDurationMs() { return durationMs; }
        public String getSummary() { return summary; }

        public static AnalysisResult success(int findings, long durationMs, String summary) {
            return new AnalysisResult(true, findings, durationMs, summary);
        }

        public static AnalysisResult failure(String reason) {
            return new AnalysisResult(false, 0, 0, reason);
        }
    }
}
