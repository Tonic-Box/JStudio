package com.tonic.ui.script.bridge;

import com.tonic.analysis.pattern.PatternSearch;
import com.tonic.analysis.pattern.Patterns;
import com.tonic.analysis.pattern.SearchResult;
import com.tonic.parser.ClassPool;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.script.engine.ScriptFunction;
import com.tonic.ui.script.engine.ScriptInterpreter;
import com.tonic.ui.script.engine.ScriptValue;

import java.util.*;
import java.util.function.Consumer;

/**
 * Bridge for bytecode pattern searching.
 * Exposes a 'patterns' global object for finding code patterns.
 */
public class PatternBridge {

    private final ScriptInterpreter interpreter;
    private final ProjectModel projectModel;
    private Consumer<String> logCallback;
    private int resultLimit = 100;

    public PatternBridge(ScriptInterpreter interpreter, ProjectModel projectModel) {
        this.interpreter = interpreter;
        this.projectModel = projectModel;
    }

    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    private void log(String message) {
        if (logCallback != null) {
            logCallback.accept(message);
        }
    }

    public ScriptValue createPatternObject() {
        Map<String, ScriptValue> props = new HashMap<>();

        props.put("setLimit", ScriptValue.function(
            ScriptFunction.native1("setLimit", this::setLimit)
        ));

        props.put("findMethodCalls", ScriptValue.function(
            ScriptFunction.native1("findMethodCalls", this::findMethodCalls)
        ));

        props.put("findFieldAccesses", ScriptValue.function(
            ScriptFunction.native1("findFieldAccesses", this::findFieldAccesses)
        ));

        props.put("findAllocations", ScriptValue.function(
            ScriptFunction.native1("findAllocations", this::findAllocations)
        ));

        props.put("findCasts", ScriptValue.function(
            ScriptFunction.native1("findCasts", this::findCasts)
        ));

        props.put("findInstanceOf", ScriptValue.function(
            ScriptFunction.native1("findInstanceOf", this::findInstanceOf)
        ));

        props.put("findNullChecks", ScriptValue.function(
            ScriptFunction.native0("findNullChecks", this::findNullChecks)
        ));

        props.put("findThrows", ScriptValue.function(
            ScriptFunction.native0("findThrows", this::findThrows)
        ));

        props.put("findAll", ScriptValue.function(
            ScriptFunction.native1("findAll", this::findAll)
        ));

        props.put("count", ScriptValue.function(
            ScriptFunction.native1("count", this::countPattern)
        ));

        return ScriptValue.object(props);
    }

    private ScriptValue setLimit(ScriptValue limitVal) {
        this.resultLimit = (int) limitVal.asNumber();
        return ScriptValue.NULL;
    }

    private PatternSearch createSearch() {
        ClassPool classPool = projectModel.getClassPool();
        if (classPool == null) {
            return null;
        }
        return new PatternSearch(classPool)
                .inAllClasses()
                .limit(resultLimit);
    }

    private ScriptValue findMethodCalls(ScriptValue patternVal) {
        PatternSearch search = createSearch();
        if (search == null) {
            log("No class pool available");
            return ScriptValue.array(new ArrayList<>());
        }

        try {
            List<SearchResult> results;
            if (patternVal.isNull() || patternVal.asString().isEmpty()) {
                results = search.findMethodCalls(Patterns.anyMethodCall());
            } else {
                String pattern = patternVal.asString();
                if (pattern.contains(".")) {
                    String[] parts = pattern.split("\\.", 2);
                    results = search.findMethodCalls(parts[0], parts[1]);
                } else {
                    results = search.findMethodCalls(pattern);
                }
            }
            log("Found " + results.size() + " method call matches");
            return wrapResults(results);
        } catch (Exception e) {
            log("Pattern search error: " + e.getMessage());
            return ScriptValue.array(new ArrayList<>());
        }
    }

    private ScriptValue findFieldAccesses(ScriptValue patternVal) {
        PatternSearch search = createSearch();
        if (search == null) {
            log("No class pool available");
            return ScriptValue.array(new ArrayList<>());
        }

        try {
            String pattern = patternVal.isNull() ? ".*" : patternVal.asString();
            if (pattern.isEmpty()) pattern = ".*";
            List<SearchResult> results = search.findFieldsByName(pattern);
            log("Found " + results.size() + " field access matches");
            return wrapResults(results);
        } catch (Exception e) {
            log("Pattern search error: " + e.getMessage());
            return ScriptValue.array(new ArrayList<>());
        }
    }

    private ScriptValue findAllocations(ScriptValue patternVal) {
        PatternSearch search = createSearch();
        if (search == null) {
            log("No class pool available");
            return ScriptValue.array(new ArrayList<>());
        }

        try {
            List<SearchResult> results;
            if (patternVal.isNull() || patternVal.asString().isEmpty()) {
                results = search.findAllocations();
            } else {
                results = search.findAllocations(patternVal.asString());
            }
            log("Found " + results.size() + " allocation matches");
            return wrapResults(results);
        } catch (Exception e) {
            log("Pattern search error: " + e.getMessage());
            return ScriptValue.array(new ArrayList<>());
        }
    }

    private ScriptValue findCasts(ScriptValue patternVal) {
        PatternSearch search = createSearch();
        if (search == null) {
            log("No class pool available");
            return ScriptValue.array(new ArrayList<>());
        }

        try {
            List<SearchResult> results;
            if (patternVal.isNull() || patternVal.asString().isEmpty()) {
                results = search.findCasts();
            } else {
                results = search.findCastsTo(patternVal.asString());
            }
            log("Found " + results.size() + " cast matches");
            return wrapResults(results);
        } catch (Exception e) {
            log("Pattern search error: " + e.getMessage());
            return ScriptValue.array(new ArrayList<>());
        }
    }

    private ScriptValue findInstanceOf(ScriptValue patternVal) {
        PatternSearch search = createSearch();
        if (search == null) {
            log("No class pool available");
            return ScriptValue.array(new ArrayList<>());
        }

        try {
            List<SearchResult> results;
            if (patternVal.isNull() || patternVal.asString().isEmpty()) {
                results = search.findInstanceOfChecks();
            } else {
                results = search.findInstanceOfChecks(patternVal.asString());
            }
            log("Found " + results.size() + " instanceof matches");
            return wrapResults(results);
        } catch (Exception e) {
            log("Pattern search error: " + e.getMessage());
            return ScriptValue.array(new ArrayList<>());
        }
    }

    private ScriptValue findNullChecks() {
        PatternSearch search = createSearch();
        if (search == null) {
            log("No class pool available");
            return ScriptValue.array(new ArrayList<>());
        }

        try {
            List<SearchResult> results = search.findNullChecks();
            log("Found " + results.size() + " null check matches");
            return wrapResults(results);
        } catch (Exception e) {
            log("Pattern search error: " + e.getMessage());
            return ScriptValue.array(new ArrayList<>());
        }
    }

    private ScriptValue findThrows() {
        PatternSearch search = createSearch();
        if (search == null) {
            log("No class pool available");
            return ScriptValue.array(new ArrayList<>());
        }

        try {
            List<SearchResult> results = search.findThrows();
            log("Found " + results.size() + " throw matches");
            return wrapResults(results);
        } catch (Exception e) {
            log("Pattern search error: " + e.getMessage());
            return ScriptValue.array(new ArrayList<>());
        }
    }

    private ScriptValue findAll(ScriptValue configVal) {
        PatternSearch search = createSearch();
        if (search == null) {
            log("No class pool available");
            return ScriptValue.array(new ArrayList<>());
        }

        if (!configVal.isObject()) {
            return ScriptValue.array(new ArrayList<>());
        }

        Map<String, ScriptValue> config = configVal.asObject();
        String type = config.containsKey("type") ? config.get("type").asString() : "methodCall";
        String pattern = config.containsKey("pattern") ? config.get("pattern").asString() : "";

        try {
            List<SearchResult> results;
            switch (type.toLowerCase()) {
                case "methodcall":
                case "method":
                    if (pattern.isEmpty()) {
                        results = search.findMethodCalls(Patterns.anyMethodCall());
                    } else if (pattern.contains(".")) {
                        String[] parts = pattern.split("\\.", 2);
                        results = search.findMethodCalls(parts[0], parts[1]);
                    } else {
                        results = search.findMethodCalls(pattern);
                    }
                    break;
                case "field":
                    results = search.findFieldsByName(pattern.isEmpty() ? ".*" : pattern);
                    break;
                case "allocation":
                case "new":
                    results = pattern.isEmpty() ? search.findAllocations() : search.findAllocations(pattern);
                    break;
                case "cast":
                    results = pattern.isEmpty() ? search.findCasts() : search.findCastsTo(pattern);
                    break;
                case "instanceof":
                    results = pattern.isEmpty() ? search.findInstanceOfChecks() : search.findInstanceOfChecks(pattern);
                    break;
                case "null":
                    results = search.findNullChecks();
                    break;
                case "throw":
                    results = search.findThrows();
                    break;
                default:
                    results = new ArrayList<>();
            }
            log("Found " + results.size() + " matches for type: " + type);
            return wrapResults(results);
        } catch (Exception e) {
            log("Pattern search error: " + e.getMessage());
            return ScriptValue.array(new ArrayList<>());
        }
    }

    private ScriptValue countPattern(ScriptValue configVal) {
        ScriptValue results = findAll(configVal);
        return ScriptValue.number(results.asArray().size());
    }

    private ScriptValue wrapResults(List<SearchResult> results) {
        List<ScriptValue> list = new ArrayList<>();
        for (SearchResult result : results) {
            Map<String, ScriptValue> props = new HashMap<>();

            if (result.getClassFile() != null) {
                props.put("className", ScriptValue.string(result.getClassFile().getClassName()));
            }
            if (result.getMethod() != null) {
                props.put("methodName", ScriptValue.string(result.getMethod().getName()));
                props.put("methodDesc", ScriptValue.string(result.getMethod().getDesc()));
            }
            if (result.getDescription() != null) {
                props.put("description", ScriptValue.string(result.getDescription()));
            }

            list.add(ScriptValue.object(props));
        }
        return ScriptValue.array(list);
    }
}
