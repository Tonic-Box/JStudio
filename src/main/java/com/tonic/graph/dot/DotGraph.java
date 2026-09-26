package com.tonic.graph.dot;

import lombok.Getter;

import java.util.List;

/** A parsed subset of a Graphviz graph: ordered nodes and edges plus a layout direction, modeling only the attributes the renderer honors. */
@Getter
public final class DotGraph
{

    /** Layout direction, from the DOT rankdir attribute. */
    public enum Rankdir
    {TB, LR, BT, RL}

    /** A graph node: its id and the label, shape, colors and style flags the renderer draws. */
    @Getter
    public static final class Node
    {
        private final String id;
        private String label;
        private String shape;
        private String fillColor;
        private String strokeColor;
        private boolean dashed;
        private boolean rounded;

        Node(String id)
        {
            this.id = id;
            this.label = id;
        }

        void setLabel(String label)
        {
            this.label = label;
        }

        void setShape(String shape)
        {
            this.shape = shape;
        }

        void setFillColor(String fillColor)
        {
            this.fillColor = fillColor;
        }

        void setStrokeColor(String strokeColor)
        {
            this.strokeColor = strokeColor;
        }

        void setDashed(boolean dashed)
        {
            this.dashed = dashed;
        }

        void setRounded(boolean rounded)
        {
            this.rounded = rounded;
        }
    }

    /** A graph edge between two node ids, directed or not, with an optional label and dashed style. */
    @Getter
    public static final class Edge
    {
        private final String from;
        private final String to;
        private final boolean directed;
        private String label;
        private boolean dashed;

        Edge(String from, String to, boolean directed)
        {
            this.from = from;
            this.to = to;
            this.directed = directed;
        }

        void setLabel(String label)
        {
            this.label = label;
        }

        void setDashed(boolean dashed)
        {
            this.dashed = dashed;
        }
    }

    private final boolean directed;
    private final Rankdir rankdir;
    private final List<Node> nodes;
    private final List<Edge> edges;

    DotGraph(boolean directed, Rankdir rankdir, List<Node> nodes, List<Edge> edges)
    {
        this.directed = directed;
        this.rankdir = rankdir;
        this.nodes = nodes;
        this.edges = edges;
    }

    /**
     * Tells whether the graph has no nodes.
     *
     * @return true when there are no nodes, even if edges exist
     */
    public boolean isEmpty()
    {
        return nodes.isEmpty();
    }
}
