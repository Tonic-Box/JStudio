package com.tonic.ui.editor.bytecode;
import com.tonic.analysis.CodePrinter;

import com.tonic.analysis.DisassemblyOptions;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import lombok.Getter;

/**
 * Formats bytecode for display in the UI.
 *
 * <p>All disassembly (header, line numbers, local-variable and stack-frame markers, exception table,
 * resolved invokedynamic bootstraps) is produced by YABR's {@link CodePrinter#prettyPrintCode(
 * CodeAttribute, DisassemblyOptions)} verbose profile; this class only applies the UI indentation.
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
     * Returns a string with format "offset: opcode operands" per line, plus verbose comment lines.
     */
    public String format() {
        CodeAttribute code = method.getCodeAttribute();
        if (code == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (String line : CodePrinter.prettyPrintCode(code, DisassemblyOptions.verbose()).split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            sb.append("  ").append(line.stripTrailing()).append("\n");
        }
        return sb.toString();
    }

}
