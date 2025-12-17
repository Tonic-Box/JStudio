package com.tonic.ui.script.bridge;

import com.tonic.analysis.dataflow.DataFlowEdge;
import com.tonic.analysis.dataflow.DataFlowGraph;
import com.tonic.analysis.dataflow.DataFlowNode;
import com.tonic.analysis.dataflow.DataFlowNodeType;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.script.engine.ScriptFunction;
import com.tonic.ui.script.engine.ScriptInterpreter;
import com.tonic.ui.script.engine.ScriptValue;

import java.util.*;
import java.util.function.Consumer;

/**
 * Bridge for data flow analysis.
 * Exposes a 'dataflow' global object for building and querying data flow graphs.
 */
public class DataFlowBridge {

    private final ScriptInterpreter interpreter;
    private final ProjectModel projectModel;
    private final Map<String, DataFlowGraph> graphCache = new HashMap<>();
    private DataFlowGraph currentGraph;
    private Consumer<String> logCallback;

    public DataFlowBridge(ScriptInterpreter interpreter, ProjectModel projectModel) {
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

    public ScriptValue createDataFlowObject() {
        Map<String, ScriptValue> props = new HashMap<>();

        props.put("build", ScriptValue.function(
            ScriptFunction.native1("build", this::build)
        ));

        props.put("getNodes", ScriptValue.function(
            ScriptFunction.native0("getNodes", this::getNodes)
        ));

        props.put("getEdges", ScriptValue.function(
            ScriptFunction.native0("getEdges", this::getEdges)
        ));

        props.put("getSources", ScriptValue.function(
            ScriptFunction.native0("getSources", this::getSources)
        ));

        props.put("getSinks", ScriptValue.function(
            ScriptFunction.native0("getSinks", this::getSinks)
        ));

        props.put("getReachable", ScriptValue.function(
            ScriptFunction.native1("getReachable", this::getReachable)
        ));

        props.put("getFlowingInto", ScriptValue.function(
            ScriptFunction.native1("getFlowingInto", this::getFlowingInto)
        ));

        props.put("getNodesByType", ScriptValue.function(
            ScriptFunction.native1("getNodesByType", this::getNodesByType)
        ));

        props.put("flowsTo", ScriptValue.function(
            ScriptFunction.native2("flowsTo", this::flowsTo)
        ));

        props.put("getParams", ScriptValue.function(
            ScriptFunction.native0("getParams", this::getParams)
        ));

        props.put("getInvokes", ScriptValue.function(
            ScriptFunction.native0("getInvokes", this::getInvokes)
        ));

        props.put("nodeCount", ScriptValue.function(
            ScriptFunction.native0("nodeCount", () ->
                ScriptValue.number(currentGraph != null ? currentGraph.getNodeCount() : 0))
        ));

        props.put("edgeCount", ScriptValue.function(
            ScriptFunction.native0("edgeCount", () ->
                ScriptValue.number(currentGraph != null ? currentGraph.getEdgeCount() : 0))
        ));

        props.put("taintAnalysis", ScriptValue.function(
            ScriptFunction.native2("taintAnalysis", this::taintAnalysis)
        ));

        return ScriptValue.object(props);
    }

    private ScriptValue build(ScriptValue methodRef) {
        String className;
        String methodName;
        String methodDesc = null;

        if (methodRef.isString()) {
            String s = methodRef.asString();
            int dotIdx = s.lastIndexOf('.');
            if (dotIdx > 0) {
                className = s.substring(0, dotIdx).replace('.', '/');
                String rest = s.substring(dotIdx + 1);
                int parenIdx = rest.indexOf('(');
                if (parenIdx > 0) {
                    methodName = rest.substring(0, parenIdx);
                    methodDesc = rest.substring(parenIdx);
                } else {
                    methodName = rest;
                }
            } else {
                log("Invalid method reference: " + s);
                return ScriptValue.bool(false);
            }
        } else if (methodRef.isObject()) {
            Map<String, ScriptValue> obj = methodRef.asObject();
            className = obj.containsKey("className") ? obj.get("className").asString() : null;
            methodName = obj.containsKey("name") ? obj.get("name").asString() : null;
            methodDesc = obj.containsKey("desc") ? obj.get("desc").asString() : null;
        } else {
            log("Invalid method reference type");
            return ScriptValue.bool(false);
        }

        if (className == null || methodName == null) {
            log("Missing class or method name");
            return ScriptValue.bool(false);
        }

        ClassEntryModel classEntry = projectModel.findClassByName(className);
        if (classEntry == null) {
            log("Class not found: " + className);
            return ScriptValue.bool(false);
        }

        MethodEntry targetMethod = null;
        for (MethodEntryModel mm : classEntry.getMethods()) {
            MethodEntry m = mm.getMethodEntry();
            if (m.getName().equals(methodName)) {
                if (methodDesc == null || m.getDesc().equals(methodDesc)) {
                    targetMethod = m;
                    break;
                }
            }
        }

        if (targetMethod == null || targetMethod.getCodeAttribute() == null) {
            log("Method not found or has no code: " + methodName);
            return ScriptValue.bool(false);
        }

        String cacheKey = className + "." + methodName + (methodDesc != null ? methodDesc : "");
        if (graphCache.containsKey(cacheKey)) {
            currentGraph = graphCache.get(cacheKey);
            log("Using cached data flow graph for " + cacheKey);
            return ScriptValue.bool(true);
        }

        try {
            SSA ssa = new SSA(classEntry.getClassFile().getConstPool());
            IRMethod irMethod = ssa.lift(targetMethod);

            if (irMethod == null || irMethod.getEntryBlock() == null) {
                log("Failed to lift method to IR");
                return ScriptValue.bool(false);
            }

            currentGraph = new DataFlowGraph(irMethod);
            currentGraph.build();
            graphCache.put(cacheKey, currentGraph);

            log("Built data flow graph: " + currentGraph.getNodeCount() + " nodes, " +
                currentGraph.getEdgeCount() + " edges");
            return ScriptValue.bool(true);
        } catch (Exception e) {
            log("Error building data flow graph: " + e.getMessage());
            return ScriptValue.bool(false);
        }
    }

    private ScriptValue getNodes() {
        if (currentGraph == null) return ScriptValue.array(new ArrayList<>());
        List<ScriptValue> nodes = new ArrayList<>();
        for (DataFlowNode node : currentGraph.getNodes()) {
            nodes.add(wrapNode(node));
        }
        return ScriptValue.array(nodes);
    }

    private ScriptValue getEdges() {
        if (currentGraph == null) return ScriptValue.array(new ArrayList<>());
        List<ScriptValue> edges = new ArrayList<>();
        for (DataFlowEdge edge : currentGraph.getEdges()) {
            edges.add(wrapEdge(edge));
        }
        return ScriptValue.array(edges);
    }

    private ScriptValue getSources() {
        if (currentGraph == null) return ScriptValue.array(new ArrayList<>());
        List<ScriptValue> sources = new ArrayList<>();
        for (DataFlowNode node : currentGraph.getPotentialSources()) {
            sources.add(wrapNode(node));
        }
        return ScriptValue.array(sources);
    }

    private ScriptValue getSinks() {
        if (currentGraph == null) return ScriptValue.array(new ArrayList<>());
        List<ScriptValue> sinks = new ArrayList<>();
        for (DataFlowNode node : currentGraph.getPotentialSinks()) {
            sinks.add(wrapNode(node));
        }
        return ScriptValue.array(sinks);
    }

    private ScriptValue getReachable(ScriptValue nodeRef) {
        if (currentGraph == null) return ScriptValue.array(new ArrayList<>());
        DataFlowNode node = parseNodeRef(nodeRef);
        if (node == null) return ScriptValue.array(new ArrayList<>());

        Set<DataFlowNode> reachable = currentGraph.getReachableNodes(node);
        List<ScriptValue> result = new ArrayList<>();
        for (DataFlowNode n : reachable) {
            result.add(wrapNode(n));
        }
        return ScriptValue.array(result);
    }

    private ScriptValue getFlowingInto(ScriptValue nodeRef) {
        if (currentGraph == null) return ScriptValue.array(new ArrayList<>());
        DataFlowNode node = parseNodeRef(nodeRef);
        if (node == null) return ScriptValue.array(new ArrayList<>());

        Set<DataFlowNode> flowing = currentGraph.getFlowingIntoNodes(node);
        List<ScriptValue> result = new ArrayList<>();
        for (DataFlowNode n : flowing) {
            result.add(wrapNode(n));
        }
        return ScriptValue.array(result);
    }

    private ScriptValue getNodesByType(ScriptValue typeVal) {
        if (currentGraph == null) return ScriptValue.array(new ArrayList<>());
        String typeName = typeVal.asString().toUpperCase();

        DataFlowNodeType type;
        try {
            type = DataFlowNodeType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            log("Unknown node type: " + typeName);
            return ScriptValue.array(new ArrayList<>());
        }

        List<ScriptValue> result = new ArrayList<>();
        for (DataFlowNode node : currentGraph.getNodesByType(type)) {
            result.add(wrapNode(node));
        }
        return ScriptValue.array(result);
    }

    private ScriptValue flowsTo(ScriptValue sourceRef, ScriptValue sinkRef) {
        if (currentGraph == null) return ScriptValue.bool(false);
        DataFlowNode source = parseNodeRef(sourceRef);
        DataFlowNode sink = parseNodeRef(sinkRef);
        if (source == null || sink == null) return ScriptValue.bool(false);

        Set<DataFlowNode> reachable = currentGraph.getReachableNodes(source);
        return ScriptValue.bool(reachable.contains(sink));
    }

    private ScriptValue getParams() {
        if (currentGraph == null) return ScriptValue.array(new ArrayList<>());
        List<ScriptValue> params = new ArrayList<>();
        for (DataFlowNode node : currentGraph.getNodesByType(DataFlowNodeType.PARAM)) {
            params.add(wrapNode(node));
        }
        return ScriptValue.array(params);
    }

    private ScriptValue getInvokes() {
        if (currentGraph == null) return ScriptValue.array(new ArrayList<>());
        List<ScriptValue> invokes = new ArrayList<>();
        for (DataFlowNode node : currentGraph.getNodesByType(DataFlowNodeType.INVOKE_RESULT)) {
            invokes.add(wrapNode(node));
        }
        return ScriptValue.array(invokes);
    }

    private ScriptValue taintAnalysis(ScriptValue sources, ScriptValue sinks) {
        if (currentGraph == null) return ScriptValue.array(new ArrayList<>());

        List<DataFlowNode> sourceNodes = new ArrayList<>();
        List<DataFlowNode> sinkNodes = new ArrayList<>();

        if (sources.isArray()) {
            for (ScriptValue sv : sources.asArray()) {
                DataFlowNode node = parseNodeRef(sv);
                if (node != null) sourceNodes.add(node);
            }
        } else if (sources.isString()) {
            String type = sources.asString();
            if ("params".equalsIgnoreCase(type)) {
                sourceNodes.addAll(currentGraph.getNodesByType(DataFlowNodeType.PARAM));
            } else {
                sourceNodes.addAll(currentGraph.getPotentialSources());
            }
        }

        if (sinks.isArray()) {
            for (ScriptValue sv : sinks.asArray()) {
                DataFlowNode node = parseNodeRef(sv);
                if (node != null) sinkNodes.add(node);
            }
        } else if (sinks.isString()) {
            String type = sinks.asString();
            if ("invokes".equalsIgnoreCase(type)) {
                sinkNodes.addAll(currentGraph.getNodesByType(DataFlowNodeType.INVOKE_RESULT));
            } else {
                sinkNodes.addAll(currentGraph.getPotentialSinks());
            }
        }

        List<ScriptValue> flows = new ArrayList<>();
        for (DataFlowNode source : sourceNodes) {
            Set<DataFlowNode> reachable = currentGraph.getReachableNodes(source);
            for (DataFlowNode sink : sinkNodes) {
                if (reachable.contains(sink)) {
                    Map<String, ScriptValue> flow = new HashMap<>();
                    flow.put("source", wrapNode(source));
                    flow.put("sink", wrapNode(sink));
                    flows.add(ScriptValue.object(flow));
                }
            }
        }

        return ScriptValue.array(flows);
    }

    private DataFlowNode parseNodeRef(ScriptValue value) {
        if (currentGraph == null) return null;

        if (value.isNumber()) {
            int id = (int) value.asNumber();
            for (DataFlowNode node : currentGraph.getNodes()) {
                if (node.getId() == id) return node;
            }
        } else if (value.isObject()) {
            Map<String, ScriptValue> obj = value.asObject();
            if (obj.containsKey("id")) {
                int id = (int) obj.get("id").asNumber();
                for (DataFlowNode node : currentGraph.getNodes()) {
                    if (node.getId() == id) return node;
                }
            }
        }
        return null;
    }

    private ScriptValue wrapNode(DataFlowNode node) {
        Map<String, ScriptValue> props = new HashMap<>();
        props.put("id", ScriptValue.number(node.getId()));
        props.put("type", ScriptValue.string(node.getType().name()));
        props.put("name", ScriptValue.string(node.getName()));
        props.put("description", ScriptValue.string(node.getDescription()));
        props.put("blockId", ScriptValue.number(node.getBlockId()));
        props.put("instrIndex", ScriptValue.number(node.getInstructionIndex()));
        props.put("isSource", ScriptValue.bool(node.getType().canBeTaintSource()));
        props.put("isSink", ScriptValue.bool(node.getType().canBeTaintSink()));
        return ScriptValue.object(props);
    }

    private ScriptValue wrapEdge(DataFlowEdge edge) {
        Map<String, ScriptValue> props = new HashMap<>();
        props.put("sourceId", ScriptValue.number(edge.getSource().getId()));
        props.put("targetId", ScriptValue.number(edge.getTarget().getId()));
        props.put("type", ScriptValue.string(edge.getType().name()));
        props.put("source", wrapNode(edge.getSource()));
        props.put("target", wrapNode(edge.getTarget()));
        return ScriptValue.object(props);
    }

    public DataFlowGraph getCurrentGraph() {
        return currentGraph;
    }
}
