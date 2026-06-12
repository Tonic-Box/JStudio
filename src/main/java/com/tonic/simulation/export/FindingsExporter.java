package com.tonic.simulation.export;

import com.tonic.simulation.model.SimulationFinding;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

public class FindingsExporter {

    public static void exportToJson(List<SimulationFinding> findings, String filePath) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            writeJson(findings, writer);
        }
    }

    public static String toJsonString(List<SimulationFinding> findings) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"findings\": [\n");

        for (int i = 0; i < findings.size(); i++) {
            SimulationFinding finding = findings.get(i);
            sb.append("    {\n");
            sb.append("      \"type\": \"").append(escapeJson(finding.getType().name())).append("\",\n");
            sb.append("      \"severity\": \"").append(escapeJson(finding.getSeverity().name())).append("\",\n");
            sb.append("      \"class\": \"").append(escapeJson(finding.getClassName())).append("\",\n");
            sb.append("      \"method\": \"").append(escapeJson(finding.getMethodName())).append("\",\n");
            sb.append("      \"methodDesc\": \"").append(escapeJson(finding.getMethodDesc())).append("\",\n");
            sb.append("      \"title\": \"").append(escapeJson(finding.getTitle())).append("\",\n");
            sb.append("      \"description\": \"").append(escapeJson(finding.getDescription())).append("\",\n");
            sb.append("      \"recommendation\": \"").append(escapeJson(finding.getRecommendation())).append("\",\n");
            sb.append("      \"bytecodeOffset\": ").append(finding.getBytecodeOffset()).append("\n");
            sb.append("    }");

            if (i < findings.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }

        sb.append("  ],\n");
        sb.append("  \"summary\": {\n");
        sb.append("    \"totalFindings\": ").append(findings.size()).append(",\n");
        sb.append("    \"criticalCount\": ").append(countBySeverity(findings, SimulationFinding.Severity.CRITICAL)).append(",\n");
        sb.append("    \"highCount\": ").append(countBySeverity(findings, SimulationFinding.Severity.HIGH)).append(",\n");
        sb.append("    \"mediumCount\": ").append(countBySeverity(findings, SimulationFinding.Severity.MEDIUM)).append(",\n");
        sb.append("    \"lowCount\": ").append(countBySeverity(findings, SimulationFinding.Severity.LOW)).append(",\n");
        sb.append("    \"infoCount\": ").append(countBySeverity(findings, SimulationFinding.Severity.INFO)).append("\n");
        sb.append("  }\n");
        sb.append("}\n");

        return sb.toString();
    }

    private static void writeJson(List<SimulationFinding> findings, Writer writer) throws IOException {
        writer.write(toJsonString(findings));
    }

    private static int countBySeverity(List<SimulationFinding> findings, SimulationFinding.Severity severity) {
        return (int) findings.stream()
                .filter(f -> f.getSeverity() == severity)
                .count();
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public static void exportToHtml(List<SimulationFinding> findings, String filePath) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            writeHtml(findings, writer);
        }
    }

    private static void writeHtml(List<SimulationFinding> findings, Writer writer) throws IOException {
        writer.write("<!DOCTYPE html>\n");
        writer.write("<html>\n<head>\n");
        writer.write("<title>Simulation Analysis Report</title>\n");
        writer.write("<style>\n");
        writer.write("body { font-family: sans-serif; margin: 20px; background: #1e1e1e; color: #d4d4d4; }\n");
        writer.write("h1 { color: #569cd6; }\n");
        writer.write("table { border-collapse: collapse; width: 100%; margin-top: 20px; }\n");
        writer.write("th, td { border: 1px solid #3c3c3c; padding: 8px; text-align: left; }\n");
        writer.write("th { background: #252526; color: #569cd6; }\n");
        writer.write("tr:nth-child(even) { background: #2d2d2d; }\n");
        writer.write(".severity-critical { color: #f14c4c; font-weight: bold; }\n");
        writer.write(".severity-high { color: #f14c4c; }\n");
        writer.write(".severity-medium { color: #cca700; }\n");
        writer.write(".severity-low { color: #89d185; }\n");
        writer.write(".severity-info { color: #6a9955; }\n");
        writer.write(".summary { background: #252526; padding: 15px; margin: 20px 0; border-radius: 5px; }\n");
        writer.write("</style>\n");
        writer.write("</head>\n<body>\n");
        writer.write("<h1>Simulation Analysis Report</h1>\n");

        writer.write("<div class=\"summary\">\n");
        writer.write("<h2>Summary</h2>\n");
        writer.write("<p>Total Findings: " + findings.size() + "</p>\n");
        writer.write("<p>Critical: " + countBySeverity(findings, SimulationFinding.Severity.CRITICAL) + "</p>\n");
        writer.write("<p>High: " + countBySeverity(findings, SimulationFinding.Severity.HIGH) + "</p>\n");
        writer.write("<p>Medium: " + countBySeverity(findings, SimulationFinding.Severity.MEDIUM) + "</p>\n");
        writer.write("<p>Low: " + countBySeverity(findings, SimulationFinding.Severity.LOW) + "</p>\n");
        writer.write("<p>Info: " + countBySeverity(findings, SimulationFinding.Severity.INFO) + "</p>\n");
        writer.write("</div>\n");

        writer.write("<table>\n");
        writer.write("<tr><th>Type</th><th>Severity</th><th>Class</th><th>Method</th><th>Title</th></tr>\n");

        for (SimulationFinding finding : findings) {
            String severityClass = "severity-" + finding.getSeverity().name().toLowerCase();
            writer.write("<tr>\n");
            writer.write("<td>" + escapeHtml(finding.getType().name()) + "</td>\n");
            writer.write("<td class=\"" + severityClass + "\">" + finding.getSeverity().name() + "</td>\n");
            writer.write("<td>" + escapeHtml(getSimpleClassName(finding.getClassName())) + "</td>\n");
            writer.write("<td>" + escapeHtml(finding.getMethodName()) + "</td>\n");
            writer.write("<td>" + escapeHtml(finding.getTitle()) + "</td>\n");
            writer.write("</tr>\n");
        }

        writer.write("</table>\n");
        writer.write("</body>\n</html>\n");
    }

    private static String getSimpleClassName(String className) {
        if (className == null) return "";
        int lastSlash = className.lastIndexOf('/');
        return lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
