package com.tonic.ui.editor.graph;

import com.tonic.analysis.graph.export.PDGDOTExporter;
import com.tonic.analysis.pdg.PDG;
import com.tonic.analysis.pdg.PDGBuilder;
import com.tonic.analysis.pdg.edge.PDGEdge;
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.editor.graph.render.GraphVertex;
import com.tonic.ui.editor.graph.render.PDGVertexRenderer;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;

import java.util.HashMap;
import java.util.Map;

public class PDGView extends BaseGraphView {

    private final Map<MethodEntryModel, PDG> methodPDGs = new HashMap<>();
    private final PDGVertexRenderer renderer = new PDGVertexRenderer();

    public PDGView(ClassEntryModel classEntry) {
        super(classEntry);
        populateMethodFilter();
    }

    @Override
    protected void prepareGraphData() {
        methodPDGs.clear();

        String selectedMethod = (String) methodFilterCombo.getSelectedItem();
        boolean showAll = "All Methods".equals(selectedMethod);

        for (MethodEntryModel method : classEntry.getMethods()) {
            MethodEntry entry = method.getMethodEntry();
            if (entry.getCodeAttribute() == null) continue;

            String methodKey = method.getName() + entry.getDesc();
            if (!showAll && !methodKey.equals(selectedMethod)) continue;

            try {
                SSA ssa = new SSA(classEntry.getClassFile().getConstPool());
                IRMethod irMethod = ssa.lift(entry);
                if (irMethod == null) continue;

                PDG pdg = PDGBuilder.build(irMethod);
                methodPDGs.put(method, pdg);
            } catch (Exception e) {
                // Skip methods that fail to analyze
            }
        }
    }

    @Override
    protected void rebuildGraph() {
        clearGraph();

        graph.getModel().beginUpdate();
        try {
            Object parent = graph.getDefaultParent();

            for (Map.Entry<MethodEntryModel, PDG> entry : methodPDGs.entrySet()) {
                renderPDG(parent, entry.getValue());
            }

            applyHierarchicalLayout();
        } finally {
            graph.getModel().endUpdate();
        }
    }

    private void renderPDG(Object parent, PDG pdg) {
        Map<PDGNode, Object> nodeMap = new HashMap<>();

        for (PDGNode node : pdg.getNodes()) {
            GraphVertex<PDGNode> vertex = new GraphVertex<>(node, renderer);
            String style = vertex.getStyle();

            Object cell = graph.insertVertex(parent, null, vertex, 0, 0, 150, 60, style);
            graph.updateCellSize(cell);
            nodeMap.put(node, cell);
        }

        for (PDGEdge edge : pdg.getEdges()) {
            Object source = nodeMap.get(edge.getSource());
            Object target = nodeMap.get(edge.getTarget());

            if (source != null && target != null) {
                String edgeLabel = getEdgeLabel(edge);
                String edgeStyle = getEdgeStyle(edge);
                graph.insertEdge(parent, null, edgeLabel, source, target, edgeStyle);
            }
        }
    }

    private String getEdgeLabel(PDGEdge edge) {
        String var = edge.getVariable();
        if (var != null && !var.isEmpty()) {
            return var;
        }
        return "";
    }

    private String getEdgeStyle(PDGEdge edge) {
        if (edge.getType().isControlDependence()) {
            return "CONTROL";
        }
        return "DATA";
    }

    @Override
    protected String generateDOT() {
        StringBuilder sb = new StringBuilder();

        String selectedMethod = (String) methodFilterCombo.getSelectedItem();
        boolean showAll = "All Methods".equals(selectedMethod);

        for (Map.Entry<MethodEntryModel, PDG> entry : methodPDGs.entrySet()) {
            String methodKey = entry.getKey().getName() + entry.getKey().getMethodEntry().getDesc();
            if (!showAll && !methodKey.equals(selectedMethod)) continue;

            PDG pdg = entry.getValue();
            if (pdg != null) {
                PDGDOTExporter exporter = new PDGDOTExporter();
                sb.append("// Method: ").append(entry.getKey().getName()).append("\n");
                sb.append(exporter.export(pdg));
                sb.append("\n\n");
            }
        }

        if (sb.length() == 0) {
            return "// No PDG data available";
        }

        return sb.toString();
    }
}
