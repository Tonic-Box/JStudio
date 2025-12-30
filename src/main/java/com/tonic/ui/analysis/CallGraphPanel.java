package com.tonic.ui.analysis;

import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.view.mxGraph;
import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.analysis.callgraph.CallGraphController;
import com.tonic.ui.analysis.callgraph.CallGraphExporter;
import com.tonic.ui.analysis.callgraph.CallGraphModel;
import com.tonic.ui.analysis.callgraph.CallGraphRenderer;
import com.tonic.ui.analysis.callgraph.CallGraphStyleFactory;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.MethodSelectedEvent;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

public class CallGraphPanel extends ThemedJPanel {

    private final ProjectModel project;
    private final CallGraphModel model;
    private final CallGraphController controller;
    private final CallGraphRenderer renderer;
    private final CallGraphExporter exporter;
    private final mxGraph graph;
    private final mxGraphComponent graphComponent;
    private final JTextArea statusArea;
    private final JComboBox<String> focusCombo;

    public CallGraphPanel(ProjectModel project) {
        super(BackgroundStyle.SECONDARY, new BorderLayout());
        this.project = project;
        this.model = new CallGraphModel();
        this.exporter = new CallGraphExporter();

        focusCombo = new JComboBox<>();
        focusCombo.setBackground(JStudioTheme.getBgTertiary());
        focusCombo.setForeground(JStudioTheme.getTextPrimary());

        JPanel controlPanel = createControlPanel();
        add(controlPanel, BorderLayout.NORTH);

        graph = createGraph();
        graphComponent = createGraphComponent();
        add(graphComponent, BorderLayout.CENTER);

        statusArea = createStatusArea();
        add(createStatusPanel(), BorderLayout.SOUTH);

        CallGraphStyleFactory styleFactory = new CallGraphStyleFactory();
        styleFactory.setupStyles(graph);

        renderer = new CallGraphRenderer(graph, model, styleFactory);
        controller = new CallGraphController(
                project, model, renderer, graph, graphComponent,
                focusCombo, this::updateStatus
        );

        focusCombo.addActionListener(e -> controller.updateFocus());

        updateStatus("No call graph built. Click 'Build Graph' to analyze.");
        registerEventListeners();
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_SMALL));
        panel.setBackground(JStudioTheme.getBgSecondary());

        panel.add(createBuildButton());
        panel.add(new JLabel("Focus:"));
        panel.add(focusCombo);
        panel.add(new JLabel("Depth:"));
        panel.add(createDepthSpinner());
        panel.add(createRefreshButton());
        panel.add(createExportButton());

        return panel;
    }

    private JButton createBuildButton() {
        JButton button = createButton("Build Graph");
        button.addActionListener(e -> controller.buildCallGraph());
        return button;
    }

    private JSpinner createDepthSpinner() {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(3, 1, 10, 1));
        spinner.addChangeListener(e -> controller.onDepthChanged((Integer) spinner.getValue()));
        return spinner;
    }

    private JButton createRefreshButton() {
        JButton button = createButton("Refresh");
        button.addActionListener(e -> controller.visualizeAndUpdateStatus());
        return button;
    }

    private JButton createExportButton() {
        JButton button = createButton("Export PNG");
        button.addActionListener(e -> exporter.exportAsPng(
                this, graph, model.getFocusMethod(), this::updateStatus));
        return button;
    }

    private JButton createButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(JStudioTheme.getBgTertiary());
        button.setForeground(JStudioTheme.getTextPrimary());
        return button;
    }

    private mxGraph createGraph() {
        mxGraph g = new mxGraph();
        g.setHtmlLabels(true);
        g.setAutoSizeCells(true);
        g.setCellsEditable(false);
        g.setCellsMovable(true);
        g.setCellsResizable(false);
        return g;
    }

    private mxGraphComponent createGraphComponent() {
        mxGraphComponent component = new mxGraphComponent(graph);
        component.setBackground(JStudioTheme.getBgTertiary());
        component.getViewport().setBackground(JStudioTheme.getBgTertiary());
        component.setBorder(null);
        component.setToolTips(true);
        return component;
    }

    private JTextArea createStatusArea() {
        JTextArea area = new JTextArea(3, 40);
        area.setEditable(false);
        area.setBackground(JStudioTheme.getBgTertiary());
        area.setForeground(JStudioTheme.getTextSecondary());
        area.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE));
        area.setBorder(BorderFactory.createEmptyBorder(UIConstants.SPACING_SMALL, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_SMALL, UIConstants.SPACING_MEDIUM));
        return area;
    }

    private JScrollPane createStatusPanel() {
        JScrollPane scroll = new JScrollPane(statusArea);
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()));
        return scroll;
    }

    private void registerEventListeners() {
        EventBus.getInstance().register(MethodSelectedEvent.class, event -> {
            MethodEntryModel method = event.getMethodEntry();
            if (method != null && isShowing()) {
                controller.focusOnMethod(method.getMethodEntry());
            }
        });
    }

    private void updateStatus(String message) {
        statusArea.setText(message);
    }

    public void buildCallGraph() {
        controller.buildCallGraph();
    }

    public void refresh() {
        controller.refresh();
    }

    public void focusOnMethod(MethodEntry method) {
        controller.focusOnMethod(method);
    }

    public CallGraph getCallGraph() {
        return model.getCallGraph();
    }
}
