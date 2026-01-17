package com.tonic.ui.editor.callgraph;

import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.callgraph.CallGraphNode;
import com.tonic.analysis.callgraph.CallSite;
import com.tonic.analysis.common.MethodReference;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CallGraphDOTExporter {

    public String export(MethodReference focus, Set<MethodReference> callers,
                         Set<MethodReference> callees, CallGraph graph) {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph CallGraph {\n");
        sb.append("  rankdir=TB;\n");
        sb.append("  node [shape=box, style=rounded, fontname=\"Consolas\", fontsize=10];\n");
        sb.append("  edge [fontname=\"Consolas\", fontsize=9];\n");
        sb.append("\n");

        Map<MethodReference, String> nodeIds = new HashMap<>();
        int nodeCounter = 0;

        String focusId = "n" + nodeCounter++;
        nodeIds.put(focus, focusId);
        sb.append("  ").append(focusId)
          .append(" [label=\"").append(formatLabel(focus)).append("\"")
          .append(", style=\"rounded,bold\", penwidth=2, color=\"#3498db\"];\n");

        for (MethodReference caller : callers) {
            if (!nodeIds.containsKey(caller)) {
                String id = "n" + nodeCounter++;
                nodeIds.put(caller, id);
                String style = isExternal(caller, graph) ? ", style=\"rounded,dashed\", color=\"#95a5a6\"" : "";
                sb.append("  ").append(id)
                  .append(" [label=\"").append(formatLabel(caller)).append("\"").append(style).append("];\n");
            }
        }

        for (MethodReference callee : callees) {
            if (!nodeIds.containsKey(callee)) {
                String id = "n" + nodeCounter++;
                nodeIds.put(callee, id);
                String style = isExternal(callee, graph) ? ", style=\"rounded,dashed\", color=\"#95a5a6\"" : "";
                sb.append("  ").append(id)
                  .append(" [label=\"").append(formatLabel(callee)).append("\"").append(style).append("];\n");
            }
        }

        sb.append("\n");

        for (MethodReference caller : callers) {
            if (graph.calls(caller, focus)) {
                String color = getEdgeColor(caller, focus, graph);
                sb.append("  ").append(nodeIds.get(caller)).append(" -> ").append(nodeIds.get(focus))
                  .append(" [color=\"").append(color).append("\"];\n");
            }

            for (MethodReference otherCaller : callers) {
                if (!caller.equals(otherCaller) && graph.calls(caller, otherCaller)) {
                    String color = getEdgeColor(caller, otherCaller, graph);
                    sb.append("  ").append(nodeIds.get(caller)).append(" -> ").append(nodeIds.get(otherCaller))
                      .append(" [color=\"").append(color).append("\"];\n");
                }
            }
        }

        for (MethodReference callee : callees) {
            if (graph.calls(focus, callee)) {
                String color = getEdgeColor(focus, callee, graph);
                sb.append("  ").append(nodeIds.get(focus)).append(" -> ").append(nodeIds.get(callee))
                  .append(" [color=\"").append(color).append("\"];\n");
            }

            for (MethodReference otherCallee : callees) {
                if (!callee.equals(otherCallee) && graph.calls(callee, otherCallee)) {
                    String color = getEdgeColor(callee, otherCallee, graph);
                    sb.append("  ").append(nodeIds.get(callee)).append(" -> ").append(nodeIds.get(otherCallee))
                      .append(" [color=\"").append(color).append("\"];\n");
                }
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    private boolean isExternal(MethodReference ref, CallGraph graph) {
        return graph.getNode(ref) == null;
    }

    private String getEdgeColor(MethodReference caller, MethodReference callee, CallGraph graph) {
        CallGraphNode node = graph.getNode(caller);
        if (node != null) {
            for (CallSite site : node.getOutgoingCalls()) {
                if (site.getTarget().equals(callee)) {
                    switch (site.getInvokeType()) {
                        case VIRTUAL:
                            return "#00dd00";
                        case STATIC:
                            return "#00dddd";
                        case SPECIAL:
                            return "#dddd00";
                        case INTERFACE:
                            return "#dd00dd";
                        case DYNAMIC:
                            return "#dd8800";
                    }
                }
            }
        }
        return "#888888";
    }

    private String formatLabel(MethodReference ref) {
        String className = getSimpleClassName(ref.getOwner());
        String methodName = ref.getName();

        if ("<init>".equals(methodName)) {
            return escapeLabel(className + "\\n<init>");
        } else if ("<clinit>".equals(methodName)) {
            return escapeLabel(className + "\\n<clinit>");
        }
        return escapeLabel(className + "\\n" + methodName + "()");
    }

    private String getSimpleClassName(String fullName) {
        if (fullName == null) return "";
        int lastSlash = fullName.lastIndexOf('/');
        return lastSlash >= 0 ? fullName.substring(lastSlash + 1) : fullName;
    }

    private String escapeLabel(String text) {
        if (text == null) return "";
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("<", "\\<")
            .replace(">", "\\>");
    }
}
