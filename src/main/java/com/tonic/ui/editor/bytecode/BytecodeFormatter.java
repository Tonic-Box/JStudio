package com.tonic.ui.editor.bytecode;

import com.tonic.analysis.CodePrinter;

import com.tonic.analysis.DisassemblyOptions;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import lombok.Getter;

/** Formats method bytecode for display; YABR's code printer produces the disassembly and this class only indents it. */
@Getter
public class BytecodeFormatter
{

    /**
     * -- GETTER --
     *  Get the method being formatted.
     */
    private final MethodEntry method;

    /**
     * Creates a formatter for one method.
     *
     * @param method the method to disassemble
     */
    public BytecodeFormatter(MethodEntry method)
    {
        this.method = method;
    }

    private static final int TRIVIAL_CODE_BYTES = 16;

    /**
     * Disassembles the method with the verbose profile.
     *
     * @return one indented "offset: opcode operands" line per instruction plus verbose comment lines; empty when the method has no code
     */
    public String format()
    {
        return format(false);
    }

    /**
     * Disassembles the method with the verbose or compact profile.
     *
     * @param terse true for the compact profile, which drops the header, line numbers, locals and frames
     * @return the indented disassembly, one line per entry; empty when the method has no code
     */
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
     * Dumps a whole class's bytecode, verbose, with every method.
     *
     * @param classEntry the class
     * @return each method's name and descriptor followed by its disassembly
     */
    public static String formatClass(ClassEntryModel classEntry)
    {
        return formatClass(classEntry, false, false);
    }

    /**
     * Dumps a whole class's bytecode.
     *
     * @param classEntry the class
     * @param terse true for the compact disassembly profile
     * @param skipTrivial true to omit methods with at most 16 bytes of code, noting how many were omitted; abstract and native methods are always listed by signature
     * @return each included method's name and descriptor followed by its disassembly
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
     * Lists a class's methods with their code sizes, as a compact index to inspect methods from on demand.
     *
     * @param classEntry the class
     * @return one line per method with its name, descriptor, and code size or a no-code marker; trivial methods are flagged
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
        return code != null && code.getCode() != null && code.getCode().length <= TRIVIAL_CODE_BYTES;
    }

    /**
     * Disassembles one method, for callers such as plugins that cannot reach YABR types.
     *
     * @param methodModel the method
     * @param terse true for the compact disassembly profile
     * @return the indented disassembly; empty when the method has no code
     */
    public static String formatMethod(MethodEntryModel methodModel, boolean terse)
    {
        return new BytecodeFormatter(methodModel.getMethodEntry()).format(terse);
    }

}
