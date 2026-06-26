package com.tonic.ui.editor.source;

import com.tonic.analysis.source.decompile.DecompileResult;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.model.SourceLineMaps;
import com.tonic.parser.ClassFile;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.LocalVariableTableAttribute;
import com.tonic.parser.attribute.table.LocalVariableTableEntry;
import com.tonic.parser.constpool.Utf8Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Resolves where in the decompiled source each of a paused frame's runtime variables should be annotated:
 * locals at their declaration line (the method's LocalVariableTable start-PC mapped through the offset-to-line
 * map), and {@code this}/arguments (or anything without an in-scope LVT entry) at the method's signature line.
 * The LVT is read from the target bytecode, so placement needs no JDI.
 */
final class RuntimeHintPlacement {

    private RuntimeHintPlacement() {
    }

    /** Maps each name in {@code varNames} to a 1-based source line, omitting any that can't be placed. */
    static Map<String, Integer> place(ClassEntryModel classEntry, String methodKey, int pc, List<String> varNames) {
        Map<String, Integer> out = new HashMap<>();
        NavigableMap<Integer, Integer> lineMap = lineMap(classEntry, methodKey);
        int sigLine = signatureLine(classEntry, methodKey);
        List<LvtEntry> lvt = lvt(classEntry, methodKey);

        for (String name : varNames) {
            if ("this".equals(name)) {
                if (sigLine > 0) {
                    out.put(name, sigLine);
                }
                continue;
            }
            int line = -1;
            LvtEntry match = bestScope(lvt, name, pc);
            if (match != null && lineMap != null) {
                line = SourceLineMaps.sourceLineForPc(lineMap, match.startPc);
            }
            if (line <= 0) {
                line = sigLine;
            }
            if (line > 0) {
                out.put(name, line);
            }
        }
        return out;
    }

    private static NavigableMap<Integer, Integer> lineMap(ClassEntryModel classEntry, String methodKey) {
        Map<String, NavigableMap<Integer, Integer>> maps = classEntry.getSourceLineMaps();
        return maps != null ? maps.get(methodKey) : null;
    }

    private static int signatureLine(ClassEntryModel classEntry, String methodKey) {
        Map<String, DecompileResult.MethodSpan> spans = classEntry.getMethodSpans();
        if (spans == null) {
            return -1;
        }
        DecompileResult.MethodSpan span = spans.get(methodKey);
        return span != null ? span.getStartLine() : -1;
    }

    /** The LVT entry for {@code name} whose scope contains {@code pc}, else any entry with that name, else null. */
    private static LvtEntry bestScope(List<LvtEntry> lvt, String name, int pc) {
        LvtEntry any = null;
        for (LvtEntry e : lvt) {
            if (!e.name.equals(name)) {
                continue;
            }
            any = e;
            if (pc >= e.startPc && pc < e.startPc + e.lengthPc) {
                return e;
            }
        }
        return any;
    }

    private static List<LvtEntry> lvt(ClassEntryModel classEntry, String methodKey) {
        List<LvtEntry> out = new ArrayList<>();
        try {
            int paren = methodKey.indexOf('(');
            if (paren <= 0) {
                return out;
            }
            MethodEntryModel me = classEntry.getMethod(methodKey.substring(0, paren), methodKey.substring(paren));
            if (me == null || me.getMethodEntry().getCodeAttribute() == null) {
                return out;
            }
            CodeAttribute code = me.getMethodEntry().getCodeAttribute();
            ClassFile cf = classEntry.getClassFile();
            for (Attribute attr : code.getAttributes()) {
                if (attr instanceof LocalVariableTableAttribute) {
                    for (LocalVariableTableEntry e : ((LocalVariableTableAttribute) attr).getLocalVariableTable()) {
                        out.add(new LvtEntry(resolveUtf8(cf, e.getNameIndex()), e.getStartPc(), e.getLengthPc()));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String resolveUtf8(ClassFile cf, int index) {
        try {
            Object item = cf.getConstPool().getItem(index);
            if (item instanceof Utf8Item) {
                return ((Utf8Item) item).getValue();
            }
        } catch (Exception ignored) {
        }
        return "#" + index;
    }

    private static final class LvtEntry {
        final String name;
        final int startPc;
        final int lengthPc;

        LvtEntry(String name, int startPc, int lengthPc) {
            this.name = name;
            this.startPc = startPc;
            this.lengthPc = lengthPc;
        }
    }
}
