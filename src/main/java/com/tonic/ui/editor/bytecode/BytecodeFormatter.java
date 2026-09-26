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
 *CodeAttribute, DisassemblyOptions)} verbose profile; this class only applies the UI indentation.
 */
@Getter
public class BytecodeFormatter
{

    /**
     * -- GETTER --
     *  Get the method being formatted.
     */
    private final MethodEntry method;

    public BytecodeFormatter(MethodEntry method)
    {
        this.method = method;
    }

    /** Code-size (bytes) at or below which a method's disassembly is treated as trivial (getter/setter/tiny). */
    private static final int TRIVIAL_CODE_BYTES = 16;

    /**
     * Format the method's bytecode for display (verbose profile).
     * Returns a string with format "offset: opcode operands" per line, plus verbose comment lines.
     */
    public String format()
    {
        return format(false);
    }

    /** As {@link #format()} but {@code terse} selects YABR's compact profile (no header/lines/locals/frames/etc). */
    public String format(boolean terse)
    {
        CodeAttribute code = method.getCodeAttribute();
        if (code == null)
        {
            return "";
        }
        DisassemblyOptions options = terse ? DisassemblyOptions.terse() : DisassemblyOptions.verbose();
        StringBuilder sb = new StringBuilder();
        for (String line : CodePrinter.prettyPrintCode(code, options).split("\n"))
        {
            if (line.isEmpty())
            {
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
    public static String formatClass(ClassEntryModel classEntry)
    {
        return formatClass(classEntry, false, false);
    }

    /**
     * A whole-class bytecode dump. {@code terse} uses YABR's compact profile; {@code skipTrivial} omits the body
     * of trivial methods (getters/setters, {@code super()}-only constructors, tiny returns) with a summary note,
     * to keep the token cost of feeding a class to an LLM down.
     */
    public static String formatClass(ClassEntryModel classEntry, boolean terse, boolean skipTrivial)
    {
        StringBuilder sb = new StringBuilder();
        int skipped = 0;
        for (MethodEntryModel methodModel : classEntry.getMethods())
        {
            MethodEntry method = methodModel.getMethodEntry();
            if (skipTrivial && isTrivial(method))
            {
                skipped++;
                continue;
            }
            sb.append(method.getName()).append(method.getDesc()).append("\n");
            if (method.getCodeAttribute() != null)
            {
                sb.append(new BytecodeFormatter(method).format(terse));
            }
            else
            {
                sb.append("  // no code (abstract or native)\n");
            }
            sb.append("\n");
        }
        if (skipped > 0)
        {
            sb.append("// ").append(skipped).append(" trivial method(s) omitted (getters/setters/tiny)\n");
        }
        return sb.toString().trim();
    }

    /**
     * A compact per-method index (signature + code size) for pointing an LLM at methods it can inspect on demand,
     * instead of inlining the whole disassembly.
     */
    public static String indexOf(ClassEntryModel classEntry)
    {
        StringBuilder sb = new StringBuilder();
        for (MethodEntryModel methodModel : classEntry.getMethods())
        {
            MethodEntry method = methodModel.getMethodEntry();
            CodeAttribute code = method.getCodeAttribute();
            sb.append("  ").append(method.getName()).append(method.getDesc());
            if (code == null || code.getCode() == null)
            {
                sb.append("  (no code)");
            }
            else
            {
                sb.append("  (").append(code.getCode().length).append(" bytes");
                if (isTrivial(method))
                {
                    sb.append(", trivial");
                }
                sb.append(')');
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private static boolean isTrivial(MethodEntry method)
    {
        CodeAttribute code = method.getCodeAttribute();
        return code == null || code.getCode() == null || code.getCode().length <= TRIVIAL_CODE_BYTES;
    }

    /** One method's disassembly from a {@link MethodEntryModel}, for callers that can't reach YABR types (plugins). */
    public static String formatMethod(MethodEntryModel methodModel, boolean terse)
    {
        return new BytecodeFormatter(methodModel.getMethodEntry()).format(terse);
    }

}
