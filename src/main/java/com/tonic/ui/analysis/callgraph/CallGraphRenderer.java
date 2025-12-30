package com.tonic.ui.analysis.callgraph;

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.model.mxCell;
import com.mxgraph.view.mxGraph;
import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.callgraph.CallGraphNode;
import com.tonic.analysis.callgraph.CallSite;
import com.tonic.analysis.common.MethodReference;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class CallGraphRenderer {

    private static final int NODE_HEIGHT = 45;
    private static final int MIN_NODE_WIDTH = 100;
    private static final int MAX_NODE_WIDTH = 220;
    private static final int CHAR_WIDTH = 8;
    private static final int NODE_PADDING = 30;
    private static final int INTER_RANK_SPACING = 70;
    private static final int INTRA_CELL_SPACING = 40;
    private static final int MAX_CLASS_LENGTH = 22;
    private static final int MAX_METHOD_LENGTH = 20;

    private final mxGraph graph;
    private final CallGraphModel model;
    private final CallGraphStyleFactory styleFactory;

    public CallGraphRenderer(mxGraph graph, CallGraphModel model, CallGraphStyleFactory styleFactory) {
        this.graph = graph;
        this.model = model;
        this.styleFactory = styleFactory;
    }

    public void render() {
        CallGraph callGraph = model.getCallGraph();
        MethodReference focusMethod = model.getFocusMethod();

        if (callGraph == null || focusMethod == null) {
            return;
        }

        model.clearCellMap();
        int maxDepth = model.getMaxDepth();

        Set<MethodReference> callers = collectCallers(callGraph, focusMethod, maxDepth);
        Set<MethodReference> callees = collectCallees(callGraph, focusMethod, maxDepth);

        graph.getModel().beginUpdate();
        try {
            clearGraph();

            Object parent = graph.getDefaultParent();
            Map<MethodReference, Object> nodeMap = new HashMap<>();

            createFocusNode(parent, focusMethod, nodeMap);
            createCallerNodes(parent, callers, nodeMap);
            createCalleeNodes(parent, callees, nodeMap);

            createCallerEdges(parent, callGraph, callers, focusMethod, nodeMap);
            createCalleeEdges(parent, callGraph, callees, focusMethod, nodeMap);

            applyLayout(parent);
        } finally {
            graph.getModel().endUpdate();
        }
    }

    public RenderStats getLastRenderStats() {
        CallGraph callGraph = model.getCallGraph();
        MethodReference focusMethod = model.getFocusMethod();

        if (callGraph == null || focusMethod == null) {
            return new RenderStats(0, 0);
        }

        Set<MethodReference> callers = collectCallers(callGraph, focusMethod, model.getMaxDepth());
        Set<MethodReference> callees = collectCallees(callGraph, focusMethod, model.getMaxDepth());
        return new RenderStats(callers.size(), callees.size());
    }

    private void clearGraph() {
        graph.removeCells(graph.getChildCells(graph.getDefaultParent(), true, true));
    }

    private void createFocusNode(Object parent, MethodReference focusMethod,
                                  Map<MethodReference, Object> nodeMap) {
        String label = formatMethodLabel(focusMethod);
        int width = calculateNodeWidth(focusMethod);
        String style = styleFactory.getNodeStyle(model.getCallGraph(), focusMethod, true);

        Object node = graph.insertVertex(parent, null, label, 0, 0, width, NODE_HEIGHT, style);
        nodeMap.put(focusMethod, node);
        model.mapCellToMethod(node, focusMethod);
    }

    private void createCallerNodes(Object parent, Set<MethodReference> callers,
                                    Map<MethodReference, Object> nodeMap) {
        for (MethodReference caller : callers) {
            if (!nodeMap.containsKey(caller)) {
                createNode(parent, caller, nodeMap);
            }
        }
    }

    private void createCalleeNodes(Object parent, Set<MethodReference> callees,
                                    Map<MethodReference, Object> nodeMap) {
        for (MethodReference callee : callees) {
            if (!nodeMap.containsKey(callee)) {
                createNode(parent, callee, nodeMap);
            }
        }
    }

    private void createNode(Object parent, MethodReference ref, Map<MethodReference, Object> nodeMap) {
        String label = formatMethodLabel(ref);
        int width = calculateNodeWidth(ref);
        String style = styleFactory.getNodeStyle(model.getCallGraph(), ref, false);

        Object node = graph.insertVertex(parent, null, label, 0, 0, width, NODE_HEIGHT, style);
        nodeMap.put(ref, node);
        model.mapCellToMethod(node, ref);
    }

    private void createCallerEdges(Object parent, CallGraph callGraph, Set<MethodReference> callers,
                                    MethodReference focusMethod, Map<MethodReference, Object> nodeMap) {
        for (MethodReference caller : callers) {
            if (callGraph.calls(caller, focusMethod)) {
                String edgeLabel = getEdgeTooltip(callGraph, caller, focusMethod);
                String edgeStyle = styleFactory.getEdgeStyle(callGraph, caller, focusMethod);
                Object edge = graph.insertEdge(parent, null, "",
                        nodeMap.get(caller), nodeMap.get(focusMethod), edgeStyle);
                if (edge instanceof mxCell) {
                    ((mxCell) edge).setValue(edgeLabel);
                }
            }
        }
    }

    private void createCalleeEdges(Object parent, CallGraph callGraph, Set<MethodReference> callees,
                                    MethodReference focusMethod, Map<MethodReference, Object> nodeMap) {
        for (MethodReference callee : callees) {
            if (callGraph.calls(focusMethod, callee)) {
                String edgeLabel = getEdgeTooltip(callGraph, focusMethod, callee);
                String edgeStyle = styleFactory.getEdgeStyle(callGraph, focusMethod, callee);
                Object edge = graph.insertEdge(parent, null, "",
                        nodeMap.get(focusMethod), nodeMap.get(callee), edgeStyle);
                if (edge instanceof mxCell) {
                    ((mxCell) edge).setValue(edgeLabel);
                }
            }
        }
    }

    private void applyLayout(Object parent) {
        mxHierarchicalLayout layout = new mxHierarchicalLayout(graph);
        layout.setInterRankCellSpacing(INTER_RANK_SPACING);
        layout.setIntraCellSpacing(INTRA_CELL_SPACING);
        layout.execute(parent);
    }

    private Set<MethodReference> collectCallers(CallGraph callGraph, MethodReference method, int depth) {
        Set<MethodReference> result = new LinkedHashSet<>();
        collectCallersRecursive(callGraph, method, depth, result);
        return result;
    }

    private void collectCallersRecursive(CallGraph callGraph, MethodReference method,
                                          int depth, Set<MethodReference> result) {
        if (depth <= 0) return;
        Set<MethodReference> callers = callGraph.getCallers(method);
        for (MethodReference caller : callers) {
            if (result.add(caller)) {
                collectCallersRecursive(callGraph, caller, depth - 1, result);
            }
        }
    }

    private Set<MethodReference> collectCallees(CallGraph callGraph, MethodReference method, int depth) {
        Set<MethodReference> result = new LinkedHashSet<>();
        collectCalleesRecursive(callGraph, method, depth, result);
        return result;
    }

    private void collectCalleesRecursive(CallGraph callGraph, MethodReference method,
                                          int depth, Set<MethodReference> result) {
        if (depth <= 0) return;
        Set<MethodReference> callees = callGraph.getCallees(method);
        for (MethodReference callee : callees) {
            if (result.add(callee)) {
                collectCalleesRecursive(callGraph, callee, depth - 1, result);
            }
        }
    }

    private String getEdgeTooltip(CallGraph callGraph, MethodReference caller, MethodReference callee) {
        CallGraphNode callerNode = callGraph.getNode(caller);
        if (callerNode == null) return "";

        StringBuilder sb = new StringBuilder();
        for (CallSite site : callerNode.getOutgoingCalls()) {
            if (site.getTarget().equals(callee)) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(site.getInvokeType().name());
            }
        }
        return sb.toString();
    }

    private String formatMethodLabel(MethodReference ref) {
        String className = getSimpleClassName(ref.getOwner());
        String methodName = ref.getName();
        String topLine;
        String bottomLine;

        if ("<init>".equals(methodName)) {
            topLine = "constructor";
            bottomLine = "new " + truncate(className, MAX_METHOD_LENGTH) + "()";
        } else if ("<clinit>".equals(methodName)) {
            topLine = "initializer";
            bottomLine = "static { }";
        } else {
            topLine = truncate(className, MAX_CLASS_LENGTH);
            bottomLine = truncate(methodName, MAX_METHOD_LENGTH) + "()";
        }

        return "<html><center>" +
                "<span style=\"font-size:10px; color:#9090A8;\">" + topLine + "</span><br>" +
                "<span style=\"font-size:12px; color:#E4E4EF;\">" + bottomLine + "</span>" +
                "</center></html>";
    }

    private int calculateNodeWidth(MethodReference ref) {
        String className = getSimpleClassName(ref.getOwner());
        String methodName = ref.getName();

        String displayName;
        if ("<init>".equals(methodName)) {
            displayName = "new " + className + "()";
        } else if ("<clinit>".equals(methodName)) {
            displayName = "static { }";
        } else {
            displayName = className.length() > methodName.length() ? className : methodName + "()";
        }

        return Math.max(MIN_NODE_WIDTH, Math.min(MAX_NODE_WIDTH, displayName.length() * CHAR_WIDTH + NODE_PADDING));
    }

    private String getSimpleClassName(String internalName) {
        int lastSlash = internalName.lastIndexOf('/');
        return lastSlash >= 0 ? internalName.substring(lastSlash + 1) : internalName;
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    public static class RenderStats {
        private final int callerCount;
        private final int calleeCount;

        public RenderStats(int callerCount, int calleeCount) {
            this.callerCount = callerCount;
            this.calleeCount = calleeCount;
        }

        public int getCallerCount() {
            return callerCount;
        }

        public int getCalleeCount() {
            return calleeCount;
        }
    }
}
