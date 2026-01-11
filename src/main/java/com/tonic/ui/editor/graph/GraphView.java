package com.tonic.ui.editor.graph;

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.layout.mxCircleLayout;
import com.mxgraph.layout.mxOrganicLayout;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxStylesheet;
import com.tonic.ui.core.component.LoadingOverlay;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.theme.*;
import lombok.Getter;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public abstract class GraphView extends JPanel implements ThemeChangeListener {

    protected final ClassEntryModel classEntry;

    protected mxGraph graph;
    protected mxGraphComponent graphComponent;
    protected JTextPane dotTextPane;
    protected StyledDocument dotDoc;
    protected JScrollPane dotScrollPane;

    protected JPanel contentPanel;
    protected CardLayout cardLayout;
    protected JToolBar toolbar;

    protected JToggleButton visualBtn;
    protected JToggleButton dotBtn;
    protected JComboBox<String> layoutCombo;
    protected JComboBox<String> methodFilterCombo;

    private static final String VISUAL_CARD = "VISUAL";
    private static final String DOT_CARD = "DOT";

    protected boolean showingVisual = true;

    @Getter
    protected boolean loaded = false;

    protected boolean initializing = true;

    protected String currentDOT = "";

    protected LoadingOverlay loadingOverlay;
    protected SwingWorker<String, Void> currentWorker;

    public GraphView(ClassEntryModel classEntry) {
        this.classEntry = classEntry;
        initComponents();
        ThemeManager.getInstance().addThemeChangeListener(this);
        initializing = false;
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgTertiary());

        createToolbar();
        add(toolbar, BorderLayout.NORTH);

        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(JStudioTheme.getBgTertiary());

        createGraphComponent();
        createDOTTextPane();

        contentPanel.add(graphComponent, VISUAL_CARD);
        contentPanel.add(dotScrollPane, DOT_CARD);

        loadingOverlay = new LoadingOverlay();

        JPanel wrapperPanel = new JPanel();
        wrapperPanel.setLayout(new OverlayLayout(wrapperPanel));
        loadingOverlay.setAlignmentX(0.5f);
        loadingOverlay.setAlignmentY(0.5f);
        contentPanel.setAlignmentX(0.5f);
        contentPanel.setAlignmentY(0.5f);
        wrapperPanel.add(loadingOverlay);
        wrapperPanel.add(contentPanel);

        add(wrapperPanel, BorderLayout.CENTER);
    }

    private void createToolbar() {
        toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(JStudioTheme.getBgSecondary());
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));

        ButtonGroup viewGroup = new ButtonGroup();

        visualBtn = new JToggleButton("Visual");
        visualBtn.setSelected(true);
        visualBtn.setFont(JStudioTheme.getCodeFont(11));
        visualBtn.addActionListener(e -> switchToVisual());
        viewGroup.add(visualBtn);
        toolbar.add(visualBtn);

        dotBtn = new JToggleButton("DOT");
        dotBtn.setFont(JStudioTheme.getCodeFont(11));
        dotBtn.addActionListener(e -> switchToDOT());
        viewGroup.add(dotBtn);
        toolbar.add(dotBtn);

        toolbar.addSeparator();

        JButton zoomInBtn = new JButton(Icons.getIcon("zoom_in", 16));
        zoomInBtn.setToolTipText("Zoom In");
        zoomInBtn.addActionListener(e -> zoomIn());
        toolbar.add(zoomInBtn);

        JButton zoomOutBtn = new JButton(Icons.getIcon("zoom_out", 16));
        zoomOutBtn.setToolTipText("Zoom Out");
        zoomOutBtn.addActionListener(e -> zoomOut());
        toolbar.add(zoomOutBtn);

        JButton fitBtn = new JButton(Icons.getIcon("fit", 16));
        fitBtn.setToolTipText("Fit to Window");
        fitBtn.addActionListener(e -> fitToWindow());
        toolbar.add(fitBtn);

        toolbar.addSeparator();

        toolbar.add(new JLabel(" Layout: "));
        layoutCombo = new JComboBox<>(new String[]{"Hierarchical", "Organic", "Circular"});
        layoutCombo.setFont(JStudioTheme.getCodeFont(11));
        layoutCombo.setMaximumSize(new Dimension(120, 25));
        layoutCombo.addActionListener(e -> applyLayout((String) layoutCombo.getSelectedItem()));
        toolbar.add(layoutCombo);

        toolbar.addSeparator();

        toolbar.add(new JLabel(" Method: "));
        methodFilterCombo = new JComboBox<>(new String[]{"All Methods"});
        methodFilterCombo.setFont(JStudioTheme.getCodeFont(11));
        methodFilterCombo.setMaximumSize(new Dimension(200, 25));
        methodFilterCombo.addActionListener(e -> onMethodFilterChanged());
        toolbar.add(methodFilterCombo);

        toolbar.add(Box.createHorizontalGlue());

        JButton exportBtn = new JButton("Export DOT");
        exportBtn.setFont(JStudioTheme.getCodeFont(11));
        exportBtn.addActionListener(e -> exportDOT());
        toolbar.add(exportBtn);
    }

    private void createGraphComponent() {
        graph = new mxGraph();
        graph.setAllowDanglingEdges(false);
        graph.setEdgeLabelsMovable(false);
        graph.setVertexLabelsMovable(false);
        graph.setCellsEditable(false);
        graph.setCellsResizable(false);

        setupGraphStyles();

        graphComponent = new mxGraphComponent(graph);
        graphComponent.setConnectable(false);
        graphComponent.setDragEnabled(false);
        graphComponent.getViewport().setBackground(JStudioTheme.getBgTertiary());
        graphComponent.setBackground(JStudioTheme.getBgTertiary());
        graphComponent.setBorder(null);
        graphComponent.setToolTips(true);
    }

    protected void setupGraphStyles() {
        mxStylesheet stylesheet = graph.getStylesheet();

        Map<String, Object> nodeStyle = new HashMap<>();
        nodeStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RECTANGLE);
        nodeStyle.put(mxConstants.STYLE_ROUNDED, true);
        nodeStyle.put(mxConstants.STYLE_FILLCOLOR, toHex(JStudioTheme.getBgSecondary()));
        nodeStyle.put(mxConstants.STYLE_STROKECOLOR, toHex(JStudioTheme.getBorder()));
        nodeStyle.put(mxConstants.STYLE_FONTCOLOR, toHex(JStudioTheme.getTextPrimary()));
        nodeStyle.put(mxConstants.STYLE_FONTSIZE, 10);
        nodeStyle.put(mxConstants.STYLE_ALIGN, mxConstants.ALIGN_CENTER);
        nodeStyle.put(mxConstants.STYLE_VERTICAL_ALIGN, mxConstants.ALIGN_MIDDLE);
        stylesheet.putCellStyle("NODE", nodeStyle);

        Map<String, Object> entryStyle = new HashMap<>(nodeStyle);
        entryStyle.put(mxConstants.STYLE_FILLCOLOR, "#228B22");
        entryStyle.put(mxConstants.STYLE_FONTCOLOR, "#FFFFFF");
        stylesheet.putCellStyle("ENTRY", entryStyle);

        Map<String, Object> exitStyle = new HashMap<>(nodeStyle);
        exitStyle.put(mxConstants.STYLE_FILLCOLOR, "#DC143C");
        exitStyle.put(mxConstants.STYLE_FONTCOLOR, "#FFFFFF");
        stylesheet.putCellStyle("EXIT", exitStyle);

        Map<String, Object> phiStyle = new HashMap<>(nodeStyle);
        phiStyle.put(mxConstants.STYLE_FILLCOLOR, "#FF8C00");
        phiStyle.put(mxConstants.STYLE_FONTCOLOR, "#000000");
        stylesheet.putCellStyle("PHI", phiStyle);

        Map<String, Object> callStyle = new HashMap<>(nodeStyle);
        callStyle.put(mxConstants.STYLE_FILLCOLOR, "#8B008B");
        callStyle.put(mxConstants.STYLE_FONTCOLOR, "#FFFFFF");
        stylesheet.putCellStyle("CALL", callStyle);

        Map<String, Object> blockStyle = new HashMap<>(nodeStyle);
        blockStyle.put(mxConstants.STYLE_FILLCOLOR, "#4682B4");
        blockStyle.put(mxConstants.STYLE_FONTCOLOR, "#FFFFFF");
        stylesheet.putCellStyle("BLOCK", blockStyle);

        Map<String, Object> controlEdge = new HashMap<>();
        controlEdge.put(mxConstants.STYLE_STROKECOLOR, "#FF6347");
        controlEdge.put(mxConstants.STYLE_DASHED, true);
        controlEdge.put(mxConstants.STYLE_FONTCOLOR, toHex(JStudioTheme.getTextSecondary()));
        controlEdge.put(mxConstants.STYLE_FONTSIZE, 9);
        stylesheet.putCellStyle("CONTROL", controlEdge);

        Map<String, Object> dataEdge = new HashMap<>();
        dataEdge.put(mxConstants.STYLE_STROKECOLOR, "#4169E1");
        dataEdge.put(mxConstants.STYLE_FONTCOLOR, toHex(JStudioTheme.getTextSecondary()));
        dataEdge.put(mxConstants.STYLE_FONTSIZE, 9);
        stylesheet.putCellStyle("DATA", dataEdge);

        Map<String, Object> cfgEdge = new HashMap<>();
        cfgEdge.put(mxConstants.STYLE_STROKECOLOR, "#32CD32");
        cfgEdge.put(mxConstants.STYLE_FONTCOLOR, toHex(JStudioTheme.getTextSecondary()));
        cfgEdge.put(mxConstants.STYLE_FONTSIZE, 9);
        stylesheet.putCellStyle("CFG", cfgEdge);
    }

    private void createDOTTextPane() {
        dotTextPane = new JTextPane();
        dotTextPane.setEditable(false);
        dotTextPane.setBackground(JStudioTheme.getBgTertiary());
        dotTextPane.setForeground(JStudioTheme.getTextPrimary());
        dotTextPane.setCaretColor(JStudioTheme.getTextPrimary());
        dotTextPane.setFont(JStudioTheme.getCodeFont(12));

        dotDoc = dotTextPane.getStyledDocument();

        dotScrollPane = new JScrollPane(dotTextPane);
        dotScrollPane.setBorder(null);
        dotScrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        SwingUtilities.invokeLater(this::applyTheme);
    }

    private void applyTheme() {
        setBackground(JStudioTheme.getBgTertiary());
        toolbar.setBackground(JStudioTheme.getBgSecondary());
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));
        contentPanel.setBackground(JStudioTheme.getBgTertiary());

        graphComponent.getViewport().setBackground(JStudioTheme.getBgTertiary());
        graphComponent.setBackground(JStudioTheme.getBgTertiary());
        setupGraphStyles();

        dotTextPane.setBackground(JStudioTheme.getBgTertiary());
        dotTextPane.setForeground(JStudioTheme.getTextPrimary());
        dotTextPane.setCaretColor(JStudioTheme.getTextPrimary());
        dotScrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());

        repaint();
    }

    public void refresh() {
        if (loaded) {
            return;
        }

        cancelCurrentWorker();

        loadingOverlay.showLoading("Building graph...");
        graphComponent.setEnabled(false);

        currentWorker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                prepareGraphData();
                return generateDOT();
            }

            @Override
            protected void done() {
                loadingOverlay.hideLoading();
                graphComponent.setEnabled(true);

                if (isCancelled()) {
                    return;
                }

                try {
                    currentDOT = get();
                    renderGraph();
                    applyLayout((String) layoutCombo.getSelectedItem());
                    updateDOTView();
                    loaded = true;
                } catch (Exception ex) {
                    showError("Failed to build graph: " + ex.getMessage());
                }
            }
        };

        currentWorker.execute();
    }

    private void cancelCurrentWorker() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
            loadingOverlay.hideLoading();
        }
    }

    protected abstract void prepareGraphData();

    protected abstract void renderGraph();

    protected abstract String generateDOT();

    protected void onMethodFilterChanged() {
        if (initializing) {
            return;
        }
        loaded = false;
        refresh();
    }

    protected void populateMethodFilter() {
        initializing = true;
        try {
            methodFilterCombo.removeAllItems();
            methodFilterCombo.addItem("All Methods");
            for (var method : classEntry.getMethods()) {
                methodFilterCombo.addItem(method.getName() + method.getMethodEntry().getDesc());
            }
        } finally {
            initializing = false;
        }
    }

    public void switchToVisual() {
        showingVisual = true;
        visualBtn.setSelected(true);
        cardLayout.show(contentPanel, VISUAL_CARD);
    }

    public void switchToDOT() {
        showingVisual = false;
        dotBtn.setSelected(true);
        updateDOTView();
        cardLayout.show(contentPanel, DOT_CARD);
    }

    private void updateDOTView() {
        try {
            dotDoc.remove(0, dotDoc.getLength());
            SimpleAttributeSet style = new SimpleAttributeSet();
            StyleConstants.setForeground(style, JStudioTheme.getTextPrimary());
            StyleConstants.setFontFamily(style, JStudioTheme.getCodeFont(12).getFamily());
            StyleConstants.setFontSize(style, 12);
            dotDoc.insertString(0, currentDOT, style);
        } catch (BadLocationException e) {
            // Ignore
        }
    }

    public void zoomIn() {
        graphComponent.zoomIn();
    }

    public void zoomOut() {
        graphComponent.zoomOut();
    }

    public void fitToWindow() {
        graphComponent.zoomActual();
        double newScale = Math.min(
            (double) graphComponent.getWidth() / graph.getGraphBounds().getWidth(),
            (double) graphComponent.getHeight() / graph.getGraphBounds().getHeight()
        );
        if (newScale > 0 && Double.isFinite(newScale)) {
            graphComponent.zoomTo(newScale * 0.9, false);
        }
    }

    public void applyLayout(String layoutType) {
        if (layoutType == null || graph.getChildVertices(graph.getDefaultParent()).length == 0) {
            return;
        }

        Object parent = graph.getDefaultParent();
        graph.getModel().beginUpdate();
        try {
            switch (layoutType) {
                case "Hierarchical":
                    mxHierarchicalLayout hierarchical = new mxHierarchicalLayout(graph);
                    hierarchical.setInterRankCellSpacing(50);
                    hierarchical.setIntraCellSpacing(30);
                    hierarchical.execute(parent);
                    break;
                case "Organic":
                    mxOrganicLayout organic = new mxOrganicLayout(graph);
                    organic.execute(parent);
                    break;
                case "Circular":
                    mxCircleLayout circle = new mxCircleLayout(graph);
                    circle.execute(parent);
                    break;
            }
        } finally {
            graph.getModel().endUpdate();
        }
    }

    public void exportDOT() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(classEntry.getSimpleName() + ".dot"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(currentDOT);
                JOptionPane.showMessageDialog(this,
                    "Exported to: " + file.getAbsolutePath(),
                    "Export Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                    "Failed to export: " + e.getMessage(),
                    "Export Failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    protected void showError(String message) {
        try {
            dotDoc.remove(0, dotDoc.getLength());
            SimpleAttributeSet style = new SimpleAttributeSet();
            StyleConstants.setForeground(style, Color.RED);
            dotDoc.insertString(0, "// Error: " + message, style);
        } catch (BadLocationException e) {
            // Ignore
        }
    }

    protected void clearGraph() {
        graph.getModel().beginUpdate();
        try {
            graph.removeCells(graph.getChildCells(graph.getDefaultParent(), true, true));
        } finally {
            graph.getModel().endUpdate();
        }
    }

    protected static String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    public String getText() {
        return currentDOT;
    }

    public void copySelection() {
        if (showingVisual) {
            return;
        }
        String selected = dotTextPane.getSelectedText();
        if (selected != null && !selected.isEmpty()) {
            StringSelection selection = new StringSelection(selected);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        }
    }

    public String getSelectedText() {
        if (showingVisual) {
            return null;
        }
        return dotTextPane.getSelectedText();
    }

    public void goToLine(int line) {
        if (showingVisual) return;
        try {
            int offset = dotTextPane.getDocument().getDefaultRootElement().getElement(line - 1).getStartOffset();
            dotTextPane.setCaretPosition(offset);
            dotTextPane.requestFocus();
        } catch (Exception e) {
            // Line out of range
        }
    }

    public void showFindDialog() {
        // Not implemented for graph view
    }

    public void scrollToText(String searchText) {
        if (showingVisual || searchText == null || searchText.isEmpty()) return;
        String text = dotTextPane.getText();
        int index = text.toLowerCase().indexOf(searchText.toLowerCase());
        if (index >= 0) {
            dotTextPane.setCaretPosition(index);
            dotTextPane.select(index, index + searchText.length());
            dotTextPane.requestFocus();
        }
    }

    public void setFontSize(int size) {
        dotTextPane.setFont(JStudioTheme.getCodeFont(size));
    }

    public void setWordWrap(boolean enabled) {
        // No-op for graph view
    }
}
