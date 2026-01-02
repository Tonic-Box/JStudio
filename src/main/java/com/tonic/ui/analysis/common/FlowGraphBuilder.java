package com.tonic.ui.analysis.common;

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxStylesheet;
import com.tonic.ui.theme.JStudioTheme;
import lombok.Getter;

import javax.swing.SwingConstants;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class FlowGraphBuilder {

    private int orientation = SwingConstants.NORTH;
    private int interRankSpacing = 60;
    private int intraCellSpacing = 30;
    private boolean orthogonalEdges = true;

    public static FlowGraphBuilder create() {
        return new FlowGraphBuilder();
    }

    public FlowGraphBuilder withOrientation(int orientation) {
        this.orientation = orientation;
        return this;
    }

    public FlowGraphBuilder withSpacing(int interRank, int intraCell) {
        this.interRankSpacing = interRank;
        this.intraCellSpacing = intraCell;
        return this;
    }

    public FlowGraphBuilder withOrthogonalEdges(boolean orthogonal) {
        this.orthogonalEdges = orthogonal;
        return this;
    }

    public FlowGraph build() {
        mxGraph graph = new mxGraph();

        mxStylesheet stylesheet = new mxStylesheet();

        Map<String, Object> edgeStyle = new HashMap<>();
        edgeStyle.put(mxConstants.STYLE_STROKECOLOR, toHex(JStudioTheme.getTextSecondary()));
        edgeStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
        edgeStyle.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_CLASSIC);
        edgeStyle.put(mxConstants.STYLE_ROUNDED, true);
        if (orthogonalEdges) {
            edgeStyle.put(mxConstants.STYLE_EDGE, mxConstants.EDGESTYLE_ORTHOGONAL);
        }
        stylesheet.setDefaultEdgeStyle(edgeStyle);

        Map<String, Object> vertexStyle = new HashMap<>();
        vertexStyle.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RECTANGLE);
        vertexStyle.put(mxConstants.STYLE_ROUNDED, true);
        vertexStyle.put(mxConstants.STYLE_ARCSIZE, 8);
        vertexStyle.put(mxConstants.STYLE_FILLCOLOR, toHex(JStudioTheme.getGraphNodeFill()));
        vertexStyle.put(mxConstants.STYLE_STROKECOLOR, toHex(JStudioTheme.getGraphNodeStroke()));
        vertexStyle.put(mxConstants.STYLE_FONTCOLOR, toHex(JStudioTheme.getTextPrimary()));
        vertexStyle.put(mxConstants.STYLE_AUTOSIZE, 1);
        stylesheet.setDefaultVertexStyle(vertexStyle);

        graph.setStylesheet(stylesheet);

        graph.setHtmlLabels(true);
        graph.setAutoSizeCells(true);
        graph.setCellsEditable(false);
        graph.setCellsMovable(true);
        graph.setCellsResizable(false);
        graph.setAllowDanglingEdges(false);

        return new FlowGraph(graph, orientation, interRankSpacing, intraCellSpacing);
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    @Getter
    public static class FlowGraph {
        private final mxGraph graph;
        private final int orientation;
        private final int interRankSpacing;
        private final int intraCellSpacing;

        FlowGraph(mxGraph graph, int orientation, int interRank, int intraCell) {
            this.graph = graph;
            this.orientation = orientation;
            this.interRankSpacing = interRank;
            this.intraCellSpacing = intraCell;
        }

        public mxGraphComponent createComponent() {
            mxGraphComponent component = new mxGraphComponent(graph);
            component.setBackground(JStudioTheme.getBgTertiary());
            component.getViewport().setBackground(JStudioTheme.getBgTertiary());
            component.setBorder(null);
            component.setToolTips(true);
            return component;
        }

        public void applyLayout() {
            applyLayout(graph.getDefaultParent());
        }

        public void applyLayout(Object parent) {
            mxHierarchicalLayout layout = new mxHierarchicalLayout(graph, orientation);
            layout.setInterRankCellSpacing(interRankSpacing);
            layout.setIntraCellSpacing(intraCellSpacing);
            layout.setDisableEdgeStyle(false);
            layout.execute(parent);
        }

        public void addVertexStyle(String name, Map<String, Object> style) {
            graph.getStylesheet().putCellStyle(name, style);
        }

        public void addEdgeStyle(String name, Map<String, Object> style) {
            if (!style.containsKey(mxConstants.STYLE_STROKECOLOR)) {
                style.put(mxConstants.STYLE_STROKECOLOR, toHex(JStudioTheme.getTextSecondary()));
            }
            graph.getStylesheet().putCellStyle(name, style);
        }
    }
}
