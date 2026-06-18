package com.tonic.graph.dot;

import lombok.Getter;

import java.util.List;

/**
 * A parsed subset of a Graphviz graph: an ordered set of nodes and directed/undirected edges plus a layout
 * direction. Produced by {@link DotParser}; consumed by the UI graph builder. Intentionally small — only the
 * attributes the renderer honors (label, shape, colors, dashed/rounded, rankdir) are modeled.
 */
@Getter
public final class DotGraph {

    /** Layout direction, mapped from the DOT {@code rankdir} attribute. */
    public enum Rankdir { TB, LR, BT, RL }

    @Getter
    public static final class Node {
        private final String id;
        private String label;
        private String shape;
        private String fillColor;
        private String strokeColor;
        private boolean dashed;
        private boolean rounded;

        Node(String id) {
            this.id = id;
            this.label = id;
        }

        void setLabel(String label) { this.label = label; }
        void setShape(String shape) { this.shape = shape; }
        void setFillColor(String fillColor) { this.fillColor = fillColor; }
        void setStrokeColor(String strokeColor) { this.strokeColor = strokeColor; }
        void setDashed(boolean dashed) { this.dashed = dashed; }
        void setRounded(boolean rounded) { this.rounded = rounded; }
    }

    @Getter
    public static final class Edge {
        private final String from;
        private final String to;
        private final boolean directed;
        private String label;
        private boolean dashed;

        Edge(String from, String to, boolean directed) {
            this.from = from;
            this.to = to;
            this.directed = directed;
        }

        void setLabel(String label) { this.label = label; }
        void setDashed(boolean dashed) { this.dashed = dashed; }
    }

    private final boolean directed;
    private final Rankdir rankdir;
    private final List<Node> nodes;
    private final List<Edge> edges;

    DotGraph(boolean directed, Rankdir rankdir, List<Node> nodes, List<Edge> edges) {
        this.directed = directed;
        this.rankdir = rankdir;
        this.nodes = nodes;
        this.edges = edges;
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }
}
