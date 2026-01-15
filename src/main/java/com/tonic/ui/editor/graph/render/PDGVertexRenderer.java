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

        String typeColor = getTypeColor(node);
        String typeName = getTypeName(node);

        sb.append("<b style='color:").append(typeColor).append("'>")
          .append(escapeHtml(typeName))
          .append("</b>\n");

        if (node instanceof PDGRegionNode) {
            PDGRegionNode region = (PDGRegionNode) node;
            String label = region.getLabel();
            if (label != null && !label.isEmpty() && !label.equals(typeName)) {
                sb.append("<span style='color:").append(toHex(JStudioTheme.getTextSecondary()))
                  .append("'>").append(escapeHtml(truncate(label, 40))).append("</span>\n");
            }
        } else if (node instanceof PDGInstructionNode) {
            PDGInstructionNode instrNode = (PDGInstructionNode) node;
            IRInstruction instr = instrNode.getInstruction();
            if (instr != null) {
                String repr = instr.toString();
                sb.append("<span style='color:").append(toHex(JStudioTheme.getTextSecondary()))
                  .append("'>").append(escapeHtml(truncate(repr, 50))).append("</span>\n");
            }
        }

        sb.append("</pre></html>");
        return sb.toString();
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

    private String getTypeName(PDGNode node) {
        if (node instanceof PDGRegionNode) {
            PDGRegionNode region = (PDGRegionNode) node;
            if (region.isEntry()) return "ENTRY";
            if (region.isExit()) return "EXIT";
            return "REGION";
        }

        if (node instanceof PDGInstructionNode) {
            PDGInstructionNode instrNode = (PDGInstructionNode) node;
            IRInstruction instr = instrNode.getInstruction();
            if (instr instanceof InvokeInstruction) return "INVOKE";
            if (instr instanceof PhiInstruction) return "PHI";
            return "INSTR";
        }

        return node.getType().name();
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
                if (node instanceof PDGInstructionNode) {
                    PDGInstructionNode instrNode = (PDGInstructionNode) node;
                    IRInstruction instr = instrNode.getInstruction();
                    if (instr instanceof InvokeInstruction) return "#9b59b6";
                    if (instr instanceof PhiInstruction) return "#e67e22";
                }
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
