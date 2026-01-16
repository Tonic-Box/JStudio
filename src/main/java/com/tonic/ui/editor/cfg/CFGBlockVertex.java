package com.tonic.ui.editor.cfg;

import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.theme.SyntaxColors;
import lombok.Getter;

import java.awt.Color;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
public class CFGBlockVertex {
    private final CFGBlock block;
    private final MethodEntry method;
    private final boolean showIR;
    private String cachedHtml;
    private IRMethod irMethod;

    public CFGBlockVertex(CFGBlock block, MethodEntry method, boolean showIR, ConstPool constPool) {
        this.block = block;
        this.method = method;
        this.showIR = showIR;

        if (showIR && method.getCodeAttribute() != null) {
            try {
                SSA ssa = new SSA(constPool);
                this.irMethod = ssa.lift(method);
            } catch (Exception e) {
                this.irMethod = null;
            }
        }
    }

    @Override
    public String toString() {
        if (cachedHtml == null) {
            cachedHtml = buildHtml();
        }
        return cachedHtml;
    }

    private String buildHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><pre style='margin:4px;font-family:monospace;font-size:10px'>");

        if (block.isExceptionHandler()) {
            sb.append("<b style='color:#e67e22'>catch (").append(escapeHtml(block.getHandlerType())).append(")</b>\n");
        }

        if (block.getStartOffset() == 0) {
            sb.append("<b style='color:#27ae60'>entry:</b>\n");
        }

        if (showIR && irMethod != null) {
            renderIRInstructions(sb);
        } else {
            renderBytecodeInstructions(sb);
        }

        sb.append("</pre></html>");
        return sb.toString();
    }

    private void renderBytecodeInstructions(StringBuilder sb) {
        List<Instruction> instructions = block.getInstructions();
        int maxLines = 15;
        int count = 0;

        for (Instruction instr : instructions) {
            if (count >= maxLines) {
                sb.append("... (").append(instructions.size() - maxLines).append(" more)\n");
                break;
            }
            sb.append(String.format("<span style='color:#888'>%04d:</span> %s\n",
                    instr.getOffset(), escapeHtml(formatInstruction(instr))));
            count++;
        }
    }

    private void renderIRInstructions(StringBuilder sb) {
        IRBlock irBlock = findMatchingIRBlock();
        if (irBlock == null) {
            sb.append("<i style='color:#888'>No IR available</i>\n");
            return;
        }

        int maxLines = 15;
        int count = 0;

        for (IRInstruction phi : irBlock.getPhiInstructions()) {
            if (count >= maxLines) break;
            sb.append(formatIRInstruction(phi.toString(), true)).append("\n");
            count++;
        }

        for (IRInstruction instr : irBlock.getInstructions()) {
            if (count >= maxLines) {
                sb.append("... (more)\n");
                break;
            }
            sb.append(formatIRInstruction(instr.toString(), false)).append("\n");
            count++;
        }
    }

    private static final Pattern VALUE_PATTERN = Pattern.compile("\\bv\\d+\\b");
    private static final Set<String> CONTROL_OPS = Set.of(
            "GOTO", "IF", "RETURN", "THROW", "SWITCH", "TABLESWITCH", "LOOKUPSWITCH"
    );
    private static final Set<String> INVOKE_OPS = Set.of(
            "INVOKEVIRTUAL", "INVOKESPECIAL", "INVOKESTATIC", "INVOKEINTERFACE", "INVOKEDYNAMIC"
    );
    private static final Set<String> FIELD_OPS = Set.of(
            "GETFIELD", "PUTFIELD", "GETSTATIC", "PUTSTATIC"
    );
    private static final Set<String> NEW_OPS = Set.of(
            "NEW", "NEWARRAY", "ANEWARRAY", "MULTIANEWARRAY"
    );
    private static final Set<String> CAST_OPS = Set.of(
            "CHECKCAST", "INSTANCEOF"
    );

    private String formatIRInstruction(String instr, boolean isPhi) {
        if (instr.length() > 50) {
            instr = instr.substring(0, 47) + "...";
        }

        String escaped = escapeHtml(instr);

        if (isPhi) {
            escaped = colorize(escaped, SyntaxColors.getIrPhi());
            escaped = highlightValues(escaped);
            return escaped;
        }

        String upperInstr = instr.toUpperCase();
        Color instrColor = null;

        for (String op : CONTROL_OPS) {
            if (upperInstr.contains(op)) {
                instrColor = SyntaxColors.getIrControl();
                break;
            }
        }
        if (instrColor == null) {
            for (String op : INVOKE_OPS) {
                if (upperInstr.contains(op)) {
                    instrColor = SyntaxColors.getIrInvoke();
                    break;
                }
            }
        }
        if (instrColor == null) {
            for (String op : FIELD_OPS) {
                if (upperInstr.contains(op)) {
                    instrColor = SyntaxColors.getIrGetField();
                    break;
                }
            }
        }
        if (instrColor == null) {
            for (String op : NEW_OPS) {
                if (upperInstr.contains(op)) {
                    instrColor = SyntaxColors.getIrNew();
                    break;
                }
            }
        }
        if (instrColor == null) {
            for (String op : CAST_OPS) {
                if (upperInstr.contains(op)) {
                    instrColor = SyntaxColors.getIrCast();
                    break;
                }
            }
        }

        if (instrColor != null) {
            escaped = colorize(escaped, instrColor);
        }

        escaped = highlightValues(escaped);
        return escaped;
    }

    private String highlightValues(String text) {
        String valueColor = colorToHex(SyntaxColors.getIrValue());
        Matcher matcher = VALUE_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            result.append(text, lastEnd, matcher.start());
            result.append("<span style='color:").append(valueColor).append("'>")
                  .append(matcher.group())
                  .append("</span>");
            lastEnd = matcher.end();
        }
        result.append(text.substring(lastEnd));
        return result.toString();
    }

    private String colorize(String text, Color color) {
        return "<span style='color:" + colorToHex(color) + "'>" + text + "</span>";
    }

    private String colorToHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private IRBlock findMatchingIRBlock() {
        if (irMethod == null) return null;

        int targetOffset = block.getStartOffset();
        for (IRBlock irBlock : irMethod.getBlocks()) {
            if (irBlock.getBytecodeOffset() == targetOffset) {
                return irBlock;
            }
        }

        for (IRBlock irBlock : irMethod.getBlocks()) {
            int blockOffset = irBlock.getBytecodeOffset();
            if (blockOffset >= block.getStartOffset() && blockOffset < block.getEndOffset()) {
                return irBlock;
            }
        }

        return null;
    }

    private String formatInstruction(Instruction instr) {
        String str = instr.toString();
        if (str.length() > 50) {
            str = str.substring(0, 47) + "...";
        }
        return str;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
