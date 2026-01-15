package com.tonic.ui.editor.graph.render;

import com.tonic.analysis.cpg.node.*;
import com.tonic.ui.theme.JStudioTheme;

import java.awt.*;

public class CPGVertexRenderer implements GraphVertexRenderer<CPGNode> {

    @Override
    public String renderHtml(CPGNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><pre style='margin:4px;font-family:monospace;font-size:10px'>");

        if (node instanceof MethodNode) {
            renderMethodNode(sb, (MethodNode) node);
        } else if (node instanceof BlockNode) {
            renderBlockNode(sb, (BlockNode) node);
        } else if (node instanceof InstructionNode) {
            renderInstructionNode(sb, (InstructionNode) node);
        } else if (node instanceof CallSiteNode) {
            renderCallSiteNode(sb, (CallSiteNode) node);
        } else {
            sb.append("<b style='color:").append(toHex(JStudioTheme.getTextPrimary())).append("'>")
              .append(escapeHtml(node.getNodeType().name()))
              .append("</b>\n");
            String label = node.getLabel();
            if (label != null && !label.isEmpty()) {
                sb.append("<span style='color:").append(toHex(JStudioTheme.getTextSecondary()))
                  .append("'>").append(escapeHtml(truncate(label, 40))).append("</span>\n");
            }
        }

        sb.append("</pre></html>");
        return sb.toString();
    }

    private void renderMethodNode(StringBuilder sb, MethodNode node) {
        sb.append("<b style='color:#27ae60'>METHOD</b>\n");
        String name = node.getName();
        if (name != null) {
            sb.append("<span style='color:").append(toHex(JStudioTheme.getTextSecondary()))
              .append("'>").append(escapeHtml(truncate(name, 40))).append("</span>\n");
        }
    }

    private void renderBlockNode(StringBuilder sb, BlockNode node) {
        String color;
        String extra = "";
        if (node.isEntryBlock()) {
            color = "#27ae60";
            extra = " (entry)";
        } else if (node.isExitBlock()) {
            color = "#e74c3c";
            extra = " (exit)";
        } else {
            color = toHex(JStudioTheme.getInfo());
        }

        sb.append("<b style='color:").append(color).append("'>B")
          .append(node.getBlockId()).append("</b>").append(extra).append("\n");
    }

    private void renderInstructionNode(StringBuilder sb, InstructionNode node) {
        sb.append("<b style='color:").append(toHex(JStudioTheme.getTextPrimary())).append("'>INSTR</b>\n");
        String label = node.getLabel();
        if (label != null) {
            sb.append("<span style='color:").append(toHex(JStudioTheme.getTextSecondary()))
              .append("'>").append(escapeHtml(truncate(label, 50))).append("</span>\n");
        }
    }

    private void renderCallSiteNode(StringBuilder sb, CallSiteNode node) {
        sb.append("<b style='color:#9b59b6'>CALL</b>\n");
        String target = node.getTargetName();
        if (target != null) {
            sb.append("<span style='color:").append(toHex(JStudioTheme.getTextSecondary()))
              .append("'>").append(escapeHtml(truncate(target, 40))).append("</span>\n");
        }
    }

    @Override
    public String getNodeStyle(CPGNode node) {
        switch (node.getNodeType()) {
            case METHOD:
                return "ENTRY";
            case BLOCK:
                BlockNode block = (BlockNode) node;
                if (block.isEntryBlock()) return "ENTRY";
                if (block.isExitBlock()) return "EXIT";
                return "BLOCK";
            case CALL_SITE:
                return "CALL";
            case INSTRUCTION:
            default:
                return "NODE";
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 3) + "...";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
