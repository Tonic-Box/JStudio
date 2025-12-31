package com.tonic.ui.analysis;

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxStylesheet;
import com.tonic.analysis.dependency.DependencyAnalyzer;
import com.tonic.analysis.dependency.DependencyNode;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.util.JdkClassFilter;
import lombok.Getter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.imageio.ImageIO;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DependencyPanel extends ThemedJPanel {

    private final ProjectModel project;
    private final mxGraph graph;
    private final JTextArea statusArea;
    private final JComboBox<String> focusCombo;
    private final JSpinner depthSpinner;

    /**
     * -- GETTER --
     *  Get the dependency analyzer.
     */
    @Getter
    private DependencyAnalyzer analyzer;
    private String focusClass;
    private int maxDepth = 2;

    public DependencyPanel(ProjectModel project) {
        super(BackgroundStyle.SECONDARY, new BorderLayout());
        this.project = project;

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_SMALL));
        controlPanel.setBackground(JStudioTheme.getBgSecondary());

        JButton analyzeButton = new JButton("Analyze");
        analyzeButton.setBackground(JStudioTheme.getBgTertiary());
        analyzeButton.setForeground(JStudioTheme.getTextPrimary());
        analyzeButton.addActionListener(e -> analyze());
        controlPanel.add(analyzeButton);

        controlPanel.add(new JLabel("Focus:"));
        focusCombo = new JComboBox<>();
        focusCombo.setBackground(JStudioTheme.getBgTertiary());
        focusCombo.setForeground(JStudioTheme.getTextPrimary());
        focusCombo.addActionListener(e -> updateFocus());
        controlPanel.add(focusCombo);

        controlPanel.add(new JLabel("Depth:"));
        depthSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 5, 1));
        depthSpinner.addChangeListener(e -> {
            maxDepth = (Integer) depthSpinner.getValue();
            if (focusClass != null) {
                visualizeFocused();
            }
        });
        controlPanel.add(depthSpinner);

        JButton cyclesButton = new JButton("Find Cycles");
        cyclesButton.setBackground(JStudioTheme.getBgTertiary());
        cyclesButton.setForeground(JStudioTheme.getTextPrimary());
        cyclesButton.addActionListener(e -> findCycles());
        controlPanel.add(cyclesButton);

        JButton exportButton = new JButton("Export PNG");
        exportButton.setBackground(JStudioTheme.getBgTertiary());
        exportButton.setForeground(JStudioTheme.getTextPrimary());
        exportButton.addActionListener(e -> exportGraphAsPng());
        controlPanel.add(exportButton);

        add(controlPanel, BorderLayout.NORTH);

        // Graph component
        graph = new mxGraph();
        setupGraphStyles();
        graph.setAutoSizeCells(true);
        graph.setCellsEditable(false);
        graph.setCellsMovable(true);
        graph.setCellsResizable(false);

        mxGraphComponent graphComponent = new mxGraphComponent(graph);
        graphComponent.setBackground(JStudioTheme.getBgTertiary());
        graphComponent.getViewport().setBackground(JStudioTheme.getBgTertiary());
        graphComponent.setBorder(null);

        add(graphComponent, BorderLayout.CENTER);

        statusArea = new JTextArea(3, 40);
        statusArea.setEditable(false);
        statusArea.setBackground(JStudioTheme.getBgTertiary());
        statusArea.setForeground(JStudioTheme.getTextSecondary());
        statusArea.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE));
        statusArea.setBorder(BorderFactory.createEmptyBorder(UIConstants.SPACING_SMALL, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_SMALL, UIConstants.SPACING_MEDIUM));

        JScrollPane statusScroll = new JScrollPane(statusArea);
        statusScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()));
        add(statusScroll, BorderLayout.SOUTH);

        updateStatus("No dependency analysis. Click 'Analyze' to scan dependencies.");
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private void setupGraphStyles() {
        mxStylesheet stylesheet = graph.getStylesheet();
        Map<String, Object> style = new HashMap<>();

        // Class node style
        style.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RECTANGLE);
        style.put(mxConstants.STYLE_ROUNDED, true);
        style.put(mxConstants.STYLE_FILLCOLOR, toHex(JStudioTheme.getGraphNodeFill()));
        style.put(mxConstants.STYLE_STROKECOLOR, toHex(JStudioTheme.getGraphConstructorStroke()));
        style.put(mxConstants.STYLE_FONTCOLOR, toHex(JStudioTheme.getTextPrimary()));
        style.put(mxConstants.STYLE_FONTSIZE, 10);
        style.put(mxConstants.STYLE_SPACING, 4);
        stylesheet.putCellStyle("CLASS", style);

        // Focus node style
        Map<String, Object> focusStyle = new HashMap<>(style);
        focusStyle.put(mxConstants.STYLE_FILLCOLOR, toHex(JStudioTheme.getGraphFocusFill()));
        focusStyle.put(mxConstants.STYLE_STROKECOLOR, toHex(JStudioTheme.getGraphFocusStroke()));
        focusStyle.put(mxConstants.STYLE_STROKEWIDTH, 2);
        stylesheet.putCellStyle("FOCUS", focusStyle);

        // External class style
        Map<String, Object> externalStyle = new HashMap<>(style);
        externalStyle.put(mxConstants.STYLE_FILLCOLOR, toHex(JStudioTheme.getGraphExternalFill()));
        externalStyle.put(mxConstants.STYLE_STROKECOLOR, toHex(JStudioTheme.getGraphExternalStroke()));
        externalStyle.put(mxConstants.STYLE_FONTCOLOR, toHex(JStudioTheme.getTextSecondary()));
        stylesheet.putCellStyle("EXTERNAL", externalStyle);

        // Cycle node style (uses error color)
        Map<String, Object> cycleStyle = new HashMap<>(style);
        cycleStyle.put(mxConstants.STYLE_FILLCOLOR, toHex(darker(JStudioTheme.getError())));
        cycleStyle.put(mxConstants.STYLE_STROKECOLOR, toHex(JStudioTheme.getError()));
        stylesheet.putCellStyle("CYCLE", cycleStyle);

        // Edge style
        Map<String, Object> edgeStyle = new HashMap<>();
        edgeStyle.put(mxConstants.STYLE_STROKECOLOR, toHex(JStudioTheme.getGraphExternalStroke()));
        edgeStyle.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_CLASSIC);
        stylesheet.putCellStyle("EDGE", edgeStyle);

        graph.getStylesheet().setDefaultEdgeStyle(edgeStyle);
    }

    private static Color darker(Color c) {
        return new Color(
            Math.max(0, (int)(c.getRed() * (float) 0.3)),
            Math.max(0, (int)(c.getGreen() * (float) 0.3)),
            Math.max(0, (int)(c.getBlue() * (float) 0.3))
        );
    }

    /**
     * Analyze dependencies.
     */
    public void analyze() {
        if (project.getClassPool() == null) {
            updateStatus("No project loaded. Open a JAR or class file first.");
            return;
        }

        updateStatus("Analyzing dependencies...");

        SwingWorker<DependencyAnalyzer, Void> worker = new SwingWorker<>() {
            @Override
            protected DependencyAnalyzer doInBackground() {
                return new DependencyAnalyzer(project.getClassPool());
            }

            @Override
            protected void done() {
                try {
                    analyzer = get();
                    populateFocusCombo();
                    updateStatus("Dependency analysis complete: " + analyzer.size() + " classes, " +
                            analyzer.edgeCount() + " edges");
                } catch (Exception e) {
                    updateStatus("Failed to analyze dependencies: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    private void populateFocusCombo() {
        focusCombo.removeAllItems();
        focusCombo.addItem("(Select class to focus)");

        if (analyzer == null) return;

        for (DependencyNode node : analyzer.getPoolNodes()) {
            if (JdkClassFilter.isJdkClass(node.getClassName())) {
                continue;
            }
            focusCombo.addItem(node.getClassName());
        }
    }

    private void updateFocus() {
        String selected = (String) focusCombo.getSelectedItem();
        if (selected == null || selected.startsWith("(")) {
            focusClass = null;
            return;
        }

        focusClass = selected;
        visualizeFocused();
    }

    private void visualizeFocused() {
        if (analyzer == null || focusClass == null) return;

        graph.getModel().beginUpdate();
        try {
            // Clear existing cells
            graph.removeCells(graph.getChildCells(graph.getDefaultParent(), true, true));

            Object parent = graph.getDefaultParent();
            Map<String, Object> nodeMap = new HashMap<>();

            // Get dependencies and dependents up to max depth
            Set<String> dependencies = collectDependencies(focusClass, maxDepth);
            Set<String> dependents = collectDependents(focusClass, maxDepth);

            // Create focus node
            String focusLabel = formatClassName(focusClass);
            Object focusNode = graph.insertVertex(parent, null, focusLabel,
                    0, 0, 120, 30, "FOCUS");
            nodeMap.put(focusClass, focusNode);

            // Create dependency nodes
            for (String dep : dependencies) {
                if (!nodeMap.containsKey(dep)) {
                    String label = formatClassName(dep);
                    DependencyNode depNode = analyzer.getNode(dep);
                    String style = (depNode != null && depNode.isInPool()) ? "CLASS" : "EXTERNAL";
                    Object node = graph.insertVertex(parent, null, label,
                            0, 0, 120, 30, style);
                    nodeMap.put(dep, node);
                }
            }

            // Create dependent nodes
            for (String dep : dependents) {
                if (!nodeMap.containsKey(dep)) {
                    String label = formatClassName(dep);
                    DependencyNode depNode = analyzer.getNode(dep);
                    String style = (depNode != null && depNode.isInPool()) ? "CLASS" : "EXTERNAL";
                    Object node = graph.insertVertex(parent, null, label,
                            0, 0, 120, 30, style);
                    nodeMap.put(dep, node);
                }
            }

            // Create edges: focus -> dependencies
            DependencyNode focusNode2 = analyzer.getNode(focusClass);
            if (focusNode2 != null) {
                for (String dep : focusNode2.getDependencies()) {
                    if (nodeMap.containsKey(dep)) {
                        graph.insertEdge(parent, null, "", nodeMap.get(focusClass), nodeMap.get(dep), "EDGE");
                    }
                }
            }

            // Create edges: dependents -> focus
            for (String dep : dependents) {
                if (analyzer.dependsOn(dep, focusClass)) {
                    graph.insertEdge(parent, null, "", nodeMap.get(dep), nodeMap.get(focusClass), "EDGE");
                }
            }

            // Layout
            mxHierarchicalLayout layout = new mxHierarchicalLayout(graph);
            layout.setInterRankCellSpacing(50);
            layout.setIntraCellSpacing(30);
            layout.execute(parent);

        } finally {
            graph.getModel().endUpdate();
        }

        updateStatus("Showing dependencies for: " + focusClass +
                " (" + collectDependencies(focusClass, maxDepth).size() + " dependencies, " +
                collectDependents(focusClass, maxDepth).size() + " dependents)");
    }

    private void findCycles() {
        if (analyzer == null) {
            updateStatus("Run dependency analysis first.");
            return;
        }

        List<List<String>> cycles = analyzer.findCircularDependencies();

        if (cycles.isEmpty()) {
            updateStatus("No circular dependencies found.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(cycles.size()).append(" circular dependencies:\n");
        int count = 0;
        for (List<String> cycle : cycles) {
            if (count++ >= 5) {
                sb.append("... and ").append(cycles.size() - 5).append(" more\n");
                break;
            }
            sb.append("  ");
            for (int i = 0; i < cycle.size(); i++) {
                sb.append(formatClassName(cycle.get(i)));
                if (i < cycle.size() - 1) {
                    sb.append(" -> ");
                }
            }
            sb.append("\n");
        }

        updateStatus(sb.toString());
    }

    private Set<String> collectDependencies(String className, int depth) {
        Set<String> result = new LinkedHashSet<>();
        collectDependenciesRecursive(className, depth, result);
        return result;
    }

    private void collectDependenciesRecursive(String className, int depth, Set<String> result) {
        if (depth <= 0) return;
        DependencyNode node = analyzer.getNode(className);
        if (node == null) return;

        for (String dep : node.getDependencies()) {
            if (result.add(dep)) {
                collectDependenciesRecursive(dep, depth - 1, result);
            }
        }
    }

    private Set<String> collectDependents(String className, int depth) {
        Set<String> result = new LinkedHashSet<>();
        collectDependentsRecursive(className, depth, result);
        return result;
    }

    private void collectDependentsRecursive(String className, int depth, Set<String> result) {
        if (depth <= 0) return;
        DependencyNode node = analyzer.getNode(className);
        if (node == null) return;

        for (String dep : node.getDependents()) {
            if (result.add(dep)) {
                collectDependentsRecursive(dep, depth - 1, result);
            }
        }
    }

    private String formatClassName(String className) {
        if (className == null) return "?";
        int lastSlash = className.lastIndexOf('/');
        if (lastSlash >= 0) {
            return className.substring(lastSlash + 1);
        }
        int lastDot = className.lastIndexOf('.');
        if (lastDot >= 0) {
            return className.substring(lastDot + 1);
        }
        return className;
    }

    private void updateStatus(String message) {
        statusArea.setText(message);
    }

    /**
     * Refresh the panel.
     */
    public void refresh() {
        if (analyzer != null) {
            populateFocusCombo();
        }
    }

    /**
     * Focus on a specific class.
     */
    public void focusOnClass(String className) {
        if (analyzer == null) {
            analyze();
        }
        this.focusClass = className;
        visualizeFocused();

        for (int i = 0; i < focusCombo.getItemCount(); i++) {
            if (className.equals(focusCombo.getItemAt(i))) {
                focusCombo.setSelectedIndex(i);
                break;
            }
        }
    }

    /**
     * Build the dependency graph (alias for analyze).
     */
    public void buildDependencyGraph() {
        if (analyzer == null) {
            analyze();
        }
    }

    /**
     * Export the current graph as a PNG image.
     */
    private void exportGraphAsPng() {
        if (focusClass == null) {
            updateStatus("No graph to export. Analyze dependencies and select a class first.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Dependency Graph as PNG");
        chooser.setFileFilter(new FileNameExtensionFilter("PNG Images", "png"));

        // Suggest a filename based on the focused class
        String suggestedName = focusClass.replace('/', '_') + "_dependencies.png";
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
                    updateStatus("Failed to create image - graph may be empty.");
                }
            } catch (IOException ex) {
                updateStatus("Failed to export graph: " + ex.getMessage());
            }
        }
    }
}
