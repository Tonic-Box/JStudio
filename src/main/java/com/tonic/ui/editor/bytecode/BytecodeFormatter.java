package com.tonic.ui.editor.bytecode;
import com.tonic.analysis.CodePrinter;

import com.tonic.analysis.DisassemblyOptions;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.MethodEntryModel;
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

    /**
     * A whole-class bytecode dump: each method's signature followed by its disassembled code. Convenience for
     * callers that hold only a {@link ClassEntryModel} (e.g. plugins, which cannot reach YABR types directly)
     * and want a single printable String for the class.
     */
    public static String formatClass(ClassEntryModel classEntry) {
        StringBuilder sb = new StringBuilder();
        for (MethodEntryModel methodModel : classEntry.getMethods()) {
            MethodEntry method = methodModel.getMethodEntry();
            sb.append(method.getName()).append(method.getDesc()).append("\n");
            if (method.getCodeAttribute() != null) {
                sb.append(new BytecodeFormatter(method).format());
            } else {
                sb.append("  // no code (abstract or native)\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

}
