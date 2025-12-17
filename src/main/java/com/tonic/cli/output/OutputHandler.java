package com.tonic.cli.output;

import com.tonic.cli.engine.ExecutionResult;
import com.tonic.plugin.result.Finding;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public abstract class OutputHandler {

    protected final File outputFile;

    protected OutputHandler(File outputFile) {
        this.outputFile = outputFile;
    }

    public abstract void writeResult(ExecutionResult result) throws IOException;

    public static OutputHandler forFormat(OutputFormat format, File outputFile) {
        switch (format) {
            case JSON:
                return new JsonOutputHandler(outputFile);
            case CSV:
                return new CsvOutputHandler(outputFile);
            case TEXT:
            default:
                return new TextOutputHandler(outputFile);
        }
    }

    protected PrintWriter getWriter() throws IOException {
        if (outputFile != null) {
            return new PrintWriter(new FileWriter(outputFile));
        }
        return new PrintWriter(System.out) {
            @Override
            public void close() {
                flush();
            }
        };
    }

    private static class TextOutputHandler extends OutputHandler {
        TextOutputHandler(File outputFile) {
            super(outputFile);
        }

        @Override
        public void writeResult(ExecutionResult result) throws IOException {
            try (PrintWriter out = getWriter()) {
                out.println("=== Execution Result ===");
                out.println("Status: " + (result.isSuccess() ? "SUCCESS" : "FAILED"));
                out.println("Duration: " + result.getDurationMs() + "ms");
                out.println("Classes processed: " + result.getClassesProcessed());
                out.println("Methods processed: " + result.getMethodsProcessed());
                out.println();

                if (!result.getFindings().isEmpty()) {
                    out.println("=== Findings (" + result.getFindingsCount() + ") ===");
                    for (Finding finding : result.getFindings()) {
                        out.println();
                        out.println("[" + finding.getSeverity() + "] " + finding.getCategory());
                        out.println("  " + finding.getMessage());
                        if (finding.getLocation() != null) {
                            out.println("  Location: " + finding.getLocation());
                        }
                    }
                }

                if (result.getSummary() != null && !result.getSummary().isEmpty()) {
                    out.println();
                    out.println("=== Summary ===");
                    out.println(result.getSummary());
                }

                if (result.getErrorMessage() != null) {
                    out.println();
                    out.println("Error: " + result.getErrorMessage());
                }
            }
        }
    }

    private static class JsonOutputHandler extends OutputHandler {
        JsonOutputHandler(File outputFile) {
            super(outputFile);
        }

        @Override
        public void writeResult(ExecutionResult result) throws IOException {
            try (PrintWriter out = getWriter()) {
                out.println("{");
                out.println("  \"success\": " + result.isSuccess() + ",");
                out.println("  \"durationMs\": " + result.getDurationMs() + ",");
                out.println("  \"classesProcessed\": " + result.getClassesProcessed() + ",");
                out.println("  \"methodsProcessed\": " + result.getMethodsProcessed() + ",");
                out.println("  \"findingsCount\": " + result.getFindingsCount() + ",");

                if (result.getErrorMessage() != null) {
                    out.println("  \"error\": \"" + escapeJson(result.getErrorMessage()) + "\",");
                }

                out.println("  \"findings\": [");
                int i = 0;
                for (Finding finding : result.getFindings()) {
                    out.print("    {");
                    out.print("\"severity\": \"" + finding.getSeverity() + "\", ");
                    out.print("\"category\": \"" + escapeJson(finding.getCategory()) + "\", ");
                    out.print("\"message\": \"" + escapeJson(finding.getMessage()) + "\"");
                    if (finding.getLocation() != null) {
                        out.print(", \"location\": \"" + escapeJson(finding.getLocation().toString()) + "\"");
                    }
                    out.print("}");
                    if (++i < result.getFindingsCount()) {
                        out.println(",");
                    } else {
                        out.println();
                    }
                }
                out.println("  ]");
                out.println("}");
            }
        }

        private String escapeJson(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }

    private static class CsvOutputHandler extends OutputHandler {
        CsvOutputHandler(File outputFile) {
            super(outputFile);
        }

        @Override
        public void writeResult(ExecutionResult result) throws IOException {
            try (PrintWriter out = getWriter()) {
                out.println("severity,category,message,className,methodName,lineNumber");

                for (Finding finding : result.getFindings()) {
                    out.print(finding.getSeverity());
                    out.print(",");
                    out.print(escapeCsv(finding.getCategory()));
                    out.print(",");
                    out.print(escapeCsv(finding.getMessage()));
                    out.print(",");

                    if (finding.getLocation() != null) {
                        out.print(escapeCsv(finding.getLocation().getClassName()));
                        out.print(",");
                        out.print(escapeCsv(finding.getLocation().getMethodName()));
                        out.print(",");
                        out.print(finding.getLocation().getLineNumber());
                    } else {
                        out.print(",,,");
                    }
                    out.println();
                }
            }
        }

        private String escapeCsv(String s) {
            if (s == null) return "";
            if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
                return "\"" + s.replace("\"", "\"\"") + "\"";
            }
            return s;
        }
    }
}
