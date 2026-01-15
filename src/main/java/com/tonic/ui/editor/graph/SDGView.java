package com.tonic.ui.editor.graph;

import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.common.MethodReference;
import com.tonic.analysis.graph.export.SDGDOTExporter;
import com.tonic.analysis.pdg.edge.PDGEdge;
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.sdg.SDG;
import com.tonic.analysis.pdg.sdg.SDGBuilder;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.editor.graph.render.GraphVertex;
import com.tonic.ui.editor.graph.render.SDGVertexRenderer;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class SDGView extends BaseGraphView {

    private SDG sdg;
    private String prepareError = null;
    private final SDGVertexRenderer renderer = new SDGVertexRenderer();

    public SDGView(ClassEntryModel classEntry) {
        super(classEntry);
        populateMethodFilter();
    }

    @Override
    protected void prepareGraphData() {
        sdg = null;
        prepareError = null;
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
            prepareError = "Failed to build SDG: " + e.getMessage();
        }
    }

    @Override
    protected void rebuildGraph() {
        clearGraph();

        if (prepareError != null) {
            showError(prepareError);
            return;
        }

        if (sdg == null) {
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
            GraphVertex<PDGNode> vertex = new GraphVertex<>(node, renderer);
            String style = vertex.getStyle();

            Object cell = graph.insertVertex(parent, null, vertex, 0, 0, 150, 50, style);
            graph.updateCellSize(cell);
            nodeMap.put(node, cell);
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
