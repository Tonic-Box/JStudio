package com.tonic.ui.editor.cfg;

import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import lombok.Getter;

import java.util.List;

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
            sb.append(escapeHtml(phi.toString())).append("\n");
            count++;
        }

        for (IRInstruction instr : irBlock.getInstructions()) {
            if (count >= maxLines) {
                sb.append("... (more)\n");
                break;
            }
            sb.append(escapeHtml(instr.toString())).append("\n");
            count++;
        }
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
