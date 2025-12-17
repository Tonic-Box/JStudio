package com.tonic.ui.script.bridge;

import com.tonic.ui.script.engine.ScriptFunction;
import com.tonic.ui.script.engine.ScriptInterpreter;
import com.tonic.ui.script.engine.ScriptValue;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * Bridge for collecting and managing script findings/results.
 * Exposes a 'results' global object for accumulating analysis findings.
 */
public class ResultsBridge {

    private final ScriptInterpreter interpreter;
    private final List<ScriptValue> findings = new ArrayList<>();
    private Consumer<String> logCallback;

    public ResultsBridge(ScriptInterpreter interpreter) {
        this.interpreter = interpreter;
    }

    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    private void log(String message) {
        if (logCallback != null) {
            logCallback.accept(message);
        }
    }

    public ScriptValue createResultsObject() {
        Map<String, ScriptValue> props = new HashMap<>();

        props.put("add", ScriptValue.function(
            ScriptFunction.native1("add", this::addFinding)
        ));

        props.put("count", ScriptValue.function(
            ScriptFunction.native0("count", this::getCount)
        ));

        props.put("clear", ScriptValue.function(
            ScriptFunction.native0("clear", this::clearFindings)
        ));

        props.put("all", ScriptValue.function(
            ScriptFunction.native0("all", this::getAllFindings)
        ));

        props.put("filter", ScriptValue.function(
            ScriptFunction.native1("filter", this::filterFindings)
        ));

        props.put("groupBy", ScriptValue.function(
            ScriptFunction.native1("groupBy", this::groupBy)
        ));

        props.put("sortBy", ScriptValue.function(
            ScriptFunction.native1("sortBy", this::sortBy)
        ));

        props.put("toTable", ScriptValue.function(
            ScriptFunction.native0("toTable", this::toTable)
        ));

        props.put("exportJson", ScriptValue.function(
            ScriptFunction.native1("exportJson", this::exportJson)
        ));

        props.put("exportCsv", ScriptValue.function(
            ScriptFunction.native1("exportCsv", this::exportCsv)
        ));

        props.put("unique", ScriptValue.function(
            ScriptFunction.native1("unique", this::uniqueBy)
        ));

        props.put("summary", ScriptValue.function(
            ScriptFunction.native0("summary", this::getSummary)
        ));

        return ScriptValue.object(props);
    }

    private ScriptValue addFinding(ScriptValue finding) {
        findings.add(finding);
        return ScriptValue.number(findings.size());
    }

    private ScriptValue getCount() {
        return ScriptValue.number(findings.size());
    }

    private ScriptValue clearFindings() {
        findings.clear();
        return ScriptValue.NULL;
    }

    private ScriptValue getAllFindings() {
        return ScriptValue.array(new ArrayList<>(findings));
    }

    private ScriptValue filterFindings(ScriptValue callback) {
        if (!callback.isFunction()) {
            throw new RuntimeException("filter requires a function argument");
        }
        ScriptFunction fn = callback.asFunction();
        List<ScriptValue> filtered = new ArrayList<>();
        for (int i = 0; i < findings.size(); i++) {
            List<ScriptValue> args = new ArrayList<>();
            args.add(findings.get(i));
            args.add(ScriptValue.number(i));
            if (fn.call(interpreter, args).asBoolean()) {
                filtered.add(findings.get(i));
            }
        }
        return ScriptValue.array(filtered);
    }

    private ScriptValue groupBy(ScriptValue keyExtractor) {
        if (!keyExtractor.isFunction()) {
            throw new RuntimeException("groupBy requires a function argument");
        }
        ScriptFunction fn = keyExtractor.asFunction();
        Map<String, List<ScriptValue>> groups = new LinkedHashMap<>();

        for (ScriptValue finding : findings) {
            List<ScriptValue> args = new ArrayList<>();
            args.add(finding);
            String key = fn.call(interpreter, args).asString();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(finding);
        }

        Map<String, ScriptValue> result = new HashMap<>();
        for (Map.Entry<String, List<ScriptValue>> entry : groups.entrySet()) {
            result.put(entry.getKey(), ScriptValue.array(entry.getValue()));
        }
        return ScriptValue.object(result);
    }

    private ScriptValue sortBy(ScriptValue keyExtractor) {
        if (!keyExtractor.isFunction()) {
            throw new RuntimeException("sortBy requires a function argument");
        }
        ScriptFunction fn = keyExtractor.asFunction();
        List<ScriptValue> sorted = new ArrayList<>(findings);

        sorted.sort((a, b) -> {
            List<ScriptValue> argsA = new ArrayList<>();
            argsA.add(a);
            ScriptValue keyA = fn.call(interpreter, argsA);

            List<ScriptValue> argsB = new ArrayList<>();
            argsB.add(b);
            ScriptValue keyB = fn.call(interpreter, argsB);

            return keyA.asString().compareTo(keyB.asString());
        });

        return ScriptValue.array(sorted);
    }

    private ScriptValue uniqueBy(ScriptValue keyExtractor) {
        if (!keyExtractor.isFunction()) {
            throw new RuntimeException("unique requires a function argument");
        }
        ScriptFunction fn = keyExtractor.asFunction();
        Set<String> seen = new HashSet<>();
        List<ScriptValue> unique = new ArrayList<>();

        for (ScriptValue finding : findings) {
            List<ScriptValue> args = new ArrayList<>();
            args.add(finding);
            String key = fn.call(interpreter, args).asString();
            if (seen.add(key)) {
                unique.add(finding);
            }
        }

        return ScriptValue.array(unique);
    }

    private ScriptValue toTable() {
        if (findings.isEmpty()) {
            return ScriptValue.string("(no results)");
        }

        Set<String> allKeys = new LinkedHashSet<>();
        for (ScriptValue finding : findings) {
            if (finding.isObject()) {
                allKeys.addAll(finding.asObject().keySet());
            }
        }

        if (allKeys.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < findings.size(); i++) {
                sb.append(i + 1).append(". ").append(findings.get(i).asString()).append("\n");
            }
            return ScriptValue.string(sb.toString());
        }

        List<String> keys = new ArrayList<>(allKeys);
        Map<String, Integer> widths = new HashMap<>();
        for (String key : keys) {
            widths.put(key, key.length());
        }
        for (ScriptValue finding : findings) {
            if (finding.isObject()) {
                Map<String, ScriptValue> obj = finding.asObject();
                for (String key : keys) {
                    ScriptValue val = obj.get(key);
                    int len = val != null ? val.asString().length() : 4;
                    widths.put(key, Math.max(widths.get(key), Math.min(len, 50)));
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            sb.append(String.format("%-" + widths.get(key) + "s", key)).append(" | ");
        }
        sb.append("\n");
        for (String key : keys) {
            sb.append("-".repeat(widths.get(key))).append("-+-");
        }
        sb.append("\n");

        for (ScriptValue finding : findings) {
            if (finding.isObject()) {
                Map<String, ScriptValue> obj = finding.asObject();
                for (String key : keys) {
                    ScriptValue val = obj.get(key);
                    String str = val != null ? val.asString() : "null";
                    if (str.length() > 50) str = str.substring(0, 47) + "...";
                    sb.append(String.format("%-" + widths.get(key) + "s", str)).append(" | ");
                }
                sb.append("\n");
            } else {
                sb.append(finding.asString()).append("\n");
            }
        }

        return ScriptValue.string(sb.toString());
    }

    private ScriptValue exportJson(ScriptValue pathValue) {
        String path = pathValue.asString();
        try (FileWriter writer = new FileWriter(path)) {
            writer.write("[\n");
            for (int i = 0; i < findings.size(); i++) {
                writer.write("  ");
                writer.write(toJson(findings.get(i)));
                if (i < findings.size() - 1) {
                    writer.write(",");
                }
                writer.write("\n");
            }
            writer.write("]\n");
            log("Exported " + findings.size() + " findings to " + path);
            return ScriptValue.bool(true);
        } catch (IOException e) {
            log("Export failed: " + e.getMessage());
            return ScriptValue.bool(false);
        }
    }

    private ScriptValue exportCsv(ScriptValue pathValue) {
        String path = pathValue.asString();

        if (findings.isEmpty()) {
            log("No findings to export");
            return ScriptValue.bool(false);
        }

        Set<String> allKeys = new LinkedHashSet<>();
        for (ScriptValue finding : findings) {
            if (finding.isObject()) {
                allKeys.addAll(finding.asObject().keySet());
            }
        }

        try (FileWriter writer = new FileWriter(path)) {
            List<String> keys = new ArrayList<>(allKeys);
            writer.write(String.join(",", keys) + "\n");

            for (ScriptValue finding : findings) {
                if (finding.isObject()) {
                    Map<String, ScriptValue> obj = finding.asObject();
                    List<String> values = new ArrayList<>();
                    for (String key : keys) {
                        ScriptValue val = obj.get(key);
                        String str = val != null ? val.asString() : "";
                        str = str.replace("\"", "\"\"");
                        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
                            str = "\"" + str + "\"";
                        }
                        values.add(str);
                    }
                    writer.write(String.join(",", values) + "\n");
                }
            }
            log("Exported " + findings.size() + " findings to " + path);
            return ScriptValue.bool(true);
        } catch (IOException e) {
            log("Export failed: " + e.getMessage());
            return ScriptValue.bool(false);
        }
    }

    private ScriptValue getSummary() {
        Map<String, ScriptValue> summary = new HashMap<>();
        summary.put("total", ScriptValue.number(findings.size()));

        Map<String, Integer> typeCounts = new HashMap<>();
        for (ScriptValue finding : findings) {
            if (finding.isObject()) {
                ScriptValue typeVal = finding.asObject().get("type");
                String type = typeVal != null ? typeVal.asString() : "unknown";
                typeCounts.merge(type, 1, Integer::sum);
            }
        }

        Map<String, ScriptValue> byType = new HashMap<>();
        for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
            byType.put(entry.getKey(), ScriptValue.number(entry.getValue()));
        }
        summary.put("byType", ScriptValue.object(byType));

        return ScriptValue.object(summary);
    }

    private String toJson(ScriptValue value) {
        if (value.isNull()) {
            return "null";
        }
        if (value.isBoolean()) {
            return value.asBoolean() ? "true" : "false";
        }
        if (value.isNumber()) {
            double d = value.asNumber();
            if (d == (long) d) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }
        if (value.isString()) {
            return "\"" + escapeJson(value.asString()) + "\"";
        }
        if (value.isArray()) {
            List<ScriptValue> arr = value.asArray();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < arr.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(toJson(arr.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        if (value.isObject()) {
            Map<String, ScriptValue> obj = value.asObject();
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, ScriptValue> entry : obj.entrySet()) {
                if (!first) sb.append(", ");
                first = false;
                sb.append("\"").append(escapeJson(entry.getKey())).append("\": ");
                sb.append(toJson(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        return "\"" + escapeJson(value.asString()) + "\"";
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public List<ScriptValue> getFindings() {
        return new ArrayList<>(findings);
    }
}
