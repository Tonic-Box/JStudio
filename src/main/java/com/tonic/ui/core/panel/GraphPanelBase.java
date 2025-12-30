package com.tonic.ui.core.panel;

import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxStylesheet;
import com.tonic.ui.core.util.ErrorHandler;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.JStudioTheme;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;

public abstract class GraphPanelBase extends AnalysisPanelBase {

    protected mxGraph graph;
    protected mxGraphComponent graphComponent;

    protected GraphPanelBase(ProjectModel project) {
        super(project);
    }

    @Override
    protected JPanel createContentPanel() {
        graph = new mxGraph();
        graph.setAllowDanglingEdges(false);
        graph.setEdgeLabelsMovable(false);
        graph.setVertexLabelsMovable(false);
        graph.setCellsEditable(false);
        graph.setCellsResizable(false);
        graph.setConnectableEdges(false);
        graph.setDropEnabled(false);

        setupGraphStyles(graph.getStylesheet());

        graphComponent = new mxGraphComponent(graph);
        graphComponent.setConnectable(false);
        graphComponent.setDragEnabled(false);
        graphComponent.getViewport().setBackground(JStudioTheme.getBgPrimary());
        graphComponent.setBackground(JStudioTheme.getBgPrimary());
        graphComponent.setBorder(null);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(JStudioTheme.getBgPrimary());
        panel.add(graphComponent, BorderLayout.CENTER);
        return panel;
    }

    protected abstract void setupGraphStyles(mxStylesheet stylesheet);

    protected void clearGraph() {
        graph.getModel().beginUpdate();
        try {
            graph.removeCells(graph.getChildVertices(graph.getDefaultParent()));
        } finally {
            graph.getModel().endUpdate();
        }
    }

    protected void exportAsPng() {
        exportAsPng(null);
    }

    protected void exportAsPng(String suggestedFilename) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Graph as PNG");
        chooser.setFileFilter(new FileNameExtensionFilter("PNG Images", "png"));

        if (suggestedFilename != null) {
            chooser.setSelectedFile(new File(suggestedFilename + ".png"));
        }

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".png")) {
                file = new File(file.getAbsolutePath() + ".png");
            }

            try {
                BufferedImage image = mxCellRenderer.createBufferedImage(
                    graph, null, 1, Color.WHITE, true, null
                );
                if (image != null) {
                    ImageIO.write(image, "PNG", file);
                    updateStatus("Exported graph to: " + file.getAbsolutePath());
                } else {
                    updateStatus("Cannot export empty graph");
                }
            } catch (Exception e) {
                ErrorHandler.handle(e, "Graph export");
                updateStatus("Export failed: " + e.getMessage());
            }
        }
    }

    protected void zoomIn() {
        graphComponent.zoomIn();
    }

    protected void zoomOut() {
        graphComponent.zoomOut();
    }

    protected void zoomToFit() {
        graphComponent.zoomTo(1.0, true);
    }

    protected void centerGraph() {
        graphComponent.scrollToCenter(true);
    }
}
