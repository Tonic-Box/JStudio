package com.tonic.ui.graph.dot;

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxGraph;
import com.tonic.graph.dot.DotGraph;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.SwingConstants;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds a laid-out, themed {@link mxGraph} from a parsed {@link DotGraph}, mirroring the call-graph renderer
 * (insert vertices/edges inside a model update, then {@link mxHierarchicalLayout}). Node/edge styles are inline so
 * arbitrary DOT colors are honored; missing colors fall back to the current {@link JStudioTheme}.
 */
public final class DotGraphBuilder {

    private static final int NODE_HEIGHT = 36;
    private static final int LINE_HEIGHT = 16;
    private static final int CHAR_WIDTH = 8;
    private static final int NODE_PADDING = 28;
    private static final int MIN_NODE_WIDTH = 90;
    private static final int MAX_NODE_WIDTH = 260;

    private DotGraphBuilder() {
    }

    public static mxGraph build(DotGraph dot) {
        mxGraph graph = new mxGraph();
        graph.setHtmlLabels(false);
        graph.setCellsEditable(false);
        graph.setCellsResizable(false);
        graph.setCellsMovable(false);
        graph.setAllowDanglingEdges(false);

        Object parent = graph.getDefaultParent();
        graph.getModel().beginUpdate();
        try {
            Map<String, Object> cells = new HashMap<>();
            for (DotGraph.Node node : dot.getNodes()) {
                String label = node.getLabel() == null ? node.getId() : node.getLabel();
                Object cell = graph.insertVertex(parent, node.getId(), label,
                        0, 0, nodeWidth(label), nodeHeight(label), nodeStyle(node));
                cells.put(node.getId(), cell);
            }
            for (DotGraph.Edge edge : dot.getEdges()) {
                Object src = cells.get(edge.getFrom());
                Object tgt = cells.get(edge.getTo());
                if (src == null || tgt == null) {
                    continue;
                }
                graph.insertEdge(parent, null, edge.getLabel() == null ? "" : edge.getLabel(),
                        src, tgt, edgeStyle(edge));
            }
            applyLayout(graph, parent, dot.getRankdir());
        } finally {
            graph.getModel().endUpdate();
        }
        return graph;
    }

    private static void applyLayout(mxGraph graph, Object parent, DotGraph.Rankdir rankdir) {
        mxHierarchicalLayout layout = new mxHierarchicalLayout(graph, orientation(rankdir));
        layout.setInterRankCellSpacing(55);
        layout.setIntraCellSpacing(30);
        layout.setParallelEdgeSpacing(10);
        layout.setFineTuning(true);
        layout.execute(parent);
    }

    private static int orientation(DotGraph.Rankdir rankdir) {
        switch (rankdir) {
            case LR: return SwingConstants.WEST;
            case RL: return SwingConstants.EAST;
            case BT: return SwingConstants.SOUTH;
            case TB:
            default: return SwingConstants.NORTH;
        }
    }

    private static String nodeStyle(DotGraph.Node node) {
        StringBuilder sb = new StringBuilder();
        sb.append(mxConstants.STYLE_SHAPE).append('=').append(shape(node.getShape())).append(';');
        sb.append(mxConstants.STYLE_FILLCOLOR).append('=')
                .append(color(node.getFillColor(), JStudioTheme.getGraphNodeFill())).append(';');
        sb.append(mxConstants.STYLE_STROKECOLOR).append('=')
                .append(color(node.getStrokeColor(), JStudioTheme.getGraphNodeStroke())).append(';');
        sb.append(mxConstants.STYLE_FONTCOLOR).append('=').append(toHex(JStudioTheme.getTextPrimary())).append(';');
        sb.append(mxConstants.STYLE_FONTSIZE).append("=11;");
        sb.append(mxConstants.STYLE_VERTICAL_ALIGN).append('=').append(mxConstants.ALIGN_MIDDLE).append(';');
        sb.append(mxConstants.STYLE_ALIGN).append('=').append(mxConstants.ALIGN_CENTER).append(';');
        sb.append(mxConstants.STYLE_WHITE_SPACE).append("=wrap;");
        if (node.isRounded() || isBox(node.getShape())) {
            sb.append(mxConstants.STYLE_ROUNDED).append("=1;").append(mxConstants.STYLE_ARCSIZE).append("=10;");
        }
        if (node.isDashed()) {
            sb.append(mxConstants.STYLE_DASHED).append("=1;");
        }
        return sb.toString();
    }

    private static String edgeStyle(DotGraph.Edge edge) {
        StringBuilder sb = new StringBuilder();
        sb.append(mxConstants.STYLE_STROKECOLOR).append('=').append(toHex(JStudioTheme.getTextSecondary())).append(';');
        sb.append(mxConstants.STYLE_FONTCOLOR).append('=').append(toHex(JStudioTheme.getTextSecondary())).append(';');
        sb.append(mxConstants.STYLE_FONTSIZE).append("=9;");
        sb.append(mxConstants.STYLE_ROUNDED).append("=1;");
        sb.append(mxConstants.STYLE_EDGE).append('=').append(mxConstants.EDGESTYLE_ORTHOGONAL).append(';');
        sb.append(mxConstants.STYLE_ENDARROW).append('=')
                .append(edge.isDirected() ? mxConstants.ARROW_CLASSIC : mxConstants.NONE).append(';');
        if (edge.isDashed()) {
            sb.append(mxConstants.STYLE_DASHED).append("=1;");
        }
        return sb.toString();
    }

    private static String shape(String dotShape) {
        if (dotShape == null) {
            return mxConstants.SHAPE_RECTANGLE;
        }
        switch (dotShape) {
            case "ellipse":
            case "oval":
            case "circle":
            case "doublecircle":
                return mxConstants.SHAPE_ELLIPSE;
            case "diamond":
            case "rhombus":
            case "mdiamond":
                return mxConstants.SHAPE_RHOMBUS;
            default:
                return mxConstants.SHAPE_RECTANGLE;
        }
    }

    private static boolean isBox(String dotShape) {
        return dotShape == null || dotShape.equals("box") || dotShape.equals("rect")
                || dotShape.equals("rectangle") || dotShape.equals("square");
    }

    private static int nodeWidth(String label) {
        int longest = 0;
        for (String line : label.split("\n", -1)) {
            longest = Math.max(longest, line.length());
        }
        return Math.max(MIN_NODE_WIDTH, Math.min(MAX_NODE_WIDTH, longest * CHAR_WIDTH + NODE_PADDING));
    }

    private static int nodeHeight(String label) {
        int lines = label.split("\n", -1).length;
        return NODE_HEIGHT + (lines - 1) * LINE_HEIGHT;
    }

    /** Resolves a DOT color (hex or a common name) to a hex string, or the theme fallback. */
    private static String color(String dotColor, Color fallback) {
        if (dotColor == null || dotColor.trim().isEmpty()) {
            return toHex(fallback);
        }
        String c = dotColor.trim();
        if (c.startsWith("#")) {
            return c;
        }
        String named = NAMED_COLORS.get(c.toLowerCase());
        return named != null ? named : toHex(fallback);
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static final Map<String, String> NAMED_COLORS = new HashMap<>();
    static {
        NAMED_COLORS.put("black", "#000000");
        NAMED_COLORS.put("white", "#FFFFFF");
        NAMED_COLORS.put("red", "#E74C3C");
        NAMED_COLORS.put("green", "#2ECC71");
        NAMED_COLORS.put("blue", "#3498DB");
        NAMED_COLORS.put("yellow", "#F1C40F");
        NAMED_COLORS.put("orange", "#E67E22");
        NAMED_COLORS.put("purple", "#9B59B6");
        NAMED_COLORS.put("cyan", "#1ABC9C");
        NAMED_COLORS.put("magenta", "#E91E63");
        NAMED_COLORS.put("pink", "#FF80AB");
        NAMED_COLORS.put("gray", "#95A5A6");
        NAMED_COLORS.put("grey", "#95A5A6");
        NAMED_COLORS.put("lightgray", "#BDC3C7");
        NAMED_COLORS.put("lightgrey", "#BDC3C7");
        NAMED_COLORS.put("darkgray", "#5D6D7E");
        NAMED_COLORS.put("darkgrey", "#5D6D7E");
        NAMED_COLORS.put("lightblue", "#5DADE2");
        NAMED_COLORS.put("lightgreen", "#58D68D");
        NAMED_COLORS.put("gold", "#F1C40F");
    }
}
