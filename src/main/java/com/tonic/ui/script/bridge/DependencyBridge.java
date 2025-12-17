package com.tonic.ui.script.bridge;

import com.tonic.analysis.dependency.Dependency;
import com.tonic.analysis.dependency.DependencyAnalyzer;
import com.tonic.analysis.dependency.DependencyNode;
import com.tonic.parser.ClassPool;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.script.engine.ScriptFunction;
import com.tonic.ui.script.engine.ScriptInterpreter;
import com.tonic.ui.script.engine.ScriptValue;

import java.util.*;
import java.util.function.Consumer;

/**
 * Bridge for class dependency analysis.
 * Exposes a 'dependencies' global object for analyzing class relationships.
 */
public class DependencyBridge {

    private final ScriptInterpreter interpreter;
    private final ProjectModel projectModel;
    private DependencyAnalyzer analyzer;
    private Consumer<String> logCallback;

    public DependencyBridge(ScriptInterpreter interpreter, ProjectModel projectModel) {
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

    public ScriptValue createDependencyObject() {
        Map<String, ScriptValue> props = new HashMap<>();

        props.put("build", ScriptValue.function(
            ScriptFunction.native0("build", this::build)
        ));

        props.put("isBuilt", ScriptValue.function(
            ScriptFunction.native0("isBuilt", () -> ScriptValue.bool(analyzer != null))
        ));

        props.put("getDependencies", ScriptValue.function(
            ScriptFunction.native1("getDependencies", this::getDependencies)
        ));

        props.put("getDependents", ScriptValue.function(
            ScriptFunction.native1("getDependents", this::getDependents)
        ));

        props.put("getTransitiveDeps", ScriptValue.function(
            ScriptFunction.native1("getTransitiveDeps", this::getTransitiveDeps)
        ));

        props.put("getTransitiveDependents", ScriptValue.function(
            ScriptFunction.native1("getTransitiveDependents", this::getTransitiveDependents)
        ));

        props.put("findCycles", ScriptValue.function(
            ScriptFunction.native0("findCycles", this::findCycles)
        ));

        props.put("dependsOn", ScriptValue.function(
            ScriptFunction.native2("dependsOn", this::dependsOn)
        ));

        props.put("transitivelyDependsOn", ScriptValue.function(
            ScriptFunction.native2("transitivelyDependsOn", this::transitivelyDependsOn)
        ));

        props.put("findLeafClasses", ScriptValue.function(
            ScriptFunction.native0("findLeafClasses", this::findLeafClasses)
        ));

        props.put("findRootClasses", ScriptValue.function(
            ScriptFunction.native0("findRootClasses", this::findRootClasses)
        ));

        props.put("getClassesInPackage", ScriptValue.function(
            ScriptFunction.native1("getClassesInPackage", this::getClassesInPackage)
        ));

        props.put("getNode", ScriptValue.function(
            ScriptFunction.native1("getNode", this::getNode)
        ));

        props.put("getAllNodes", ScriptValue.function(
            ScriptFunction.native0("getAllNodes", this::getAllNodes)
        ));

        props.put("classCount", ScriptValue.function(
            ScriptFunction.native0("classCount", () ->
                ScriptValue.number(analyzer != null ? analyzer.size() : 0))
        ));

        props.put("edgeCount", ScriptValue.function(
            ScriptFunction.native0("edgeCount", () ->
                ScriptValue.number(analyzer != null ? analyzer.edgeCount() : 0))
        ));

        return ScriptValue.object(props);
    }

    private ScriptValue build() {
        ClassPool classPool = projectModel.getClassPool();
        if (classPool == null) {
            log("No class pool available");
            return ScriptValue.bool(false);
        }

        log("Building dependency graph...");
        analyzer = new DependencyAnalyzer(classPool);
        log("Dependency graph built: " + analyzer.size() + " classes, " + analyzer.edgeCount() + " edges");
        return ScriptValue.bool(true);
    }

    private void ensureBuilt() {
        if (analyzer == null) {
            build();
        }
    }

    private ScriptValue getDependencies(ScriptValue classRef) {
        ensureBuilt();
        String className = parseClassName(classRef);
        if (className == null) return ScriptValue.array(new ArrayList<>());

        Set<String> deps = analyzer.getDependencies(className);
        return wrapClassNames(deps);
    }

    private ScriptValue getDependents(ScriptValue classRef) {
        ensureBuilt();
        String className = parseClassName(classRef);
        if (className == null) return ScriptValue.array(new ArrayList<>());

        Set<String> deps = analyzer.getDependents(className);
        return wrapClassNames(deps);
    }

    private ScriptValue getTransitiveDeps(ScriptValue classRef) {
        ensureBuilt();
        String className = parseClassName(classRef);
        if (className == null) return ScriptValue.array(new ArrayList<>());

        Set<String> deps = analyzer.getTransitiveDependencies(className);
        return wrapClassNames(deps);
    }

    private ScriptValue getTransitiveDependents(ScriptValue classRef) {
        ensureBuilt();
        String className = parseClassName(classRef);
        if (className == null) return ScriptValue.array(new ArrayList<>());

        Set<String> deps = analyzer.getTransitiveDependents(className);
        return wrapClassNames(deps);
    }

    private ScriptValue findCycles() {
        ensureBuilt();
        List<List<String>> cycles = analyzer.findCircularDependencies();

        List<ScriptValue> result = new ArrayList<>();
        for (List<String> cycle : cycles) {
            List<ScriptValue> cycleList = new ArrayList<>();
            for (String className : cycle) {
                cycleList.add(ScriptValue.string(className));
            }
            result.add(ScriptValue.array(cycleList));
        }

        if (!cycles.isEmpty()) {
            log("Found " + cycles.size() + " circular dependencies");
        }

        return ScriptValue.array(result);
    }

    private ScriptValue dependsOn(ScriptValue classA, ScriptValue classB) {
        ensureBuilt();
        String nameA = parseClassName(classA);
        String nameB = parseClassName(classB);
        if (nameA == null || nameB == null) return ScriptValue.bool(false);

        return ScriptValue.bool(analyzer.dependsOn(nameA, nameB));
    }

    private ScriptValue transitivelyDependsOn(ScriptValue classA, ScriptValue classB) {
        ensureBuilt();
        String nameA = parseClassName(classA);
        String nameB = parseClassName(classB);
        if (nameA == null || nameB == null) return ScriptValue.bool(false);

        return ScriptValue.bool(analyzer.transitivelyDependsOn(nameA, nameB));
    }

    private ScriptValue findLeafClasses() {
        ensureBuilt();
        return wrapClassNames(analyzer.findLeafClasses());
    }

    private ScriptValue findRootClasses() {
        ensureBuilt();
        return wrapClassNames(analyzer.findRootClasses());
    }

    private ScriptValue getClassesInPackage(ScriptValue packageRef) {
        ensureBuilt();
        String pkg = packageRef.asString();
        return wrapClassNames(analyzer.getClassesInPackage(pkg));
    }

    private ScriptValue getNode(ScriptValue classRef) {
        ensureBuilt();
        String className = parseClassName(classRef);
        if (className == null) return ScriptValue.NULL;

        DependencyNode node = analyzer.getNode(className);
        if (node == null) return ScriptValue.NULL;

        return wrapNode(node);
    }

    private ScriptValue getAllNodes() {
        ensureBuilt();
        List<ScriptValue> nodes = new ArrayList<>();
        for (DependencyNode node : analyzer.getPoolNodes()) {
            nodes.add(wrapNode(node));
        }
        return ScriptValue.array(nodes);
    }

    private String parseClassName(ScriptValue value) {
        if (value.isString()) {
            return value.asString().replace('.', '/');
        } else if (value.isObject()) {
            Map<String, ScriptValue> obj = value.asObject();
            if (obj.containsKey("className")) {
                return obj.get("className").asString();
            }
            if (obj.containsKey("name")) {
                return obj.get("name").asString().replace('.', '/');
            }
        }
        return null;
    }

    private ScriptValue wrapClassNames(Set<String> classNames) {
        List<ScriptValue> list = new ArrayList<>();
        for (String name : classNames) {
            list.add(ScriptValue.string(name));
        }
        return ScriptValue.array(list);
    }

    private ScriptValue wrapNode(DependencyNode node) {
        Map<String, ScriptValue> props = new HashMap<>();
        props.put("className", ScriptValue.string(node.getClassName()));
        props.put("isInPool", ScriptValue.bool(node.isInPool()));
        props.put("dependencyCount", ScriptValue.number(node.getDependencyCount()));
        props.put("dependentCount", ScriptValue.number(node.getDependentCount()));

        List<ScriptValue> outgoing = new ArrayList<>();
        for (Dependency dep : node.getOutgoingDependencies()) {
            Map<String, ScriptValue> depProps = new HashMap<>();
            depProps.put("from", ScriptValue.string(dep.getFromClass()));
            depProps.put("to", ScriptValue.string(dep.getToClass()));
            depProps.put("type", ScriptValue.string(dep.getType().name()));
            outgoing.add(ScriptValue.object(depProps));
        }
        props.put("outgoingDependencies", ScriptValue.array(outgoing));

        return ScriptValue.object(props);
    }

    public DependencyAnalyzer getAnalyzer() {
        return analyzer;
    }
}
