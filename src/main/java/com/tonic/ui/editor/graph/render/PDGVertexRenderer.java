package com.tonic.ui.editor.graph.render;

import com.tonic.analysis.pdg.node.PDGInstructionNode;
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.node.PDGNodeType;
import com.tonic.analysis.pdg.node.PDGRegionNode;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.ir.PhiInstruction;
import com.tonic.ui.theme.JStudioTheme;

import java.awt.*;

public class PDGVertexRenderer implements GraphVertexRenderer<PDGNode> {

    @Override
    public String renderHtml(PDGNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><pre style='margin:4px;font-family:monospace;font-size:10px'>");

        if (node instanceof PDGRegionNode) {
            renderRegionNode(sb, (PDGRegionNode) node);
        } else if (node instanceof PDGInstructionNode) {
            renderInstructionNode(sb, (PDGInstructionNode) node);
        } else {
            renderGenericNode(sb, node);
        }

        sb.append("</pre></html>");
        return sb.toString();
    }

    private void renderRegionNode(StringBuilder sb, PDGRegionNode region) {
        String color;
        String typeName;

        if (region.isEntry()) {
            color = "#27ae60";
            typeName = "entry:";
        } else if (region.isExit()) {
            color = "#e74c3c";
            typeName = "exit:";
        } else {
            color = toHex(JStudioTheme.getInfo());
            typeName = "region:";
        }

        sb.append("<b style='color:").append(color).append("'>").append(typeName).append("</b>\n");

        String label = region.getLabel();
        if (label != null && !label.isEmpty() && !label.equals(typeName)) {
            sb.append("<span style='color:#888'>").append(escapeHtml(truncate(label, 45))).append("</span>\n");
        }
    }

    private void renderInstructionNode(StringBuilder sb, PDGInstructionNode instrNode) {
        IRInstruction instr = instrNode.getInstruction();

        String color = toHex(JStudioTheme.getTextPrimary());
        String typeName = "instr";

        if (instr instanceof InvokeInstruction) {
            color = "#9b59b6";
            typeName = "call";
        } else if (instr instanceof PhiInstruction) {
            color = "#e67e22";
            typeName = "phi";
        }

        sb.append("<b style='color:").append(color).append("'>").append(typeName).append(":</b>\n");

        if (instr != null) {
            String repr = instr.toString();
            String[] lines = repr.split("\n");
            int maxLines = 10;
            int count = 0;

            for (String line : lines) {
                if (count >= maxLines) {
                    sb.append("<span style='color:#888'>... (").append(lines.length - maxLines).append(" more)</span>\n");
                    break;
                }
                sb.append("<span style='color:").append(toHex(JStudioTheme.getTextSecondary())).append("'>")
                  .append(escapeHtml(truncate(line.trim(), 50))).append("</span>\n");
                count++;
            }
        }
    }

    private void renderGenericNode(StringBuilder sb, PDGNode node) {
        String typeColor = getTypeColor(node);
        String typeName = node.getType().name().toLowerCase();

        sb.append("<b style='color:").append(typeColor).append("'>")
          .append(typeName).append(":</b>\n");
    }

    @Override
    public String getNodeStyle(PDGNode node) {
        PDGNodeType type = node.getType();

        switch (type) {
            case ENTRY:
                return "ENTRY";
            case EXIT:
                return "EXIT";
            case PHI:
                return "PHI";
            case CALL_SITE:
                return "CALL";
            default:
                if (node instanceof PDGInstructionNode) {
                    PDGInstructionNode instrNode = (PDGInstructionNode) node;
                    IRInstruction instr = instrNode.getInstruction();
                    if (instr instanceof InvokeInstruction) {
                        return "CALL";
                    }
                    if (instr instanceof PhiInstruction) {
                        return "PHI";
                    }
                }
                return "NODE";
        }
    }

    private String getTypeColor(PDGNode node) {
        PDGNodeType type = node.getType();

        switch (type) {
            case ENTRY:
                return "#27ae60";
            case EXIT:
                return "#e74c3c";
            case PHI:
                return "#e67e22";
            case CALL_SITE:
                return "#9b59b6";
            default:
                return toHex(JStudioTheme.getTextPrimary());
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
