package com.tonic.ui.editor.callgraph;

import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.common.MethodReference;
import com.tonic.ui.editor.graph.render.GraphVertexRenderer;
import com.tonic.ui.theme.JStudioTheme;

public class CallGraphVertexRenderer implements GraphVertexRenderer<MethodReference> {

    private final CallGraph callGraph;
    private final MethodReference focusMethod;
    private final boolean isFocus;

    private static final int MAX_CLASS_LENGTH = 22;
    private static final int MAX_METHOD_LENGTH = 20;

    public CallGraphVertexRenderer(CallGraph callGraph, MethodReference focusMethod, boolean isFocus) {
        this.callGraph = callGraph;
        this.focusMethod = focusMethod;
        this.isFocus = isFocus;
    }

    @Override
    public String renderHtml(MethodReference ref) {
        String className = getSimpleClassName(ref.getOwner());
        String methodName = ref.getName();

        String topLine;
        String bottomLine;

        if ("<init>".equals(methodName)) {
            topLine = truncate(className, MAX_CLASS_LENGTH);
            bottomLine = "<init>";
        } else if ("<clinit>".equals(methodName)) {
            topLine = truncate(className, MAX_CLASS_LENGTH);
            bottomLine = "<clinit>";
        } else {
            topLine = truncate(className, MAX_CLASS_LENGTH);
            bottomLine = truncate(methodName, MAX_METHOD_LENGTH) + "()";
        }

        String topColor = toHex(JStudioTheme.getTextSecondary());
        String bottomColor = toHex(JStudioTheme.getTextPrimary());

        return "<html><center>" +
               "<span style=\"font-size:9px; color:" + topColor + ";\">" + escapeHtml(topLine) + "</span><br>" +
               "<span style=\"font-size:11px; color:" + bottomColor + ";\"><b>" + escapeHtml(bottomLine) + "</b></span>" +
               "</center></html>";
    }

    @Override
    public String getNodeStyle(MethodReference ref) {
        if (isFocus || ref.equals(focusMethod)) {
            return "FOCUS";
        }
        if (callGraph.getNode(ref) == null) {
            return "EXTERNAL";
        }
        return "NODE";
    }

    private String getSimpleClassName(String fullName) {
        if (fullName == null) return "";
        int lastSlash = fullName.lastIndexOf('/');
        return lastSlash >= 0 ? fullName.substring(lastSlash + 1) : fullName;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    private static String toHex(java.awt.Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
