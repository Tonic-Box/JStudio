package com.tonic.ui.editor.graph.render;

import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.sdg.node.*;
import com.tonic.ui.theme.JStudioTheme;

import java.awt.*;

public class SDGVertexRenderer implements GraphVertexRenderer<PDGNode> {

    @Override
    public String renderHtml(PDGNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><pre style='margin:4px;font-family:monospace;font-size:10px'>");

        if (node instanceof SDGEntryNode) {
            renderEntryNode(sb, (SDGEntryNode) node);
        } else if (node instanceof SDGCallNode) {
            renderCallNode(sb, (SDGCallNode) node);
        } else if (node instanceof SDGFormalInNode) {
            renderFormalIn(sb, (SDGFormalInNode) node);
        } else if (node instanceof SDGFormalOutNode) {
            renderFormalOut(sb);
        } else if (node instanceof SDGActualInNode) {
            renderActualIn(sb, (SDGActualInNode) node);
        } else if (node instanceof SDGActualOutNode) {
            renderActualOut(sb);
        } else {
            sb.append("<b style='color:").append(toHex(JStudioTheme.getTextPrimary())).append("'>")
              .append(escapeHtml(node.getType().name().toLowerCase())).append(":</b>\n");
        }

        sb.append("</pre></html>");
        return sb.toString();
    }

    private void renderEntryNode(StringBuilder sb, SDGEntryNode node) {
        sb.append("<b style='color:#27ae60'>entry:</b>\n");
        String name = node.getMethodName();
        if (name != null) {
            int lastDot = name.lastIndexOf('.');
            if (lastDot >= 0) {
                name = name.substring(lastDot + 1);
            }
            sb.append("<span style='color:#888'>method: </span>");
            sb.append("<span style='color:").append(toHex(JStudioTheme.getTextSecondary()))
              .append("'>").append(escapeHtml(truncate(name, 35))).append("</span>\n");
        }
    }

    private void renderCallNode(StringBuilder sb, SDGCallNode node) {
        sb.append("<b style='color:#9b59b6'>call:</b>\n");
        String target = node.getTargetName();
        if (target != null) {
            sb.append("<span style='color:#888'>target: </span>");
            sb.append("<span style='color:").append(toHex(JStudioTheme.getTextSecondary()))
              .append("'>").append(escapeHtml(truncate(target, 35))).append("</span>\n");
        }
    }

    private void renderFormalIn(StringBuilder sb, SDGFormalInNode node) {
        sb.append("<b style='color:").append(toHex(JStudioTheme.getInfo())).append("'>formal_in:</b>\n");
        sb.append("<span style='color:#888'>param[").append(node.getParameterIndex()).append("]</span>\n");
    }

    private void renderFormalOut(StringBuilder sb) {
        sb.append("<b style='color:#e74c3c'>formal_out:</b>\n");
        sb.append("<span style='color:#888'>return value</span>\n");
    }

    private void renderActualIn(StringBuilder sb, SDGActualInNode node) {
        sb.append("<b style='color:").append(toHex(JStudioTheme.getInfo())).append("'>actual_in:</b>\n");
        sb.append("<span style='color:#888'>arg[").append(node.getParameterIndex()).append("]</span>\n");
    }

    private void renderActualOut(StringBuilder sb) {
        sb.append("<b style='color:#e74c3c'>actual_out:</b>\n");
        sb.append("<span style='color:#888'>result</span>\n");
    }

    @Override
    public String getNodeStyle(PDGNode node) {
        if (node instanceof SDGEntryNode) {
            return "ENTRY";
        }
        if (node instanceof SDGCallNode) {
            return "CALL";
        }
        if (node instanceof SDGFormalInNode || node instanceof SDGActualInNode) {
            return "BLOCK";
        }
        if (node instanceof SDGFormalOutNode || node instanceof SDGActualOutNode) {
            return "EXIT";
        }
        return "NODE";
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
