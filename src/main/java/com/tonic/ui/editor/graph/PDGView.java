package com.tonic.ui.editor.graph;

import com.tonic.analysis.graph.export.PDGDOTExporter;
import com.tonic.analysis.pdg.PDG;
import com.tonic.analysis.pdg.PDGBuilder;
import com.tonic.analysis.pdg.edge.PDGEdge;
import com.tonic.analysis.pdg.node.PDGInstructionNode;
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.node.PDGNodeType;
import com.tonic.analysis.pdg.node.PDGRegionNode;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.ir.PhiInstruction;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;

import java.util.HashMap;
import java.util.Map;

public class PDGView extends GraphView {

    private final Map<MethodEntryModel, PDG> methodPDGs = new HashMap<>();

    public PDGView(ClassEntryModel classEntry) {
        super(classEntry);
        populateMethodFilter();
    }

    @Override
    protected void buildGraph() {
        clearGraph();
        methodPDGs.clear();

        String selectedMethod = (String) methodFilterCombo.getSelectedItem();
        boolean showAll = "All Methods".equals(selectedMethod);

        graph.getModel().beginUpdate();
        try {
            Object parent = graph.getDefaultParent();
            int methodOffset = 0;

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

                    renderPDG(parent, pdg, method.getName(), methodOffset);
                    methodOffset += 400;
                } catch (Exception e) {
                    // Skip methods that fail to analyze
                }
            }
        } finally {
            graph.getModel().endUpdate();
        }
    }

    private void renderPDG(Object parent, PDG pdg, String methodName, int xOffset) {
        Map<PDGNode, Object> nodeMap = new HashMap<>();

        for (PDGNode node : pdg.getNodes()) {
            String label = getNodeLabel(node);
            String style = getNodeStyle(node);

            double width = Math.max(100, label.length() * 7);
            double height = 30;

            Object vertex = graph.insertVertex(parent, null, label,
                xOffset, 0, width, height, style);
            nodeMap.put(node, vertex);
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

    private String getNodeLabel(PDGNode node) {
        if (node instanceof PDGRegionNode) {
            PDGRegionNode region = (PDGRegionNode) node;
            if (region.isEntry()) return "ENTRY";
            if (region.isExit()) return "EXIT";
            return region.getLabel();
        }

        if (node instanceof PDGInstructionNode) {
            PDGInstructionNode instrNode = (PDGInstructionNode) node;
            var instr = instrNode.getInstruction();
            if (instr == null) return "?";

            String repr = instr.toString();
            if (repr.length() > 40) {
                repr = repr.substring(0, 37) + "...";
            }
            return repr;
        }

        return node.getType().name();
    }

    private String getNodeStyle(PDGNode node) {
        PDGNodeType type = node.getType();

        switch (type) {
            case ENTRY:
                return "ENTRY";
            case EXIT:
                return "EXIT";
            case PHI:
                return "PHI";
            case CALL_SITE:
                return "CALL";
            default:
                if (node instanceof PDGInstructionNode) {
                    PDGInstructionNode instrNode = (PDGInstructionNode) node;
                    if (instrNode.getInstruction() instanceof InvokeInstruction) {
                        return "CALL";
                    }
                    if (instrNode.getInstruction() instanceof PhiInstruction) {
                        return "PHI";
                    }
                }
                return "NODE";
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
        if (edge.getType().isDataDependence()) {
            return "DATA";
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
