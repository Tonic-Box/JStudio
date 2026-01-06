package com.tonic.ui.editor.graph;

import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.common.MethodReference;
import com.tonic.analysis.graph.export.SDGDOTExporter;
import com.tonic.analysis.pdg.edge.PDGEdge;
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.sdg.SDG;
import com.tonic.analysis.pdg.sdg.SDGBuilder;
import com.tonic.analysis.pdg.sdg.node.*;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class SDGView extends GraphView {

    private SDG sdg;

    public SDGView(ClassEntryModel classEntry) {
        super(classEntry);
        populateMethodFilter();
    }

    @Override
    protected void buildGraph() {
        clearGraph();
        sdg = null;
        Map<MethodReference, IRMethod> irMethods = new LinkedHashMap<>();

        ClassFile classFile = classEntry.getClassFile();

        ClassPool pool = new ClassPool(true);
        pool.put(classFile);

        for (MethodEntryModel method : classEntry.getMethods()) {
            MethodEntry entry = method.getMethodEntry();
            if (entry.getCodeAttribute() == null) continue;

            try {
                SSA ssa = new SSA(classFile.getConstPool());
                IRMethod irMethod = ssa.lift(entry);
                if (irMethod != null) {
                    MethodReference ref = new MethodReference(
                        classFile.getClassName(), method.getName(), entry.getDesc());
                    irMethods.put(ref, irMethod);
                }
            } catch (Exception e) {
                // Skip methods that fail to lift
            }
        }

        if (irMethods.isEmpty()) {
            return;
        }

        try {
            CallGraph callGraph = CallGraph.build(pool);
            sdg = SDGBuilder.build(callGraph, irMethods);
        } catch (Exception e) {
            showError("Failed to build SDG: " + e.getMessage());
            return;
        }

        graph.getModel().beginUpdate();
        try {
            renderSDG();
        } finally {
            graph.getModel().endUpdate();
        }
    }

    private void renderSDG() {
        if (sdg == null) return;

        Object parent = graph.getDefaultParent();
        Map<PDGNode, Object> nodeMap = new HashMap<>();

        for (PDGNode node : sdg.getAllNodes()) {
            String label = getNodeLabel(node);
            String style = getNodeStyle(node);

            double width = Math.max(120, label.length() * 7);
            double height = 35;

            Object vertex = graph.insertVertex(parent, null, label,
                0, 0, width, height, style);
            nodeMap.put(node, vertex);
        }

        for (PDGEdge edge : sdg.getParameterEdges()) {
            Object source = nodeMap.get(edge.getSource());
            Object target = nodeMap.get(edge.getTarget());

            if (source != null && target != null) {
                String edgeStyle = getEdgeStyle(edge);
                graph.insertEdge(parent, null, "", source, target, edgeStyle);
            }
        }

        for (PDGEdge edge : sdg.getSummaryEdges()) {
            Object source = nodeMap.get(edge.getSource());
            Object target = nodeMap.get(edge.getTarget());

            if (source != null && target != null) {
                graph.insertEdge(parent, null, "summary", source, target, "DATA");
            }
        }
    }

    private String getNodeLabel(PDGNode node) {
        if (node instanceof SDGEntryNode) {
            SDGEntryNode entry = (SDGEntryNode) node;
            String name = entry.getMethodName();
            if (name != null) {
                int lastDot = name.lastIndexOf('.');
                if (lastDot >= 0) {
                    name = name.substring(lastDot + 1);
                }
                int paren = name.indexOf('(');
                if (paren >= 0) {
                    name = name.substring(0, paren);
                }
            }
            return "ENTRY: " + (name != null ? name : "?");
        }

        if (node instanceof SDGCallNode) {
            SDGCallNode call = (SDGCallNode) node;
            return "CALL: " + call.getTargetName();
        }

        if (node instanceof SDGFormalInNode) {
            SDGFormalInNode formal = (SDGFormalInNode) node;
            return "F_IN[" + formal.getParameterIndex() + "]";
        }

        if (node instanceof SDGFormalOutNode) {
            return "F_OUT";
        }

        if (node instanceof SDGActualInNode) {
            SDGActualInNode actual = (SDGActualInNode) node;
            return "A_IN[" + actual.getParameterIndex() + "]";
        }

        if (node instanceof SDGActualOutNode) {
            return "A_OUT";
        }

        return node.getType().name();
    }

    private String getNodeStyle(PDGNode node) {
        if (node instanceof SDGEntryNode) {
            return "ENTRY";
        }
        if (node instanceof SDGCallNode) {
            return "CALL";
        }
        if (node instanceof SDGFormalInNode || node instanceof SDGActualInNode) {
            return "BLOCK";
        }
        if (node instanceof SDGFormalOutNode || node instanceof SDGActualOutNode) {
            return "EXIT";
        }
        return "NODE";
    }

    private String getEdgeStyle(PDGEdge edge) {
        switch (edge.getType()) {
            case CALL:
                return "CFG";
            case PARAMETER_IN:
            case PARAMETER_OUT:
                return "DATA";
            case SUMMARY:
                return "CONTROL";
            default:
                return "DATA";
        }
    }

    @Override
    protected String generateDOT() {
        if (sdg == null) {
            return "// No SDG data available\n// Build the graph first by switching to this view";
        }

        try {
            SDGDOTExporter exporter = new SDGDOTExporter();
            return exporter.export(sdg);
        } catch (Exception e) {
            return "// Error generating DOT: " + e.getMessage();
        }
    }
}
