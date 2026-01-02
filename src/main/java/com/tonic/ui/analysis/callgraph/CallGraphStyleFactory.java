package com.tonic.ui.analysis.callgraph;

import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxStylesheet;
import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.callgraph.CallGraphNode;
import com.tonic.analysis.callgraph.CallSite;
import com.tonic.analysis.common.MethodReference;
import com.tonic.ui.theme.JStudioTheme;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class CallGraphStyleFactory {

    private static final int FONT_SIZE_LABEL = 11;
    private static final int FONT_SIZE_EDGE = 9;
    private static final int ARC_SIZE = 12;
    private static final int SPACING = 6;
    private static final double STROKE_WIDTH_NORMAL = 1.5;
    private static final double STROKE_WIDTH_FOCUS = 3;

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private String getMethodFill() {
        return toHex(JStudioTheme.getGraphNodeFill());
    }

    private String getMethodStroke() {
        return toHex(JStudioTheme.getGraphNodeStroke());
    }

    private String getFocusFill() {
        return toHex(JStudioTheme.getGraphFocusFill());
    }

    private String getFocusStroke() {
        return toHex(JStudioTheme.getGraphFocusStroke());
    }

    private String getConstructorFill() {
        return toHex(JStudioTheme.getGraphConstructorFill());
    }

    private String getConstructorStroke() {
        return toHex(JStudioTheme.getGraphConstructorStroke());
    }

    private String getStaticFill() {
        return toHex(JStudioTheme.getGraphStaticFill());
    }

    private String getStaticStroke() {
        return toHex(JStudioTheme.getGraphStaticStroke());
    }

    private String getExternalFill() {
        return toHex(JStudioTheme.getGraphExternalFill());
    }

    private String getExternalStroke() {
        return toHex(JStudioTheme.getGraphExternalStroke());
    }

    private String getTextPrimary() {
        return toHex(JStudioTheme.getTextPrimary());
    }

    private String getTextSecondary() {
        return toHex(JStudioTheme.getTextSecondary());
    }

    private String getErrorStroke() {
        return toHex(JStudioTheme.getError());
    }

    public void setupStyles(mxGraph graph) {
        mxStylesheet stylesheet = new mxStylesheet();
        graph.setStylesheet(stylesheet);

        Map<String, Object> baseStyle = createBaseNodeStyle();

        stylesheet.putCellStyle("METHOD", createMethodStyle(baseStyle));
        stylesheet.putCellStyle("FOCUS", createFocusStyle(baseStyle));
        stylesheet.putCellStyle("CONSTRUCTOR", createConstructorStyle(baseStyle));
        stylesheet.putCellStyle("STATIC_INIT", createStaticInitStyle(baseStyle));
        stylesheet.putCellStyle("EXTERNAL", createExternalStyle(baseStyle));
        stylesheet.putCellStyle("FOCUS_CONSTRUCTOR", createFocusConstructorStyle(baseStyle));
        stylesheet.putCellStyle("FOCUS_STATIC_INIT", createFocusStaticInitStyle(baseStyle));

        Map<String, Object> baseEdgeStyle = createBaseEdgeStyle();

        stylesheet.putCellStyle("EDGE_VIRTUAL", createVirtualEdgeStyle(baseEdgeStyle));
        stylesheet.putCellStyle("EDGE_STATIC", createStaticEdgeStyle(baseEdgeStyle));
        stylesheet.putCellStyle("EDGE_SPECIAL", createSpecialEdgeStyle(baseEdgeStyle));
        stylesheet.putCellStyle("EDGE_INTERFACE", createInterfaceEdgeStyle(baseEdgeStyle));
        stylesheet.putCellStyle("EDGE_DYNAMIC", createDynamicEdgeStyle(baseEdgeStyle));
        stylesheet.putCellStyle("EDGE", createDefaultEdgeStyle(baseEdgeStyle));

        graph.getStylesheet().setDefaultEdgeStyle(createDefaultEdgeStyle(baseEdgeStyle));
    }

    private Map<String, Object> createBaseNodeStyle() {
        Map<String, Object> style = new HashMap<>();
        style.put(mxConstants.STYLE_SHAPE, mxConstants.SHAPE_RECTANGLE);
        style.put(mxConstants.STYLE_ROUNDED, true);
        style.put(mxConstants.STYLE_ARCSIZE, ARC_SIZE);
        style.put(mxConstants.STYLE_VERTICAL_ALIGN, mxConstants.ALIGN_MIDDLE);
        style.put(mxConstants.STYLE_ALIGN, mxConstants.ALIGN_CENTER);
        style.put(mxConstants.STYLE_SPACING, SPACING);
        style.put(mxConstants.STYLE_FONTSIZE, FONT_SIZE_LABEL);
        return style;
    }

    private Map<String, Object> createMethodStyle(Map<String, Object> baseStyle) {
        Map<String, Object> style = new HashMap<>(baseStyle);
        style.put(mxConstants.STYLE_FILLCOLOR, getMethodFill());
        style.put(mxConstants.STYLE_STROKECOLOR, getMethodStroke());
        style.put(mxConstants.STYLE_FONTCOLOR, getTextPrimary());
        style.put(mxConstants.STYLE_STROKEWIDTH, STROKE_WIDTH_NORMAL);
        return style;
    }

    private Map<String, Object> createFocusStyle(Map<String, Object> baseStyle) {
        Map<String, Object> style = new HashMap<>(baseStyle);
        style.put(mxConstants.STYLE_FILLCOLOR, getFocusFill());
        style.put(mxConstants.STYLE_STROKECOLOR, getFocusStroke());
        style.put(mxConstants.STYLE_FONTCOLOR, getTextPrimary());
        style.put(mxConstants.STYLE_STROKEWIDTH, STROKE_WIDTH_FOCUS);
        return style;
    }

    private Map<String, Object> createConstructorStyle(Map<String, Object> baseStyle) {
        Map<String, Object> style = new HashMap<>(baseStyle);
        style.put(mxConstants.STYLE_FILLCOLOR, getConstructorFill());
        style.put(mxConstants.STYLE_STROKECOLOR, getConstructorStroke());
        style.put(mxConstants.STYLE_FONTCOLOR, getTextPrimary());
        style.put(mxConstants.STYLE_STROKEWIDTH, STROKE_WIDTH_NORMAL);
        return style;
    }

    private Map<String, Object> createStaticInitStyle(Map<String, Object> baseStyle) {
        Map<String, Object> style = new HashMap<>(baseStyle);
        style.put(mxConstants.STYLE_FILLCOLOR, getStaticFill());
        style.put(mxConstants.STYLE_STROKECOLOR, getStaticStroke());
        style.put(mxConstants.STYLE_FONTCOLOR, getTextPrimary());
        style.put(mxConstants.STYLE_STROKEWIDTH, STROKE_WIDTH_NORMAL);
        return style;
    }

    private Map<String, Object> createExternalStyle(Map<String, Object> baseStyle) {
        Map<String, Object> style = new HashMap<>(baseStyle);
        style.put(mxConstants.STYLE_FILLCOLOR, getExternalFill());
        style.put(mxConstants.STYLE_STROKECOLOR, getExternalStroke());
        style.put(mxConstants.STYLE_FONTCOLOR, getTextSecondary());
        style.put(mxConstants.STYLE_STROKEWIDTH, 1);
        return style;
    }

    private Map<String, Object> createFocusConstructorStyle(Map<String, Object> baseStyle) {
        Map<String, Object> style = createConstructorStyle(baseStyle);
        style.put(mxConstants.STYLE_STROKEWIDTH, STROKE_WIDTH_FOCUS);
        style.put(mxConstants.STYLE_STROKECOLOR, getFocusStroke());
        return style;
    }

    private Map<String, Object> createFocusStaticInitStyle(Map<String, Object> baseStyle) {
        Map<String, Object> style = createStaticInitStyle(baseStyle);
        style.put(mxConstants.STYLE_STROKEWIDTH, STROKE_WIDTH_FOCUS);
        style.put(mxConstants.STYLE_STROKECOLOR, getFocusStroke());
        return style;
    }

    private Map<String, Object> createBaseEdgeStyle() {
        Map<String, Object> style = new HashMap<>();
        style.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_CLASSIC);
        style.put(mxConstants.STYLE_STROKEWIDTH, STROKE_WIDTH_NORMAL);
        style.put(mxConstants.STYLE_FONTSIZE, FONT_SIZE_EDGE);
        style.put(mxConstants.STYLE_ROUNDED, true);
        style.put(mxConstants.STYLE_EDGE, mxConstants.EDGESTYLE_ORTHOGONAL);
        style.put(mxConstants.STYLE_STROKECOLOR, getTextSecondary());
        return style;
    }

    private Map<String, Object> createVirtualEdgeStyle(Map<String, Object> baseStyle) {
        Map<String, Object> style = new HashMap<>(baseStyle);
        style.put(mxConstants.STYLE_STROKECOLOR, getMethodStroke());
        return style;
    }

    private Map<String, Object> createStaticEdgeStyle(Map<String, Object> baseStyle) {
        Map<String, Object> style = new HashMap<>(baseStyle);
        style.put(mxConstants.STYLE_STROKECOLOR, getStaticStroke());
        return style;
    }

    private Map<String, Object> createSpecialEdgeStyle(Map<String, Object> baseStyle) {
        Map<String, Object> style = new HashMap<>(baseStyle);
        style.put(mxConstants.STYLE_STROKECOLOR, getConstructorStroke());
        return style;
    }

    private Map<String, Object> createInterfaceEdgeStyle(Map<String, Object> baseStyle) {
        Map<String, Object> style = new HashMap<>(baseStyle);
        style.put(mxConstants.STYLE_STROKECOLOR, getFocusStroke());
        style.put(mxConstants.STYLE_DASHED, true);
        return style;
    }

    private Map<String, Object> createDynamicEdgeStyle(Map<String, Object> baseStyle) {
        Map<String, Object> style = new HashMap<>(baseStyle);
        style.put(mxConstants.STYLE_STROKECOLOR, getErrorStroke());
        style.put(mxConstants.STYLE_DASHED, true);
        style.put(mxConstants.STYLE_DASH_PATTERN, "3 3");
        return style;
    }

    private Map<String, Object> createDefaultEdgeStyle(Map<String, Object> baseStyle) {
        Map<String, Object> style = new HashMap<>(baseStyle);
        style.put(mxConstants.STYLE_STROKECOLOR, getExternalStroke());
        return style;
    }

    public String getNodeStyle(CallGraph callGraph, MethodReference ref, boolean isFocus) {
        String methodName = ref.getName();
        CallGraphNode node = callGraph.getNode(ref);
        boolean inPool = node != null && node.isInPool();

        if (!inPool) {
            return "EXTERNAL";
        }

        if ("<init>".equals(methodName)) {
            return isFocus ? "FOCUS_CONSTRUCTOR" : "CONSTRUCTOR";
        } else if ("<clinit>".equals(methodName)) {
            return isFocus ? "FOCUS_STATIC_INIT" : "STATIC_INIT";
        } else {
            return isFocus ? "FOCUS" : "METHOD";
        }
    }

    public String getEdgeStyle(CallGraph callGraph, MethodReference caller, MethodReference callee) {
        CallGraphNode callerNode = callGraph.getNode(caller);
        if (callerNode == null) return "EDGE";

        for (CallSite site : callerNode.getOutgoingCalls()) {
            if (site.getTarget().equals(callee)) {
                switch (site.getInvokeType()) {
                    case VIRTUAL:
                        return "EDGE_VIRTUAL";
                    case STATIC:
                        return "EDGE_STATIC";
                    case SPECIAL:
                        return "EDGE_SPECIAL";
                    case INTERFACE:
                        return "EDGE_INTERFACE";
                    case DYNAMIC:
                        return "EDGE_DYNAMIC";
                    default:
                        return "EDGE";
                }
            }
        }
        return "EDGE";
    }
}
