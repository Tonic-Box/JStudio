package com.tonic.ui.analysis;

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxStylesheet;
import com.tonic.analysis.dataflow.*;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.MethodSelectedEvent;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class DataFlowPanel extends ThemedJPanel {

    private final ProjectModel project;
    private final mxGraph graph;
    private final mxGraphComponent graphComponent;
    private JTextArea statusArea;
    private JComboBox<String> methodCombo;
    private JComboBox<String> filterCombo;
    private JCheckBox showTaintedCheckbox;

    private DataFlowGraph currentGraph;
    private Map<Object, DataFlowNode> cellToNodeMap = new HashMap<>();
    private JPopupMenu contextMenu;

    private ClassFile currentClass;
    private MethodEntry currentMethod;

    public DataFlowPanel(ProjectModel project) {
        super(BackgroundStyle.SECONDARY, new BorderLayout());
        this.project = project;

        // Control panel
        JPanel controlPanel = createControlPanel();
        add(controlPanel, BorderLayout.NORTH);

        // Graph component
        graph = new mxGraph();
        setupGraphStyles();
        graph.setAutoSizeCells(true);
        graph.setCellsEditable(false);
        graph.setCellsMovable(true);
        graph.setCellsResizable(false);

        graphComponent = new mxGraphComponent(graph);
        graphComponent.setBackground(JStudioTheme.getBgTertiary());
        graphComponent.getViewport().setBackground(JStudioTheme.getBgTertiary());
        graphComponent.setBorder(null);
        graphComponent.setToolTips(true);

        setupMouseListeners();
        setupContextMenu();

        add(graphComponent, BorderLayout.CENTER);

        statusArea = new JTextArea(4, 40);
        statusArea.setEditable(false);
        statusArea.setBackground(JStudioTheme.getBgTertiary());
        statusArea.setForeground(JStudioTheme.getTextSecondary());
        statusArea.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE));
        statusArea.setBorder(BorderFactory.createEmptyBorder(UIConstants.SPACING_SMALL, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_SMALL, UIConstants.SPACING_MEDIUM));

        JScrollPane statusScroll = new JScrollPane(statusArea);
        statusScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()));
        add(statusScroll, BorderLayout.SOUTH);

        updateStatus("Select a method to visualize its data flow graph.");

        // Register for method selection events
        EventBus.getInstance().register(MethodSelectedEvent.class, this::handleMethodSelected);
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_SMALL));
        panel.setBackground(JStudioTheme.getBgSecondary());

        panel.add(createLabel("Method:"));
        methodCombo = new JComboBox<>();
        methodCombo.setBackground(JStudioTheme.getBgTertiary());
        methodCombo.setForeground(JStudioTheme.getTextPrimary());
        methodCombo.addActionListener(e -> updateFromMethodCombo());
        panel.add(methodCombo);

        panel.add(createLabel("Filter:"));
        filterCombo = new JComboBox<>(new String[]{
            "All Nodes", "Sources Only", "Sinks Only", "Parameters", "Return Values"
        });
        filterCombo.setBackground(JStudioTheme.getBgTertiary());
        filterCombo.setForeground(JStudioTheme.getTextPrimary());
        filterCombo.addActionListener(e -> visualizeGraph());
        panel.add(filterCombo);

        showTaintedCheckbox = new JCheckBox("Highlight Tainted");
        showTaintedCheckbox.setBackground(JStudioTheme.getBgSecondary());
        showTaintedCheckbox.setForeground(JStudioTheme.getTextPrimary());
        showTaintedCheckbox.addActionListener(e -> visualizeGraph());
        panel.add(showTaintedCheckbox);

        JButton analyzeButton = createButton("Analyze");
        analyzeButton.addActionListener(e -> buildDataFlowGraph());
        panel.add(analyzeButton);

        JButton taintButton = createButton("Run Taint");
        taintButton.addActionListener(e -> runTaintAnalysis());
        panel.add(taintButton);

        JButton exportButton = createButton("Export PNG");
        exportButton.addActionListener(e -> exportGraphAsPng());
        panel.add(exportButton);

        return panel;
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(JStudioTheme.getTextPrimary());
        return label;
    }

    private JButton createButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(JStudioTheme.getBgTertiary());
        button.setForeground(JStudioTheme.getTextPrimary());
        return button;
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static Color darker(Color c, float factor) {
        return new Color(
            Math.max(0, (int)(c.getRed() * factor)),
            Math.max(0, (int)(c.getGreen() * factor)),
            Math.max(0, (int)(c.getBlue() * factor))
        );
    }

    private void setupGraphStyles() {
        mxStylesheet stylesheet = graph.getStylesheet();

        // Base node style
        Map<String, Object> baseStyle = new HashMap<>();
        baseStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RECTANGLE);
        baseStyle.put(mxConstants.STYLE_ROUNDED, true);
        baseStyle.put(mxConstants.STYLE_FILLCOLOR, toHex(JStudioTheme.getGraphNodeFill()));
        baseStyle.put(mxConstants.STYLE_STROKECOLOR, toHex(JStudioTheme.getGraphNodeStroke()));
        baseStyle.put(mxConstants.STYLE_FONTCOLOR, toHex(JStudioTheme.getTextPrimary()));
        baseStyle.put(mxConstants.STYLE_FONTSIZE, 10);
        baseStyle.put(mxConstants.STYLE_SPACING, 4);
        stylesheet.putCellStyle("DEFAULT", baseStyle);

        // Parameter node style (input) - uses constructor colors (green)
        Map<String, Object> paramStyle = new HashMap<>(baseStyle);
        paramStyle.put(mxConstants.STYLE_FILLCOLOR, toHex(JStudioTheme.getGraphConstructorFill()));
        paramStyle.put(mxConstants.STYLE_STROKECOLOR, toHex(JStudioTheme.getGraphConstructorStroke()));
        stylesheet.putCellStyle("PARAM", paramStyle);

        // Return/sink node style (output) - uses error color
        Map<String, Object> sinkStyle = new HashMap<>(baseStyle);
        sinkStyle.put(mxConstants.STYLE_FILLCOLOR, toHex(darker(JStudioTheme.getError(), 0.3f)));
        sinkStyle.put(mxConstants.STYLE_STROKECOLOR, toHex(JStudioTheme.getError()));
        stylesheet.putCellStyle("SINK", sinkStyle);

        // Constant node style - uses static colors (purple)
        Map<String, Object> constStyle = new HashMap<>(baseStyle);
        constStyle.put(mxConstants.STYLE_FILLCOLOR, toHex(JStudioTheme.getGraphStaticFill()));
        constStyle.put(mxConstants.STYLE_STROKECOLOR, toHex(JStudioTheme.getGraphStaticStroke()));
        stylesheet.putCellStyle("CONSTANT", constStyle);

        // Phi node style - uses focus stroke color
        Map<String, Object> phiStyle = new HashMap<>(baseStyle);
        phiStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_ELLIPSE);
        phiStyle.put(mxConstants.STYLE_FILLCOLOR, toHex(darker(JStudioTheme.getGraphFocusFill(), 0.9f)));
        phiStyle.put(mxConstants.STYLE_STROKECOLOR, toHex(JStudioTheme.getGraphFocusStroke()));
        stylesheet.putCellStyle("PHI", phiStyle);

        // Tainted node style - uses bright error color
        Map<String, Object> taintStyle = new HashMap<>(baseStyle);
        taintStyle.put(mxConstants.STYLE_FILLCOLOR, toHex(darker(JStudioTheme.getError(), 0.4f)));
        taintStyle.put(mxConstants.STYLE_STROKECOLOR, toHex(JStudioTheme.getError()));
        taintStyle.put(mxConstants.STYLE_STROKEWIDTH, 2);
        stylesheet.putCellStyle("TAINTED", taintStyle);

        // Edge style
        Map<String, Object> edgeStyle = new HashMap<>();
        edgeStyle.put(mxConstants.STYLE_STROKECOLOR, toHex(JStudioTheme.getGraphExternalStroke()));
        edgeStyle.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_CLASSIC);
        edgeStyle.put(mxConstants.STYLE_FONTCOLOR, toHex(JStudioTheme.getTextSecondary()));
        edgeStyle.put(mxConstants.STYLE_FONTSIZE, 9);
        stylesheet.putCellStyle("EDGE", edgeStyle);

        // Tainted edge style
        Map<String, Object> taintEdgeStyle = new HashMap<>(edgeStyle);
        taintEdgeStyle.put(mxConstants.STYLE_STROKECOLOR, toHex(JStudioTheme.getError()));
        taintEdgeStyle.put(mxConstants.STYLE_STROKEWIDTH, 2);
        stylesheet.putCellStyle("TAINT_EDGE", taintEdgeStyle);

        graph.getStylesheet().setDefaultEdgeStyle(edgeStyle);
    }

    private void setupMouseListeners() {
        graphComponent.getGraphControl().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Object cell = graphComponent.getCellAt(e.getX(), e.getY());
                if (cell == null) return;

                DataFlowNode node = cellToNodeMap.get(cell);
                if (node == null) return;

                if (e.getClickCount() == 1) {
                    showNodeInfo(node);
                } else if (e.getClickCount() == 2) {
                    highlightFlowForNode(node);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }

            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    Object cell = graphComponent.getCellAt(e.getX(), e.getY());
                    if (cell != null && cellToNodeMap.containsKey(cell)) {
                        graph.setSelectionCell(cell);
                        contextMenu.show(graphComponent, e.getX(), e.getY());
                    }
                }
            }
        });
    }

    private void setupContextMenu() {
        contextMenu = new JPopupMenu();
        contextMenu.setBackground(JStudioTheme.getBgSecondary());
        contextMenu.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));

        JMenuItem showUsesItem = new JMenuItem("Show all uses (forward flow)");
        showUsesItem.addActionListener(e -> {
            Object cell = graph.getSelectionCell();
            if (cell != null) {
                DataFlowNode node = cellToNodeMap.get(cell);
                if (node != null) {
                    highlightForwardFlow(node);
                }
            }
        });
        contextMenu.add(showUsesItem);

        JMenuItem showDefsItem = new JMenuItem("Show definitions (backward flow)");
        showDefsItem.addActionListener(e -> {
            Object cell = graph.getSelectionCell();
            if (cell != null) {
                DataFlowNode node = cellToNodeMap.get(cell);
                if (node != null) {
                    highlightBackwardFlow(node);
                }
            }
        });
        contextMenu.add(showDefsItem);

        contextMenu.addSeparator();

        JMenuItem markTaintItem = new JMenuItem("Mark as taint source");
        markTaintItem.addActionListener(e -> {
            Object cell = graph.getSelectionCell();
            if (cell != null) {
                DataFlowNode node = cellToNodeMap.get(cell);
                if (node != null) {
                    node.setTaintSource("user-marked");
                    propagateTaint();
                    visualizeGraph();
                }
            }
        });
        contextMenu.add(markTaintItem);

        JMenuItem clearTaintItem = new JMenuItem("Clear taint");
        clearTaintItem.addActionListener(e -> {
            if (currentGraph != null) {
                for (DataFlowNode node : currentGraph.getNodes()) {
                    node.setTainted(false);
                    node.setTaintSource(null);
                }
                visualizeGraph();
            }
        });
        contextMenu.add(clearTaintItem);
    }

    private void handleMethodSelected(MethodSelectedEvent event) {
        if (!isShowing()) return;

        MethodEntryModel methodModel = event.getMethodEntry();
        if (methodModel == null) return;

        // Update combo box
        String methodLabel = methodModel.getDisplaySignature();
        for (int i = 0; i < methodCombo.getItemCount(); i++) {
            if (methodCombo.getItemAt(i).equals(methodLabel)) {
                methodCombo.setSelectedIndex(i);
                break;
            }
        }

        // Set current method and build graph
        currentMethod = methodModel.getMethodEntry();
        buildDataFlowGraph();
    }

    private void updateFromMethodCombo() {
        String selected = (String) methodCombo.getSelectedItem();
        if (selected == null || selected.startsWith("(")) return;

        // Find the method in the project
        for (ClassEntryModel classEntry : project.getUserClasses()) {
            for (MethodEntryModel methodModel : classEntry.getMethods()) {
                if (methodModel.getDisplaySignature().equals(selected)) {
                    currentClass = classEntry.getClassFile();
                    currentMethod = methodModel.getMethodEntry();
                    buildDataFlowGraph();
                    return;
                }
            }
        }
    }

    /**
     * Build the data flow graph for the current method.
     */
    public void buildDataFlowGraph() {
        if (currentMethod == null || currentClass == null) {
            updateStatus("No method selected.");
            return;
        }

        if (currentMethod.getCodeAttribute() == null) {
            updateStatus("Method has no code (abstract or native).");
            return;
        }

        updateStatus("Building data flow graph...");

        SwingWorker<DataFlowGraph, Void> worker = new SwingWorker<>() {
            @Override
            protected DataFlowGraph doInBackground() {
                try {
                    SSA ssa = new SSA(currentClass.getConstPool());
                    IRMethod irMethod = ssa.lift(currentMethod);
                    if (irMethod == null) return null;

                    DataFlowGraph dfg = new DataFlowGraph(irMethod);
                    dfg.build();
                    return dfg;
                } catch (Exception e) {
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    currentGraph = get();
                    if (currentGraph == null) {
                        updateStatus("Failed to build data flow graph.");
                        return;
                    }

                    visualizeGraph();
                    updateStatus("Data flow graph built: " + currentGraph.getNodeCount() + " nodes, " +
                                currentGraph.getEdgeCount() + " edges.");
                } catch (Exception e) {
                    updateStatus("Error: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    private void visualizeGraph() {
        if (currentGraph == null) return;

        cellToNodeMap.clear();

        graph.getModel().beginUpdate();
        try {
            graph.removeCells(graph.getChildCells(graph.getDefaultParent(), true, true));

            Object parent = graph.getDefaultParent();
            Map<DataFlowNode, Object> nodeToCell = new HashMap<>();

            String filter = (String) filterCombo.getSelectedItem();
            boolean showTainted = showTaintedCheckbox.isSelected();

            // Create nodes
            for (DataFlowNode node : currentGraph.getNodes()) {
                if (!passesFilter(node, filter)) continue;

                String label = node.getLabel();
                String style = getStyleForNode(node, showTainted);

                Object cell = graph.insertVertex(parent, null, label, 0, 0, 120, 30, style);
                nodeToCell.put(node, cell);
                cellToNodeMap.put(cell, node);
            }

            // Create edges
            for (DataFlowEdge edge : currentGraph.getEdges()) {
                Object sourceCell = nodeToCell.get(edge.getSource());
                Object targetCell = nodeToCell.get(edge.getTarget());
                if (sourceCell == null || targetCell == null) continue;

                String edgeStyle = "EDGE";
                if (showTainted && edge.getSource().isTainted()) {
                    edgeStyle = "TAINT_EDGE";
                }

                graph.insertEdge(parent, null, "", sourceCell, targetCell, edgeStyle);
            }

            // Layout
            mxHierarchicalLayout layout = new mxHierarchicalLayout(graph);
            layout.setInterRankCellSpacing(40);
            layout.setIntraCellSpacing(20);
            layout.execute(parent);

        } finally {
            graph.getModel().endUpdate();
        }
    }

    private boolean passesFilter(DataFlowNode node, String filter) {
        if (filter == null || filter.equals("All Nodes")) return true;
        switch (filter) {
            case "Sources Only":
                return node.getType().isSource();
            case "Sinks Only":
                return node.getType().isSink();
            case "Parameters":
                return node.getType() == DataFlowNodeType.PARAM;
            case "Return Values":
                return node.getType() == DataFlowNodeType.RETURN;
            default:
                return true;
        }
    }

    private String getStyleForNode(DataFlowNode node, boolean showTainted) {
        if (showTainted && node.isTainted()) {
            return "TAINTED";
        }

        switch (node.getType()) {
            case PARAM:
            case FIELD_LOAD:
                return "PARAM";
            case RETURN:
            case FIELD_STORE:
            case INVOKE_ARG:
                return "SINK";
            case CONSTANT:
                return "CONSTANT";
            case PHI:
                return "PHI";
            default:
                return "DEFAULT";
        }
    }

    private void showNodeInfo(DataFlowNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append("Node: ").append(node.getLabel()).append("\n");
        sb.append("Type: ").append(node.getType().getDisplayName()).append("\n");
        sb.append("Location: ").append(node.getLocation()).append("\n");

        if (node.getSsaValue() != null && node.getSsaValue().getType() != null) {
            sb.append("IR Type: ").append(node.getSsaValue().getType()).append("\n");
        }

        if (node.isTainted()) {
            sb.append("⚠ TAINTED");
            if (node.getTaintSource() != null) {
                sb.append(" (source: ").append(node.getTaintSource()).append(")");
            }
            sb.append("\n");
        }

        List<DataFlowEdge> incoming = currentGraph.getIncomingEdges(node);
        List<DataFlowEdge> outgoing = currentGraph.getOutgoingEdges(node);
        sb.append("Incoming: ").append(incoming.size()).append(", Outgoing: ").append(outgoing.size());

        updateStatus(sb.toString());
    }

    private void highlightFlowForNode(DataFlowNode node) {
        // Highlight all nodes reachable from this node
        Set<DataFlowNode> reachable = currentGraph.getReachableNodes(node);
        highlightNodes(reachable, "Forward flow from " + node.getLabel());
    }

    private void highlightForwardFlow(DataFlowNode node) {
        Set<DataFlowNode> reachable = currentGraph.getReachableNodes(node);
        highlightNodes(reachable, "Forward flow from " + node.getLabel() + " (" + reachable.size() + " nodes)");
    }

    private void highlightBackwardFlow(DataFlowNode node) {
        Set<DataFlowNode> flowing = currentGraph.getFlowingIntoNodes(node);
        highlightNodes(flowing, "Backward flow to " + node.getLabel() + " (" + flowing.size() + " nodes)");
    }

    private void highlightNodes(Set<DataFlowNode> nodes, String message) {
        // Reset all node styles first
        visualizeGraph();

        // Highlight the selected nodes
        for (Map.Entry<Object, DataFlowNode> entry : cellToNodeMap.entrySet()) {
            if (nodes.contains(entry.getValue())) {
                graph.getModel().setStyle(entry.getKey(), "TAINTED");
            }
        }

        updateStatus(message);
    }

    private void runTaintAnalysis() {
        if (currentGraph == null) {
            updateStatus("No data flow graph. Build one first.");
            return;
        }

        // Mark parameters as taint sources
        for (DataFlowNode node : currentGraph.getNodes()) {
            if (node.getType() == DataFlowNodeType.PARAM ||
                node.getType() == DataFlowNodeType.FIELD_LOAD) {
                node.setTaintSource(node.getType().getDisplayName());
            }
        }

        propagateTaint();
        showTaintedCheckbox.setSelected(true);
        visualizeGraph();

        // Count tainted nodes
        int taintedCount = 0;
        for (DataFlowNode node : currentGraph.getNodes()) {
            if (node.isTainted()) taintedCount++;
        }

        updateStatus("Taint analysis complete. " + taintedCount + " nodes tainted.");
    }

    private void propagateTaint() {
        if (currentGraph == null) return;

        // Simple worklist-based taint propagation
        Queue<DataFlowNode> worklist = new LinkedList<>();

        // Initialize worklist with taint sources
        for (DataFlowNode node : currentGraph.getNodes()) {
            if (node.isTainted()) {
                worklist.add(node);
            }
        }

        // Propagate taint
        while (!worklist.isEmpty()) {
            DataFlowNode current = worklist.poll();

            for (DataFlowEdge edge : currentGraph.getOutgoingEdges(current)) {
                if (edge.propagatesTaint()) {
                    DataFlowNode target = edge.getTarget();
                    if (!target.isTainted()) {
                        target.setTaintSource(current.getTaintSource());
                        worklist.add(target);
                    }
                }
            }
        }
    }

    private void exportGraphAsPng() {
        if (currentGraph == null) {
            updateStatus("No graph to export.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Data Flow Graph as PNG");
        chooser.setFileFilter(new FileNameExtensionFilter("PNG Images", "png"));

        String suggestedName = currentMethod.getName() + "_dataflow.png";
        chooser.setSelectedFile(new File(suggestedName));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".png")) {
                file = new File(file.getAbsolutePath() + ".png");
            }

            try {
                BufferedImage image = mxCellRenderer.createBufferedImage(
                    graph, null, 2, JStudioTheme.getBgPrimary(), true, null);

                if (image != null) {
                    ImageIO.write(image, "PNG", file);
                    updateStatus("Graph exported to: " + file.getAbsolutePath());
                } else {
                    updateStatus("Failed to create image.");
                }
            } catch (IOException ex) {
                updateStatus("Export failed: " + ex.getMessage());
            }
        }
    }

    private void updateStatus(String message) {
        statusArea.setText(message);
    }

    /**
     * Populate the method combo box from the project.
     */
    public void populateMethodCombo() {
        methodCombo.removeAllItems();
        methodCombo.addItem("(Select method)");

        for (ClassEntryModel classEntry : project.getUserClasses()) {
            for (MethodEntryModel methodModel : classEntry.getMethods()) {
                methodCombo.addItem(methodModel.getDisplaySignature());
            }
        }
    }

    /**
     * Refresh the panel.
     */
    public void refresh() {
        populateMethodCombo();
    }

    /**
     * Set the method to visualize.
     */
    public void setMethod(ClassFile classFile, MethodEntry method) {
        this.currentClass = classFile;
        this.currentMethod = method;
        buildDataFlowGraph();
    }
}
