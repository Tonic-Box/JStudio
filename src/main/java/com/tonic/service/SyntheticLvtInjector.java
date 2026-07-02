package com.tonic.service;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.LocalVariableTableAttribute;
import com.tonic.parser.attribute.table.LocalVariableTableEntry;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds class bytes with debugger-friendly LocalVariableTables so a live debugger reading the redefined class
 * sees named locals everywhere in a method - not just at the exact pc where javac scoped them. For methods with
 * no LVT it injects the decompiler's recovered one (stripped/obfuscated targets); for methods that already have
 * one it widens each slot's scope to the whole method so out-of-scope locals stay readable at any breakpoint.
 *
 * <p>Widening is "safe": a slot is only collapsed to a single method-wide entry when all its entries share one
 * type, so a slot reused for different-typed variables is never misread (those entries are left untouched). Only
 * the debug attribute changes - the bytecode, offsets, frames, and breakpoints stay valid.
 */
public final class SyntheticLvtInjector {

    private SyntheticLvtInjector() {
    }

    /**
     * Returns {@code original}'s bytes with every method's LVT recovered (if missing) and safely widened, or null
     * when nothing changed. Operates on a fresh parse, so the caller's {@link ClassFile} is never mutated.
     */
    public static byte[] augment(ClassFile original) {
        if (original == null) {
            return null;
        }
        try {
            ClassFile cf = new ClassFile(new ByteArrayInputStream(original.write()));
            ClassDecompiler decompiler = new ClassDecompiler(cf);
            ConstPool constPool = cf.getConstPool();
            boolean changed = false;
            for (MethodEntry method : cf.getMethods()) {
                CodeAttribute code = method.getCodeAttribute();
                if (code == null || code.getCode() == null || code.getCode().length == 0) {
                    continue;
                }
                int codeLen = code.getCode().length;

                LocalVariableTableAttribute lvt = findLvt(code);
                boolean recovered = false;
                if (lvt == null) {
                    lvt = decompiler.localVariableTableFor(method);
                    if (lvt == null) {
                        continue;
                    }
                    recovered = true;
                }

                List<LocalVariableTableEntry> current = lvt.getLocalVariableTable();
                List<LocalVariableTableEntry> widened = widen(current, constPool, codeLen);
                if (!recovered && !differs(current, widened)) {
                    continue;
                }
                current.clear();
                current.addAll(widened);
                lvt.updateLength();
                if (recovered) {
                    code.getAttributes().add(lvt);
                }
                changed = true;
            }
            return changed ? cf.write() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalVariableTableAttribute findLvt(CodeAttribute code) {
        for (Attribute a : code.getAttributes()) {
            if (a instanceof LocalVariableTableAttribute) {
                return (LocalVariableTableAttribute) a;
            }
        }
        return null;
    }

    /**
     * Widens {@code entries}: for each slot whose entries all share one type descriptor, a single method-wide
     * entry spanning {@code [0, codeLen)}; type-mixed slots keep their original (scope-accurate) entries. Slots
     * are emitted in first-seen order.
     */
    private static List<LocalVariableTableEntry> widen(List<LocalVariableTableEntry> entries, ConstPool constPool,
                                                       int codeLen) {
        Map<Integer, List<LocalVariableTableEntry>> bySlot = new LinkedHashMap<>();
        for (LocalVariableTableEntry e : entries) {
            bySlot.computeIfAbsent(e.getIndex(), k -> new ArrayList<>()).add(e);
        }
        List<LocalVariableTableEntry> out = new ArrayList<>();
        for (List<LocalVariableTableEntry> group : bySlot.values()) {
            LocalVariableTableEntry first = group.get(0);
            boolean sameType = true;
            for (LocalVariableTableEntry e : group) {
                if (e.getDescriptorIndex() != first.getDescriptorIndex()) {
                    sameType = false;
                    break;
                }
            }
            if (sameType) {
                out.add(new LocalVariableTableEntry(constPool, 0, codeLen,
                        first.getNameIndex(), first.getDescriptorIndex(), first.getIndex()));
            } else {
                out.addAll(group);
            }
        }
        return out;
    }

    /** True when {@code widened} differs from {@code current} (so the class is worth redefining). */
    private static boolean differs(List<LocalVariableTableEntry> current, List<LocalVariableTableEntry> widened) {
        if (current.size() != widened.size()) {
            return true;
        }
        for (int i = 0; i < current.size(); i++) {
            LocalVariableTableEntry a = current.get(i);
            LocalVariableTableEntry b = widened.get(i);
            if (a.getStartPc() != b.getStartPc() || a.getLengthPc() != b.getLengthPc()
                    || a.getNameIndex() != b.getNameIndex() || a.getDescriptorIndex() != b.getDescriptorIndex()
                    || a.getIndex() != b.getIndex()) {
                return true;
            }
        }
        return false;
    }
}
