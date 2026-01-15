package com.tonic.ui.editor.cfg;

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxStylesheet;
import com.tonic.ui.core.component.LoadingOverlay;
import com.tonic.ui.event.Event;
import com.tonic.ui.event.EventBus;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ControlFlowView extends JPanel implements ThemeChangeListener {

    private final ClassEntryModel classEntry;
    private final CFGBuilder cfgBuilder;

    private mxGraph graph;
    private mxGraphComponent graphComponent;
    private JComboBox<MethodEntryModel> methodSelector;
    private JToolBar toolbar;
    private LoadingOverlay loadingOverlay;

    private MethodEntryModel currentMethod;
    private List<CFGBlock> currentBlocks;
    private boolean showIR = false;

    @Getter
    private boolean loaded = false;

    public ControlFlowView(ClassEntryModel classEntry) {
        this.classEntry = classEntry;
        this.cfgBuilder = new CFGBuilder();

        initComponents();
        ThemeManager.getInstance().addThemeChangeListener(this);
        applyTheme();
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgTertiary());

        createToolbar();
        add(toolbar, BorderLayout.NORTH);

        createGraph();

        loadingOverlay = new LoadingOverlay();
        JPanel wrapperPanel = new JPanel();
        wrapperPanel.setLayout(new OverlayLayout(wrapperPanel));
        loadingOverlay.setAlignmentX(0.5f);
        loadingOverlay.setAlignmentY(0.5f);
        graphComponent.setAlignmentX(0.5f);
        graphComponent.setAlignmentY(0.5f);
        wrapperPanel.add(loadingOverlay);
        wrapperPanel.add(graphComponent);

        add(wrapperPanel, BorderLayout.CENTER);
        populateMethodSelector();
    }

    private void createToolbar() {
        toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(JStudioTheme.getBgSecondary());
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));

        toolbar.add(new JLabel(" Method: "));
        methodSelector = new JComboBox<>();
        methodSelector.setFont(JStudioTheme.getCodeFont(11));
        methodSelector.setMaximumSize(new Dimension(300, 25));
        methodSelector.setRenderer(new MethodListRenderer());
        methodSelector.addActionListener(e -> onMethodSelected());
        toolbar.add(methodSelector);

        toolbar.addSeparator();

        ButtonGroup modeGroup = new ButtonGroup();
        JToggleButton bytecodeToggle = new JToggleButton("Bytecode", true);
        bytecodeToggle.setFont(JStudioTheme.getCodeFont(11));
        bytecodeToggle.addActionListener(e -> {
            showIR = false;
            rebuildGraph();
        });
        modeGroup.add(bytecodeToggle);
        toolbar.add(bytecodeToggle);

        JToggleButton irToggle = new JToggleButton("IR");
        irToggle.setFont(JStudioTheme.getCodeFont(11));
        irToggle.addActionListener(e -> {
            showIR = true;
            rebuildGraph();
        });
        modeGroup.add(irToggle);
        toolbar.add(irToggle);

        toolbar.addSeparator();

        JButton zoomInBtn = new JButton(Icons.getIcon("zoom_in", 16));
        zoomInBtn.setToolTipText("Zoom In (Ctrl+Wheel)");
        zoomInBtn.addActionListener(e -> graphComponent.zoomIn());
        toolbar.add(zoomInBtn);

        JButton zoomOutBtn = new JButton(Icons.getIcon("zoom_out", 16));
        zoomOutBtn.setToolTipText("Zoom Out (Ctrl+Wheel)");
        zoomOutBtn.addActionListener(e -> graphComponent.zoomOut());
        toolbar.add(zoomOutBtn);

        JButton fitBtn = new JButton(Icons.getIcon("fit", 16));
        fitBtn.setToolTipText("Fit to Window");
        fitBtn.addActionListener(e -> fitToWindow());
        toolbar.add(fitBtn);

        toolbar.add(Box.createHorizontalGlue());
    }

    private void createGraph() {
        graph = new mxGraph();
        graph.setAllowDanglingEdges(false);
        graph.setEdgeLabelsMovable(false);
        graph.setVertexLabelsMovable(false);
        graph.setCellsEditable(false);
        graph.setCellsResizable(false);
        graph.setAutoSizeCells(true);
        graph.setHtmlLabels(true);

        setupGraphStyles();

        graphComponent = new mxGraphComponent(graph);
        graphComponent.setConnectable(false);
        graphComponent.setDragEnabled(false);
        graphComponent.getViewport().setBackground(JStudioTheme.getBgTertiary());
        graphComponent.setBackground(JStudioTheme.getBgTertiary());
        graphComponent.setBorder(null);
        graphComponent.setToolTips(true);

        graphComponent.getGraphControl().addMouseWheelListener(this::handleMouseWheel);
        graphComponent.getGraphControl().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleDoubleClick(e);
                }
            }
        });
    }

    private void setupGraphStyles() {
        mxStylesheet stylesheet = graph.getStylesheet();

        Map<String, Object> vertexStyle = new HashMap<>();
        vertexStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RECTANGLE);
        vertexStyle.put(mxConstants.STYLE_ROUNDED, true);
        vertexStyle.put(mxConstants.STYLE_ARCSIZE, 8);
        vertexStyle.put(mxConstants.STYLE_FILLCOLOR, toHex(JStudioTheme.getBgSecondary()));
        vertexStyle.put(mxConstants.STYLE_STROKECOLOR, toHex(JStudioTheme.getBorder()));
        vertexStyle.put(mxConstants.STYLE_FONTCOLOR, toHex(JStudioTheme.getTextPrimary()));
        vertexStyle.put(mxConstants.STYLE_FONTSIZE, 10);
        vertexStyle.put(mxConstants.STYLE_ALIGN, mxConstants.ALIGN_LEFT);
        vertexStyle.put(mxConstants.STYLE_VERTICAL_ALIGN, mxConstants.ALIGN_TOP);
        vertexStyle.put(mxConstants.STYLE_SPACING, 4);
        stylesheet.putCellStyle("BLOCK", vertexStyle);

        Map<String, Object> entryStyle = new HashMap<>(vertexStyle);
        entryStyle.put(mxConstants.STYLE_STROKECOLOR, "#27ae60");
        entryStyle.put(mxConstants.STYLE_STROKEWIDTH, 2);
        stylesheet.putCellStyle("ENTRY", entryStyle);

        Map<String, Object> handlerStyle = new HashMap<>(vertexStyle);
        handlerStyle.put(mxConstants.STYLE_STROKECOLOR, "#e67e22");
        handlerStyle.put(mxConstants.STYLE_STROKEWIDTH, 2);
        stylesheet.putCellStyle("HANDLER", handlerStyle);

        Map<String, Object> defaultEdge = stylesheet.getDefaultEdgeStyle();
        defaultEdge.put(mxConstants.STYLE_ROUNDED, true);
        defaultEdge.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
        defaultEdge.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_CLASSIC);
    }

    private void populateMethodSelector() {
        methodSelector.removeAllItems();
        for (MethodEntryModel method : classEntry.getMethods()) {
            if (method.getMethodEntry().getCodeAttribute() != null) {
                methodSelector.addItem(method);
            }
        }
    }

    private void onMethodSelected() {
        currentMethod = (MethodEntryModel) methodSelector.getSelectedItem();
        if (currentMethod != null) {
            buildCFG();
        }
    }

    private void buildCFG() {
        if (currentMethod == null) return;

        loadingOverlay.showLoading("Building CFG...");

        SwingWorker<List<CFGBlock>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<CFGBlock> doInBackground() {
                return cfgBuilder.buildCFG(currentMethod.getMethodEntry());
            }

            @Override
            protected void done() {
                try {
                    currentBlocks = get();
                    rebuildGraph();
                } catch (Exception e) {
                    showError("Failed to build CFG: " + e.getMessage());
                } finally {
                    loadingOverlay.hideLoading();
                }
            }
        };
        worker.execute();
    }

    private void rebuildGraph() {
        if (currentBlocks == null || currentBlocks.isEmpty()) {
            clearGraph();
            return;
        }

        graph.getModel().beginUpdate();
        try {
            graph.removeCells(graph.getChildCells(graph.getDefaultParent(), true, true));

            Map<CFGBlock, Object> cellMap = new HashMap<>();
            Object parent = graph.getDefaultParent();

            for (CFGBlock block : currentBlocks) {
                CFGBlockVertex vertex = new CFGBlockVertex(block, currentMethod.getMethodEntry(),
                        showIR, classEntry.getClassFile().getConstPool());

                String style = getBlockStyle(block);
                Object cell = graph.insertVertex(parent, null, vertex, 0, 0, 150, 60, style);
                graph.updateCellSize(cell);
                cellMap.put(block, cell);
            }

            for (CFGBlock block : currentBlocks) {
                Object source = cellMap.get(block);
                for (CFGEdge edge : block.getOutEdges()) {
                    Object target = cellMap.get(edge.getTarget());
                    if (target != null) {
                        String edgeStyle = "strokeColor=" + edge.getType().getColor();
                        graph.insertEdge(parent, null, null, source, target, edgeStyle);
                    }
                }
            }

            mxHierarchicalLayout layout = new mxHierarchicalLayout(graph);
            layout.setInterRankCellSpacing(50);
            layout.setIntraCellSpacing(30);
            layout.execute(parent);

        } finally {
            graph.getModel().endUpdate();
        }

        loaded = true;
    }

    private String getBlockStyle(CFGBlock block) {
        if (block.getStartOffset() == 0) {
            return "ENTRY";
        } else if (block.isExceptionHandler()) {
            return "HANDLER";
        }
        return "BLOCK";
    }

    private void handleMouseWheel(MouseWheelEvent e) {
        if (e.isControlDown()) {
            if (e.getWheelRotation() < 0) {
                graphComponent.zoomIn();
            } else {
                graphComponent.zoomOut();
            }
            e.consume();
        }
    }

    private void handleDoubleClick(MouseEvent e) {
        Object cell = graphComponent.getCellAt(e.getX(), e.getY());
        if (cell != null) {
            Object value = graph.getModel().getValue(cell);
            if (value instanceof CFGBlockVertex) {
                CFGBlockVertex vertex = (CFGBlockVertex) value;
                navigateToBytecode(vertex.getBlock().getStartOffset());
            }
        }
    }

    private void navigateToBytecode(int offset) {
        EventBus.getInstance().post(new NavigateToBytecodeRequest(
                classEntry, currentMethod, offset));
    }

    private void fitToWindow() {
        graphComponent.zoomActual();
        double newScale = Math.min(
                (double) graphComponent.getWidth() / graph.getGraphBounds().getWidth(),
                (double) graphComponent.getHeight() / graph.getGraphBounds().getHeight()
        );
        if (newScale > 0 && Double.isFinite(newScale)) {
            graphComponent.zoomTo(newScale * 0.9, false);
        }
    }

    private void clearGraph() {
        graph.getModel().beginUpdate();
        try {
            graph.removeCells(graph.getChildCells(graph.getDefaultParent(), true, true));
        } finally {
            graph.getModel().endUpdate();
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        SwingUtilities.invokeLater(this::applyTheme);
    }

    private void applyTheme() {
        setBackground(JStudioTheme.getBgTertiary());
        toolbar.setBackground(JStudioTheme.getBgSecondary());
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));
        graphComponent.getViewport().setBackground(JStudioTheme.getBgTertiary());
        graphComponent.setBackground(JStudioTheme.getBgTertiary());
        setupGraphStyles();
        if (loaded) {
            rebuildGraph();
        }
    }

    public void refresh() {
        if (!loaded && methodSelector.getItemCount() > 0) {
            methodSelector.setSelectedIndex(0);
        }
    }

    public String getText() {
        return "";
    }

    public void copySelection() {
        // Not applicable for graph view
    }

    public String getSelectedText() {
        return null;
    }

    public void goToLine(int line) {
        // Not applicable for graph view
    }

    public void showFindDialog() {
        // Not implemented for graph view
    }

    public void scrollToText(String text) {
        // Not applicable for graph view
    }

    public void setFontSize(int size) {
        // Could update vertex font size
    }

    public void setWordWrap(boolean enabled) {
        // Not applicable for graph view
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static class MethodListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof MethodEntryModel) {
                MethodEntryModel method = (MethodEntryModel) value;
                setText(method.getName() + method.getMethodEntry().getDesc());
                setFont(JStudioTheme.getCodeFont(11));
            }
            return this;
        }
    }

    @Getter
    public static class NavigateToBytecodeRequest extends Event {
        private final ClassEntryModel classEntry;
        private final MethodEntryModel method;
        private final int offset;

        public NavigateToBytecodeRequest(ClassEntryModel classEntry, MethodEntryModel method, int offset) {
            super(classEntry);
            this.classEntry = classEntry;
            this.method = method;
            this.offset = offset;
        }
    }
}
