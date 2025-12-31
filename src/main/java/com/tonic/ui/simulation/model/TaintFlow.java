package com.tonic.ui.simulation.model;

import com.tonic.analysis.ssa.ir.IRInstruction;
import lombok.Getter;
import java.util.Collections;
import java.util.List;

@Getter
public class TaintFlow extends SimulationFinding {

    private final String sourceDescription;
    private final String sinkDescription;
    private final List<String> flowPath;
    private final int blockId;
    private final TaintCategory category;

    public enum TaintCategory {
        SQL_INJECTION,
        COMMAND_INJECTION,
        PATH_TRAVERSAL,
        CRYPTO_LEAK,
        FILE_WRITE,
        NETWORK_OUTPUT,
        GENERAL
    }

    public TaintFlow(String className, String methodName, String methodDesc,
                     IRInstruction sinkInstr, String sourceDescription,
                     String sinkDescription, List<String> flowPath,
                     TaintCategory category) {
        super(className, methodName, methodDesc, FindingType.TAINTED_VALUE,
                getSeverityForCategory(category),
                sinkInstr != null && sinkInstr.getBlock() != null
                        ? sinkInstr.getBlock().getBytecodeOffset() : -1);
        this.sourceDescription = sourceDescription;
        this.sinkDescription = sinkDescription;
        this.flowPath = flowPath != null
                ? List.copyOf(flowPath)
                : Collections.emptyList();
        this.blockId = sinkInstr != null && sinkInstr.getBlock() != null
                ? sinkInstr.getBlock().getId() : -1;
        this.category = category;
    }

    private static Severity getSeverityForCategory(TaintCategory category) {
        switch (category) {
            case SQL_INJECTION:
            case COMMAND_INJECTION:
                return Severity.CRITICAL;
            case PATH_TRAVERSAL:
            case CRYPTO_LEAK:
                return Severity.HIGH;
            case FILE_WRITE:
            case NETWORK_OUTPUT:
                return Severity.MEDIUM;
            default:
                return Severity.LOW;
        }
    }

    @Override
    public String getTitle() {
        return "Taint Flow: " + category.name().replace("_", " ");
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Potentially tainted data flows from a source to a sensitive sink.\n\n");
        sb.append("Source: ").append(sourceDescription).append("\n");
        sb.append("Sink: ").append(sinkDescription).append("\n");
        sb.append("Category: ").append(category.name().replace("_", " ")).append("\n\n");

        if (!flowPath.isEmpty()) {
            sb.append("Flow Path:\n");
            for (int i = 0; i < flowPath.size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(flowPath.get(i)).append("\n");
            }
        }

        return sb.toString();
    }

    @Override
    public String getRecommendation() {
        switch (category) {
            case SQL_INJECTION:
                return "Use parameterized queries (prepared statements) instead of string concatenation. " +
                        "Validate and sanitize all user inputs before using in SQL queries.";
            case COMMAND_INJECTION:
                return "Avoid passing user input directly to command execution. " +
                        "Use allowlists for permitted commands or sanitize inputs thoroughly.";
            case PATH_TRAVERSAL:
                return "Validate file paths against an allowlist of permitted directories. " +
                        "Normalize paths and check they don't escape expected boundaries.";
            case CRYPTO_LEAK:
                return "Ensure sensitive data (keys, passwords) are not logged, displayed, or transmitted insecurely. " +
                        "Use secure storage mechanisms for cryptographic material.";
            case FILE_WRITE:
                return "Validate file paths and contents before writing. " +
                        "Ensure user input cannot control file locations or overwrite sensitive files.";
            case NETWORK_OUTPUT:
                return "Sanitize data before sending over the network. " +
                        "Be cautious of information disclosure through network responses.";
            default:
                return "Review this data flow to ensure user-controlled input is properly validated " +
                        "and sanitized before reaching the sink.";
        }
    }

    @Override
    public String toString() {
        return "TaintFlow[" + sourceDescription + " -> " + sinkDescription +
                ", category=" + category + "]";
    }
}
