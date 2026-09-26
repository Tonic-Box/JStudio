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

/** Builds a read-only, themed mxGraph with a hierarchical layout, for flow-style diagrams. */
public class FlowGraphBuilder
{

    private int orientation = SwingConstants.NORTH;
    private int interRankSpacing = 60;
    private int intraCellSpacing = 30;
    private boolean orthogonalEdges = true;

    /**
     * Starts a builder with a top-down layout, spacing 60 by 30 and orthogonal edges.
     *
     * @return the builder
     */
    public static FlowGraphBuilder create()
    {
        return new FlowGraphBuilder();
    }

    /**
     * Sets the layout direction.
     *
     * @param orientation a SwingConstants compass direction the ranks flow from
     * @return this builder
     */
    public FlowGraphBuilder withOrientation(int orientation)
    {
        this.orientation = orientation;
        return this;
    }

    /**
     * Sets the layout spacing.
     *
     * @param interRank the gap between ranks, in pixels
     * @param intraCell the gap between cells in one rank, in pixels
     * @return this builder
     */
    public FlowGraphBuilder withSpacing(int interRank, int intraCell)
    {
        this.interRankSpacing = interRank;
        this.intraCellSpacing = intraCell;
        return this;
    }

    /**
     * Sets whether edges are routed at right angles.
     *
     * @param orthogonal true for orthogonal routing
     * @return this builder
     */
    public FlowGraphBuilder withOrthogonalEdges(boolean orthogonal)
    {
        this.orthogonalEdges = orthogonal;
        return this;
    }

    /**
     * Creates the graph with theme-colored default styles and HTML labels.
     *
     * @return the graph with its layout settings
     */
    public FlowGraph build()
    {
        mxGraph graph = new mxGraph();

        mxStylesheet stylesheet = new mxStylesheet();

        Map<String, Object> edgeStyle = new HashMap<>();
        edgeStyle.put(mxConstants.STYLE_STROKECOLOR, toHex(JStudioTheme.getTextSecondary()));
        edgeStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5);
        edgeStyle.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_CLASSIC);
        edgeStyle.put(mxConstants.STYLE_ROUNDED, true);
        if (orthogonalEdges)
        {
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

    private static String toHex(Color c)
    {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    /** A built graph with the layout settings it was built with. */
    @Getter
    public static class FlowGraph
    {
        private final mxGraph graph;
        private final int orientation;
        private final int interRankSpacing;
        private final int intraCellSpacing;

        FlowGraph(mxGraph graph, int orientation, int interRank, int intraCell)
        {
            this.graph = graph;
            this.orientation = orientation;
            this.interRankSpacing = interRank;
            this.intraCellSpacing = intraCell;
        }

        /**
         * Creates a borderless, theme-colored component showing the graph, with tooltips on.
         *
         * @return the component
         */
        public mxGraphComponent createComponent()
        {
            mxGraphComponent component = new mxGraphComponent(graph);
            component.setBackground(JStudioTheme.getBgTertiary());
            component.getViewport().setBackground(JStudioTheme.getBgTertiary());
            component.setBorder(null);
            component.setToolTips(true);
            return component;
        }

        /** Lays out the whole graph. */
        public void applyLayout()
        {
            applyLayout(graph.getDefaultParent());
        }

        /**
         * Lays out the children of one cell hierarchically.
         *
         * @param parent the cell whose children are laid out
         */
        public void applyLayout(Object parent)
        {
            mxHierarchicalLayout layout = new mxHierarchicalLayout(graph, orientation);
            layout.setInterRankCellSpacing(interRankSpacing);
            layout.setIntraCellSpacing(intraCellSpacing);
            layout.setDisableEdgeStyle(false);
            layout.execute(parent);
        }

        /**
         * Registers a named vertex style.
         *
         * @param name the style name cells refer to
         * @param style the style properties
         */
        public void addVertexStyle(String name, Map<String, Object> style)
        {
            graph.getStylesheet().putCellStyle(name, style);
        }

        /**
         * Registers a named edge style, adding the theme's edge color to the given map if it has no stroke color.
         *
         * @param name the style name cells refer to
         * @param style the style properties
         */
        public void addEdgeStyle(String name, Map<String, Object> style)
        {
            if (!style.containsKey(mxConstants.STYLE_STROKECOLOR))
            {
                style.put(mxConstants.STYLE_STROKECOLOR, toHex(JStudioTheme.getTextSecondary()));
            }
            graph.getStylesheet().putCellStyle(name, style);
        }
    }
}
