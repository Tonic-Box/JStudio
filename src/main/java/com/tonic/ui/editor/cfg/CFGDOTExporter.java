package com.tonic.ui.editor.cfg;

import java.util.List;

public class CFGDOTExporter {

    public String export(List<CFGBlock> blocks, String methodName) {
        StringBuilder sb = new StringBuilder();

        sb.append("digraph CFG {\n");
        sb.append("  rankdir=TB;\n");
        sb.append("  node [shape=box, fontname=\"monospace\", fontsize=10];\n");
        sb.append("  edge [fontname=\"monospace\", fontsize=9];\n");
        sb.append("\n");

        if (methodName != null && !methodName.isEmpty()) {
            sb.append("  label=\"").append(escapeLabel(methodName)).append("\";\n");
            sb.append("  labelloc=t;\n\n");
        }

        for (CFGBlock block : blocks) {
            String nodeId = "B" + block.getId();
            String label = buildNodeLabel(block);
            String style = getNodeStyle(block);

            sb.append("  ").append(nodeId)
              .append(" [label=\"").append(escapeLabel(label)).append("\"")
              .append(style).append("];\n");
        }

        sb.append("\n");

        for (CFGBlock block : blocks) {
            String sourceId = "B" + block.getId();
            for (CFGEdge edge : block.getOutEdges()) {
                String targetId = "B" + edge.getTarget().getId();
                String edgeStyle = getEdgeStyle(edge.getType());

                sb.append("  ").append(sourceId).append(" -> ").append(targetId)
                  .append(" [").append(edgeStyle).append("];\n");
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String buildNodeLabel(CFGBlock block) {
        StringBuilder sb = new StringBuilder();

        if (block.getStartOffset() == 0) {
            sb.append("entry\\n");
        }
        if (block.isExceptionHandler()) {
            sb.append("catch (").append(block.getHandlerType()).append(")\\n");
        }

        sb.append("B").append(block.getId());
        sb.append(" [").append(String.format("%04d", block.getStartOffset()));
        sb.append("-").append(String.format("%04d", block.getEndOffset())).append("]\\n");

        int maxInstr = 8;
        int count = 0;
        for (var instr : block.getInstructions()) {
            if (count >= maxInstr) {
                sb.append("... (").append(block.getInstructions().size() - maxInstr).append(" more)\\n");
                break;
            }
            String instrStr = instr.toString();
            if (instrStr.length() > 35) {
                instrStr = instrStr.substring(0, 32) + "...";
            }
            sb.append(String.format("%04d", instr.getOffset())).append(": ").append(instrStr).append("\\n");
            count++;
        }

        return sb.toString().trim();
    }

    private String getNodeStyle(CFGBlock block) {
        if (block.getStartOffset() == 0) {
            return ", color=\"#27ae60\", penwidth=2";
        }
        if (block.isExceptionHandler()) {
            return ", color=\"#e67e22\", penwidth=2";
        }
        return "";
    }

    private String getEdgeStyle(CFGEdgeType type) {
        StringBuilder sb = new StringBuilder();
        sb.append("color=\"").append(type.getColor()).append("\"");

        switch (type) {
            case CONDITIONAL_TRUE:
                sb.append(", label=\"T\"");
                break;
            case CONDITIONAL_FALSE:
                sb.append(", label=\"F\"");
                break;
            case EXCEPTION:
                sb.append(", style=dashed, label=\"exc\"");
                break;
            case SWITCH_DEFAULT:
                sb.append(", label=\"default\"");
                break;
            case SWITCH_CASE:
                sb.append(", label=\"case\"");
                break;
            default:
                break;
        }

        return sb.toString();
    }

    private String escapeLabel(String text) {
        if (text == null) return "";
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("<", "\\<")
            .replace(">", "\\>")
            .replace("{", "\\{")
            .replace("}", "\\}");
    }
}
