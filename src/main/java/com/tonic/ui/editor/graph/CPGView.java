package com.tonic.ui.editor.graph;

import com.tonic.analysis.cpg.CPGBuilder;
import com.tonic.analysis.cpg.CodePropertyGraph;
import com.tonic.analysis.cpg.edge.CPGEdge;
import com.tonic.analysis.cpg.node.CPGNode;
import com.tonic.analysis.graph.export.CPGDOTExporter;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.ui.editor.graph.render.CPGVertexRenderer;
import com.tonic.ui.editor.graph.render.GraphVertex;
import com.tonic.ui.model.ClassEntryModel;

import java.util.HashMap;
import java.util.Map;

public class CPGView extends BaseGraphView {

    private CodePropertyGraph cpg;
    private String prepareError = null;
    private final CPGVertexRenderer renderer = new CPGVertexRenderer();

    public CPGView(ClassEntryModel classEntry) {
        super(classEntry);
        hideMethodFilter();
    }

    @Override
    protected void prepareGraphData() {
        cpg = null;
        prepareError = null;

        ClassFile classFile = classEntry.getClassFile();

        ClassPool pool = new ClassPool(true);
        pool.put(classFile);

        try {
            cpg = CPGBuilder.forClassPool(pool)
                .withCallGraph()
                .withPDG()
                .build();
        } catch (Exception e) {
            prepareError = "Failed to build CPG: " + e.getMessage();
        }
    }

    @Override
    protected void rebuildGraph() {
        clearGraph();

        if (prepareError != null) {
            showError(prepareError);
            return;
        }

        if (cpg == null) {
            return;
        }

        graph.getModel().beginUpdate();
        try {
            renderCPG();
        } finally {
            graph.getModel().endUpdate();
        }
    }

    private void renderCPG() {
        if (cpg == null) return;

        Object parent = graph.getDefaultParent();
        Map<CPGNode, Object> nodeMap = new HashMap<>();

        for (CPGNode node : cpg.getAllNodes()) {
            GraphVertex<CPGNode> vertex = new GraphVertex<>(node, renderer);
            String style = vertex.getStyle();

            Object cell = graph.insertVertex(parent, null, vertex, 0, 0, 150, 50, style);
            graph.updateCellSize(cell);
            nodeMap.put(node, cell);
        }

        for (CPGEdge edge : cpg.getAllEdges()) {
            Object source = nodeMap.get(edge.getSource());
            Object target = nodeMap.get(edge.getTarget());

            if (source != null && target != null) {
                String edgeLabel = getEdgeLabel(edge);
                String edgeStyle = getEdgeStyle(edge);
                graph.insertEdge(parent, null, edgeLabel, source, target, edgeStyle);
            }
        }
    }

    private String getEdgeLabel(CPGEdge edge) {
        switch (edge.getType()) {
            case CFG_TRUE:
                return "T";
            case CFG_FALSE:
                return "F";
            case CFG_EXCEPTION:
                return "exc";
            default:
                return "";
        }
    }

    private String getEdgeStyle(CPGEdge edge) {
        if (edge.getType().isCFGEdge()) {
            return "CFG";
        }
        if (edge.getType().isDataFlowEdge()) {
            return "DATA";
        }
        if (edge.getType().isControlDependenceEdge()) {
            return "CONTROL";
        }
        return "DATA";
    }

    @Override
    protected String generateDOT() {
        if (cpg == null) {
            return "// No CPG data available\n// Build the graph first by switching to this view";
        }

        try {
            CPGDOTExporter exporter = new CPGDOTExporter();
            return exporter.export(cpg);
        } catch (Exception e) {
            return "// Error generating DOT: " + e.getMessage();
        }
    }
}
