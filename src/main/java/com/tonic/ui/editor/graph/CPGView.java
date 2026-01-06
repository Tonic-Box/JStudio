package com.tonic.ui.editor.graph;

import com.tonic.analysis.cpg.CPGBuilder;
import com.tonic.analysis.cpg.CodePropertyGraph;
import com.tonic.analysis.cpg.edge.CPGEdge;
import com.tonic.analysis.cpg.node.*;
import com.tonic.analysis.graph.export.CPGDOTExporter;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.ui.model.ClassEntryModel;

import java.util.HashMap;
import java.util.Map;

public class CPGView extends GraphView {

    private CodePropertyGraph cpg;

    public CPGView(ClassEntryModel classEntry) {
        super(classEntry);
        methodFilterCombo.setVisible(false);
        for (int i = 0; i < toolbar.getComponentCount(); i++) {
            if (toolbar.getComponent(i) instanceof javax.swing.JLabel) {
                javax.swing.JLabel label = (javax.swing.JLabel) toolbar.getComponent(i);
                if (" Method: ".equals(label.getText())) {
                    label.setVisible(false);
                }
            }
        }
    }

    @Override
    protected void buildGraph() {
        clearGraph();
        cpg = null;

        ClassFile classFile = classEntry.getClassFile();

        ClassPool pool = new ClassPool(true);
        pool.put(classFile);

        try {
            cpg = CPGBuilder.forClassPool(pool)
                .withCallGraph()
                .withPDG()
                .build();
        } catch (Exception e) {
            showError("Failed to build CPG: " + e.getMessage());
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
            String label = getNodeLabel(node);
            String style = getNodeStyle(node);

            double width = Math.max(100, label.length() * 7);
            double height = 30;

            Object vertex = graph.insertVertex(parent, null, label,
                0, 0, width, height, style);
            nodeMap.put(node, vertex);
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

    private String getNodeLabel(CPGNode node) {
        if (node instanceof MethodNode) {
            MethodNode method = (MethodNode) node;
            String name = method.getName();
            if (name != null && name.length() > 30) {
                name = name.substring(0, 27) + "...";
            }
            return name;
        }

        if (node instanceof BlockNode) {
            BlockNode block = (BlockNode) node;
            String label = "B" + block.getBlockId();
            if (block.isEntryBlock()) label += " (entry)";
            if (block.isExitBlock()) label += " (exit)";
            return label;
        }

        if (node instanceof InstructionNode) {
            InstructionNode instr = (InstructionNode) node;
            String label = instr.getLabel();
            if (label != null && label.length() > 40) {
                label = label.substring(0, 37) + "...";
            }
            return label;
        }

        if (node instanceof CallSiteNode) {
            CallSiteNode call = (CallSiteNode) node;
            return "CALL: " + call.getTargetName();
        }

        return node.getLabel();
    }

    private String getNodeStyle(CPGNode node) {
        switch (node.getNodeType()) {
            case METHOD:
                return "ENTRY";
            case BLOCK:
                return "BLOCK";
            case INSTRUCTION:
                return "NODE";
            case CALL_SITE:
                return "CALL";
            default:
                return "NODE";
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
