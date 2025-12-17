package com.tonic.ui.analysis;

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.model.mxCell;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxStylesheet;
import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.callgraph.CallGraphNode;
import com.tonic.analysis.callgraph.CallSite;
import com.tonic.analysis.common.MethodReference;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.ClassSelectedEvent;
import com.tonic.ui.event.events.MethodSelectedEvent;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Call graph visualization panel using JGraphX.
 */
public class CallGraphPanel extends JPanel {

    private final ProjectModel project;
    private final mxGraph graph;
    private final mxGraphComponent graphComponent;
    private final JTextArea statusArea;
    private final JComboBox<String> focusCombo;
    private final JSpinner depthSpinner;

    private CallGraph callGraph;
    private MethodReference focusMethod;
    private int maxDepth = 3;

    // Map graph cells back to method references for click handling
    private Map<Object, MethodReference> cellToMethodMap = new HashMap<>();
    private JPopupMenu contextMenu;

    public CallGraphPanel(ProjectModel project) {
        this.project = project;

        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgSecondary());

        // Control panel
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controlPanel.setBackground(JStudioTheme.getBgSecondary());

        JButton buildButton = new JButton("Build Graph");
        buildButton.setBackground(JStudioTheme.getBgTertiary());
        buildButton.setForeground(JStudioTheme.getTextPrimary());
        buildButton.addActionListener(e -> buildCallGraph());
        controlPanel.add(buildButton);

        controlPanel.add(new JLabel("Focus:"));
        focusCombo = new JComboBox<>();
        focusCombo.setBackground(JStudioTheme.getBgTertiary());
        focusCombo.setForeground(JStudioTheme.getTextPrimary());
        focusCombo.addActionListener(e -> updateFocus());
        controlPanel.add(focusCombo);

        controlPanel.add(new JLabel("Depth:"));
        depthSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 10, 1));
        depthSpinner.addChangeListener(e -> {
            maxDepth = (Integer) depthSpinner.getValue();
            if (focusMethod != null) {
                visualizeFocused();
            }
        });
        controlPanel.add(depthSpinner);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.setBackground(JStudioTheme.getBgTertiary());
        refreshButton.setForeground(JStudioTheme.getTextPrimary());
        refreshButton.addActionListener(e -> visualizeFocused());
        controlPanel.add(refreshButton);

        JButton exportButton = new JButton("Export PNG");
        exportButton.setBackground(JStudioTheme.getBgTertiary());
        exportButton.setForeground(JStudioTheme.getTextPrimary());
        exportButton.addActionListener(e -> exportGraphAsPng());
        controlPanel.add(exportButton);

        add(controlPanel, BorderLayout.NORTH);

        // Graph component
        graph = new mxGraph();
        graph.setHtmlLabels(true);
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

        // Setup mouse interaction for nodes
        setupMouseListeners();
        setupContextMenu();

        add(graphComponent, BorderLayout.CENTER);

        // Status area
        statusArea = new JTextArea(3, 40);
        statusArea.setEditable(false);
        statusArea.setBackground(JStudioTheme.getBgTertiary());
        statusArea.setForeground(JStudioTheme.getTextSecondary());
        statusArea.setFont(JStudioTheme.getCodeFont(11));
        statusArea.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JScrollPane statusScroll = new JScrollPane(statusArea);
        statusScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()));
        add(statusScroll, BorderLayout.SOUTH);

        updateStatus("No call graph built. Click 'Build Graph' to analyze.");

        // Register for method selection events
        EventBus.getInstance().register(MethodSelectedEvent.class, event -> {
            MethodEntryModel method = event.getMethodEntry();
            if (method != null && isShowing()) {
                focusOnMethod(method.getMethodEntry());
            }
        });
    }

    /**
     * Setup mouse listeners for node interaction.
     */
    private void setupMouseListeners() {
        graphComponent.getGraphControl().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Object cell = graphComponent.getCellAt(e.getX(), e.getY());
                if (cell == null) return;

                MethodReference method = cellToMethodMap.get(cell);
                if (method == null) return;

                if (e.getClickCount() == 2) {
                    // Double-click: focus on this method
                    focusMethod = method;
                    visualizeFocused();
                    updateComboSelection(method);
                    updateStatus("Focused on: " + method.getOwner() + "." + method.getName());
                } else if (e.getClickCount() == 1) {
                    // Single click: show method info
                    showMethodInfo(method);
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
                    if (cell != null && cellToMethodMap.containsKey(cell)) {
                        contextMenu.show(graphComponent, e.getX(), e.getY());
                    }
                }
            }
        });
    }

    /**
     * Setup context menu for graph nodes.
     */
    private void setupContextMenu() {
        contextMenu = new JPopupMenu();
        contextMenu.setBackground(JStudioTheme.getBgSecondary());
        contextMenu.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));

        JMenuItem focusItem = new JMenuItem("Focus on this method");
        focusItem.setBackground(JStudioTheme.getBgSecondary());
        focusItem.setForeground(JStudioTheme.getTextPrimary());
        focusItem.addActionListener(e -> {
            Object cell = graph.getSelectionCell();
            if (cell != null) {
                MethodReference method = cellToMethodMap.get(cell);
                if (method != null) {
                    focusMethod = method;
                    visualizeFocused();
                    updateComboSelection(method);
                }
            }
        });
        contextMenu.add(focusItem);

        JMenuItem showCallersItem = new JMenuItem("Show all callers");
        showCallersItem.setBackground(JStudioTheme.getBgSecondary());
        showCallersItem.setForeground(JStudioTheme.getTextPrimary());
        showCallersItem.addActionListener(e -> {
            Object cell = graph.getSelectionCell();
            if (cell != null && callGraph != null) {
                MethodReference method = cellToMethodMap.get(cell);
                if (method != null) {
                    Set<MethodReference> callers = callGraph.getCallers(method);
                    StringBuilder sb = new StringBuilder();
                    sb.append("Callers of ").append(method.getName()).append(":\n");
                    for (MethodReference caller : callers) {
                        sb.append("  - ").append(caller.getOwner()).append(".").append(caller.getName()).append("\n");
                    }
                    updateStatus(sb.toString());
                }
            }
        });
        contextMenu.add(showCallersItem);

        JMenuItem showCalleesItem = new JMenuItem("Show all callees");
        showCalleesItem.setBackground(JStudioTheme.getBgSecondary());
        showCalleesItem.setForeground(JStudioTheme.getTextPrimary());
        showCalleesItem.addActionListener(e -> {
            Object cell = graph.getSelectionCell();
            if (cell != null && callGraph != null) {
                MethodReference method = cellToMethodMap.get(cell);
                if (method != null) {
                    Set<MethodReference> callees = callGraph.getCallees(method);
                    StringBuilder sb = new StringBuilder();
                    sb.append("Callees of ").append(method.getName()).append(":\n");
                    for (MethodReference callee : callees) {
                        sb.append("  - ").append(callee.getOwner()).append(".").append(callee.getName()).append("\n");
                    }
                    updateStatus(sb.toString());
                }
            }
        });
        contextMenu.add(showCalleesItem);

        JMenuItem navigateItem = new JMenuItem("Navigate to source");
        navigateItem.setBackground(JStudioTheme.getBgSecondary());
        navigateItem.setForeground(JStudioTheme.getTextPrimary());
        navigateItem.addActionListener(e -> {
            Object cell = graph.getSelectionCell();
            if (cell != null) {
                MethodReference method = cellToMethodMap.get(cell);
                if (method != null) {
                    navigateToMethod(method);
                }
            }
        });
        contextMenu.add(navigateItem);
    }

    /**
     * Show info about a method in the status area.
     */
    private void showMethodInfo(MethodReference method) {
        if (callGraph == null) return;

        CallGraphNode node = callGraph.getNode(method);
        StringBuilder sb = new StringBuilder();
        sb.append(method.getOwner()).append(".").append(method.getName()).append(method.getDescriptor()).append("\n");

        if (node != null) {
            sb.append("Callers: ").append(node.getCallCount()).append(", ");
            sb.append("Callees: ").append(node.getCalleeCount()).append(", ");
            sb.append("In pool: ").append(node.isInPool() ? "yes" : "no (external)");
        }

        updateStatus(sb.toString());
    }

    /**
     * Navigate to the source of a method.
     */
    private void navigateToMethod(MethodReference method) {
        // Find the class and method in the project
        for (ClassEntryModel classEntry : project.getAllClasses()) {
            if (classEntry.getClassName().equals(method.getOwner())) {
                // Find the method
                for (MethodEntryModel methodModel : classEntry.getMethods()) {
                    MethodEntry me = methodModel.getMethodEntry();
                    if (me.getName().equals(method.getName()) && me.getDesc().equals(method.getDescriptor())) {
                        // Fire event to navigate
                        EventBus.getInstance().post(new ClassSelectedEvent(this, classEntry));
                        return;
                    }
                }
                // Method not found but class exists, navigate to class
                EventBus.getInstance().post(new ClassSelectedEvent(this, classEntry));
                return;
            }
        }
        updateStatus("Method not found in project: " + method.getOwner() + "." + method.getName());
    }

    /**
     * Update the combo box selection to match a method.
     */
    private void updateComboSelection(MethodReference method) {
        String label = method.getOwner() + "." + method.getName();
        for (int i = 0; i < focusCombo.getItemCount(); i++) {
            if (label.equals(focusCombo.getItemAt(i))) {
                focusCombo.setSelectedIndex(i);
                break;
            }
        }
    }

    private void setupGraphStyles() {
        mxStylesheet stylesheet = graph.getStylesheet();

        Map<String, Object> baseStyle = new HashMap<>();
        baseStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RECTANGLE);
        baseStyle.put(mxConstants.STYLE_ROUNDED, true);
        baseStyle.put(mxConstants.STYLE_ARCSIZE, 12);
        baseStyle.put(mxConstants.STYLE_VERTICAL_ALIGN, mxConstants.ALIGN_MIDDLE);
        baseStyle.put(mxConstants.STYLE_ALIGN, mxConstants.ALIGN_CENTER);
        baseStyle.put(mxConstants.STYLE_SPACING, 6);
        baseStyle.put(mxConstants.STYLE_FONTSIZE, 11);

        Map<String, Object> methodStyle = new HashMap<>(baseStyle);
        methodStyle.put(mxConstants.STYLE_FILLCOLOR, "#252535");
        methodStyle.put(mxConstants.STYLE_STROKECOLOR, "#7AA2F7");
        methodStyle.put(mxConstants.STYLE_FONTCOLOR, "#E4E4EF");
        methodStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
        stylesheet.putCellStyle("METHOD", methodStyle);

        Map<String, Object> focusStyle = new HashMap<>(baseStyle);
        focusStyle.put(mxConstants.STYLE_FILLCOLOR, "#3D4070");
        focusStyle.put(mxConstants.STYLE_STROKECOLOR, "#E0AF68");
        focusStyle.put(mxConstants.STYLE_FONTCOLOR, "#E4E4EF");
        focusStyle.put(mxConstants.STYLE_STROKEWIDTH, 3);
        stylesheet.putCellStyle("FOCUS", focusStyle);

        Map<String, Object> constructorStyle = new HashMap<>(baseStyle);
        constructorStyle.put(mxConstants.STYLE_FILLCOLOR, "#1E3A2F");
        constructorStyle.put(mxConstants.STYLE_STROKECOLOR, "#9ECE6A");
        constructorStyle.put(mxConstants.STYLE_FONTCOLOR, "#E4E4EF");
        constructorStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
        stylesheet.putCellStyle("CONSTRUCTOR", constructorStyle);

        Map<String, Object> staticInitStyle = new HashMap<>(baseStyle);
        staticInitStyle.put(mxConstants.STYLE_FILLCOLOR, "#2D2640");
        staticInitStyle.put(mxConstants.STYLE_STROKECOLOR, "#BB9AF7");
        staticInitStyle.put(mxConstants.STYLE_FONTCOLOR, "#E4E4EF");
        staticInitStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
        stylesheet.putCellStyle("STATIC_INIT", staticInitStyle);

        Map<String, Object> externalStyle = new HashMap<>(baseStyle);
        externalStyle.put(mxConstants.STYLE_FILLCOLOR, "#1E1E2E");
        externalStyle.put(mxConstants.STYLE_STROKECOLOR, "#565F89");
        externalStyle.put(mxConstants.STYLE_FONTCOLOR, "#9090A8");
        externalStyle.put(mxConstants.STYLE_STROKEWIDTH, 1);
        stylesheet.putCellStyle("EXTERNAL", externalStyle);

        Map<String, Object> focusConstructorStyle = new HashMap<>(constructorStyle);
        focusConstructorStyle.put(mxConstants.STYLE_STROKEWIDTH, 3);
        focusConstructorStyle.put(mxConstants.STYLE_STROKECOLOR, "#E0AF68");
        stylesheet.putCellStyle("FOCUS_CONSTRUCTOR", focusConstructorStyle);

        Map<String, Object> focusStaticInitStyle = new HashMap<>(staticInitStyle);
        focusStaticInitStyle.put(mxConstants.STYLE_STROKEWIDTH, 3);
        focusStaticInitStyle.put(mxConstants.STYLE_STROKECOLOR, "#E0AF68");
        stylesheet.putCellStyle("FOCUS_STATIC_INIT", focusStaticInitStyle);

        Map<String, Object> baseEdgeStyle = new HashMap<>();
        baseEdgeStyle.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_CLASSIC);
        baseEdgeStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
        baseEdgeStyle.put(mxConstants.STYLE_FONTSIZE, 9);

        Map<String, Object> virtualEdge = new HashMap<>(baseEdgeStyle);
        virtualEdge.put(mxConstants.STYLE_STROKECOLOR, "#7AA2F7");
        stylesheet.putCellStyle("EDGE_VIRTUAL", virtualEdge);

        Map<String, Object> staticEdge = new HashMap<>(baseEdgeStyle);
        staticEdge.put(mxConstants.STYLE_STROKECOLOR, "#BB9AF7");
        stylesheet.putCellStyle("EDGE_STATIC", staticEdge);

        Map<String, Object> specialEdge = new HashMap<>(baseEdgeStyle);
        specialEdge.put(mxConstants.STYLE_STROKECOLOR, "#9ECE6A");
        stylesheet.putCellStyle("EDGE_SPECIAL", specialEdge);

        Map<String, Object> interfaceEdge = new HashMap<>(baseEdgeStyle);
        interfaceEdge.put(mxConstants.STYLE_STROKECOLOR, "#E0AF68");
        interfaceEdge.put(mxConstants.STYLE_DASHED, true);
        stylesheet.putCellStyle("EDGE_INTERFACE", interfaceEdge);

        Map<String, Object> dynamicEdge = new HashMap<>(baseEdgeStyle);
        dynamicEdge.put(mxConstants.STYLE_STROKECOLOR, "#F7768E");
        dynamicEdge.put(mxConstants.STYLE_DASHED, true);
        dynamicEdge.put(mxConstants.STYLE_DASH_PATTERN, "3 3");
        stylesheet.putCellStyle("EDGE_DYNAMIC", dynamicEdge);

        Map<String, Object> defaultEdge = new HashMap<>(baseEdgeStyle);
        defaultEdge.put(mxConstants.STYLE_STROKECOLOR, "#565F89");
        stylesheet.putCellStyle("EDGE", defaultEdge);

        graph.getStylesheet().setDefaultEdgeStyle(defaultEdge);
    }

    /**
     * Build the call graph from the project.
     */
    public void buildCallGraph() {
        if (project.getClassPool() == null) {
            updateStatus("No project loaded. Open a JAR or class file first.");
            return;
        }

        updateStatus("Building call graph...");

        SwingWorker<CallGraph, Void> worker = new SwingWorker<CallGraph, Void>() {
            @Override
            protected CallGraph doInBackground() throws Exception {
                return CallGraph.build(project.getClassPool());
            }

            @Override
            protected void done() {
                try {
                    callGraph = get();
                    populateFocusCombo();

                    // Auto-select first method and visualize for immediate feedback
                    if (focusCombo.getItemCount() > 1) {
                        focusCombo.setSelectedIndex(1);  // Skip "(Select method to focus)"
                    }

                    updateStatus("Call graph built: " + callGraph.size() + " methods, " +
                            callGraph.edgeCount() + " edges. Select a method to explore.");
                } catch (Exception e) {
                    updateStatus("Failed to build call graph: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    private void populateFocusCombo() {
        focusCombo.removeAllItems();
        focusCombo.addItem("(Select method to focus)");

        if (callGraph == null) return;

        for (CallGraphNode node : callGraph.getPoolNodes()) {
            MethodReference ref = node.getReference();
            String label = ref.getOwner() + "." + ref.getName();
            focusCombo.addItem(label);
        }
    }

    private void updateFocus() {
        if (callGraph == null) return;

        String selected = (String) focusCombo.getSelectedItem();
        if (selected == null || selected.startsWith("(")) {
            focusMethod = null;
            return;
        }

        // Find the method reference
        for (CallGraphNode node : callGraph.getPoolNodes()) {
            MethodReference ref = node.getReference();
            String label = ref.getOwner() + "." + ref.getName();
            if (label.equals(selected)) {
                focusMethod = ref;
                visualizeFocused();
                return;
            }
        }
    }

    private void visualizeFocused() {
        if (callGraph == null || focusMethod == null) return;

        cellToMethodMap.clear();

        Set<MethodReference> callers = collectCallers(focusMethod, maxDepth);
        Set<MethodReference> callees = collectCallees(focusMethod, maxDepth);

        graph.getModel().beginUpdate();
        try {
            graph.removeCells(graph.getChildCells(graph.getDefaultParent(), true, true));

            Object parent = graph.getDefaultParent();
            Map<MethodReference, Object> nodeMap = new HashMap<>();

            String focusLabel = formatMethodLabel(focusMethod);
            int focusWidth = calculateNodeWidth(focusMethod);
            String focusStyle = getNodeStyle(focusMethod, true);
            Object focusNode = graph.insertVertex(parent, null, focusLabel,
                    0, 0, focusWidth, 45, focusStyle);
            nodeMap.put(focusMethod, focusNode);
            cellToMethodMap.put(focusNode, focusMethod);

            for (MethodReference caller : callers) {
                if (!nodeMap.containsKey(caller)) {
                    String label = formatMethodLabel(caller);
                    int width = calculateNodeWidth(caller);
                    String style = getNodeStyle(caller, false);
                    Object node = graph.insertVertex(parent, null, label,
                            0, 0, width, 45, style);
                    nodeMap.put(caller, node);
                    cellToMethodMap.put(node, caller);
                }
            }

            for (MethodReference callee : callees) {
                if (!nodeMap.containsKey(callee)) {
                    String label = formatMethodLabel(callee);
                    int width = calculateNodeWidth(callee);
                    String style = getNodeStyle(callee, false);
                    Object node = graph.insertVertex(parent, null, label,
                            0, 0, width, 45, style);
                    nodeMap.put(callee, node);
                    cellToMethodMap.put(node, callee);
                }
            }

            for (MethodReference caller : callers) {
                if (callGraph.calls(caller, focusMethod)) {
                    String edgeLabel = getEdgeTooltip(caller, focusMethod);
                    String edgeStyle = getEdgeStyleForInvoke(caller, focusMethod);
                    Object edge = graph.insertEdge(parent, null, "", nodeMap.get(caller), nodeMap.get(focusMethod), edgeStyle);
                    if (edge instanceof mxCell) {
                        ((mxCell) edge).setValue(edgeLabel);
                    }
                }
            }

            for (MethodReference callee : callees) {
                if (callGraph.calls(focusMethod, callee)) {
                    String edgeLabel = getEdgeTooltip(focusMethod, callee);
                    String edgeStyle = getEdgeStyleForInvoke(focusMethod, callee);
                    Object edge = graph.insertEdge(parent, null, "", nodeMap.get(focusMethod), nodeMap.get(callee), edgeStyle);
                    if (edge instanceof mxCell) {
                        ((mxCell) edge).setValue(edgeLabel);
                    }
                }
            }

            mxHierarchicalLayout layout = new mxHierarchicalLayout(graph);
            layout.setInterRankCellSpacing(70);
            layout.setIntraCellSpacing(40);
            layout.execute(parent);

        } finally {
            graph.getModel().endUpdate();
        }

        updateStatus("Showing call graph for: " + focusMethod.getOwner() + "." + focusMethod.getName() +
                " (" + callers.size() + " callers, " + callees.size() + " callees)");
    }

    /**
     * Get tooltip text for an edge showing call site info.
     */
    private String getEdgeTooltip(MethodReference caller, MethodReference callee) {
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

    private Set<MethodReference> collectCallers(MethodReference method, int depth) {
        Set<MethodReference> result = new LinkedHashSet<>();
        collectCallersRecursive(method, depth, result);
        return result;
    }

    private void collectCallersRecursive(MethodReference method, int depth, Set<MethodReference> result) {
        if (depth <= 0) return;
        Set<MethodReference> callers = callGraph.getCallers(method);
        for (MethodReference caller : callers) {
            if (result.add(caller)) {
                collectCallersRecursive(caller, depth - 1, result);
            }
        }
    }

    private Set<MethodReference> collectCallees(MethodReference method, int depth) {
        Set<MethodReference> result = new LinkedHashSet<>();
        collectCalleesRecursive(method, depth, result);
        return result;
    }

    private void collectCalleesRecursive(MethodReference method, int depth, Set<MethodReference> result) {
        if (depth <= 0) return;
        Set<MethodReference> callees = callGraph.getCallees(method);
        for (MethodReference callee : callees) {
            if (result.add(callee)) {
                collectCalleesRecursive(callee, depth - 1, result);
            }
        }
    }

    private String getSimpleClassName(String internalName) {
        int lastSlash = internalName.lastIndexOf('/');
        return lastSlash >= 0 ? internalName.substring(lastSlash + 1) : internalName;
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    private String formatMethodLabel(MethodReference ref) {
        String className = getSimpleClassName(ref.getOwner());
        String methodName = ref.getName();
        String topLine;
        String bottomLine;

        if ("<init>".equals(methodName)) {
            topLine = "constructor";
            bottomLine = "new " + truncate(className, 20) + "()";
        } else if ("<clinit>".equals(methodName)) {
            topLine = "initializer";
            bottomLine = "static { }";
        } else {
            topLine = truncate(className, 22);
            bottomLine = truncate(methodName, 20) + "()";
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

        int width = Math.max(100, Math.min(220, displayName.length() * 8 + 30));
        return width;
    }

    private String getNodeStyle(MethodReference ref, boolean isFocus) {
        String methodName = ref.getName();
        CallGraphNode node = callGraph.getNode(ref);
        boolean inPool = node != null && node.isInPool();

        if (!inPool) {
            return "EXTERNAL";
        }

        if ("<init>".equals(methodName)) {
            return isFocus ? "FOCUS_CONSTRUCTOR" : "CONSTRUCTOR";
        } else if ("<clinit>".equals(methodName)) {
            return isFocus ? "FOCUS_STATIC_INIT" : "STATIC_INIT";
        } else {
            return isFocus ? "FOCUS" : "METHOD";
        }
    }

    private String getEdgeStyleForInvoke(MethodReference caller, MethodReference callee) {
        CallGraphNode callerNode = callGraph.getNode(caller);
        if (callerNode == null) return "EDGE";

        for (CallSite site : callerNode.getOutgoingCalls()) {
            if (site.getTarget().equals(callee)) {
                switch (site.getInvokeType()) {
                    case VIRTUAL:
                        return "EDGE_VIRTUAL";
                    case STATIC:
                        return "EDGE_STATIC";
                    case SPECIAL:
                        return "EDGE_SPECIAL";
                    case INTERFACE:
                        return "EDGE_INTERFACE";
                    case DYNAMIC:
                        return "EDGE_DYNAMIC";
                    default:
                        return "EDGE";
                }
            }
        }
        return "EDGE";
    }

    private void updateStatus(String message) {
        statusArea.setText(message);
    }

    /**
     * Refresh the panel.
     */
    public void refresh() {
        if (callGraph != null) {
            populateFocusCombo();
        }
    }

    /**
     * Focus on a specific method.
     */
    public void focusOnMethod(MethodEntry method) {
        if (callGraph == null) {
            buildCallGraph();
        }
        this.focusMethod = new MethodReference(method.getOwnerName(), method.getName(), method.getDesc());
        visualizeFocused();

        // Update combo box
        String label = method.getOwnerName() + "." + method.getName();
        for (int i = 0; i < focusCombo.getItemCount(); i++) {
            if (label.equals(focusCombo.getItemAt(i))) {
                focusCombo.setSelectedIndex(i);
                break;
            }
        }
    }

    /**
     * Get the call graph.
     */
    public CallGraph getCallGraph() {
        return callGraph;
    }

    /**
     * Export the current graph as a PNG image.
     */
    private void exportGraphAsPng() {
        if (focusMethod == null) {
            updateStatus("No graph to export. Build a call graph and select a method first.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Call Graph as PNG");
        chooser.setFileFilter(new FileNameExtensionFilter("PNG Images", "png"));

        // Suggest a filename based on the focused method
        String suggestedName = focusMethod.getOwner().replace('/', '_') + "_" + focusMethod.getName() + "_callgraph.png";
        chooser.setSelectedFile(new File(suggestedName));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".png")) {
                file = new File(file.getAbsolutePath() + ".png");
            }

            try {
                BufferedImage image = mxCellRenderer.createBufferedImage(
                        graph, null, 2, Color.WHITE, true, null);

                if (image != null) {
                    ImageIO.write(image, "PNG", file);
                    updateStatus("Graph exported to: " + file.getAbsolutePath());
                } else {
                    updateStatus("Failed to create image - graph may be empty.");
                }
            } catch (IOException ex) {
                updateStatus("Failed to export graph: " + ex.getMessage());
            }
        }
    }
}
