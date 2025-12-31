package com.tonic.ui.editor.bytecode;

import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.table.ExceptionTableEntry;
import lombok.Getter;

/**
 * Formats bytecode for display in the UI.
 * Uses the existing CodePrinter but formats output for styled display.
 */
@Getter
public class BytecodeFormatter {

    /**
     * -- GETTER --
     *  Get the method being formatted.
     */
    private final MethodEntry method;

    public BytecodeFormatter(MethodEntry method) {
        this.method = method;
    }

    /**
     * Format the method's bytecode for display.
     * Returns a string with format "offset: opcode operands" per line.
     */
    public String format() {
        CodeAttribute code = method.getCodeAttribute();
        if (code == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // Method info header
        sb.append("  // max_stack=").append(code.getMaxStack());
        sb.append(", max_locals=").append(code.getMaxLocals());
        sb.append(", code_length=").append(code.getCode().length).append("\n");

        // Exception table if present
        if (!code.getExceptionTable().isEmpty()) {
            sb.append("  // Exception table:\n");
            for (ExceptionTableEntry entry : code.getExceptionTable()) {
                sb.append("  //   from=").append(entry.getStartPc());
                sb.append(" to=").append(entry.getEndPc());
                sb.append(" target=").append(entry.getHandlerPc());
                sb.append(" type=").append(entry.getCatchType()).append("\n");
            }
        }

        // Use existing CodePrinter but reformat for our display
        String rawDisasm = code.prettyPrintCode();

        // The CodePrinter outputs format: "0000: opcode          operands"
        // We want: "offset: opcode operands"
        String[] lines = rawDisasm.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            // Clean up the line - CodePrinter already formats as "offset: opcode operands"
            sb.append("  ").append(line.trim()).append("\n");
        }

        return sb.toString();
    }

}
