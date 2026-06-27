package com.tonic.service;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.LocalVariableTableAttribute;
import com.tonic.parser.attribute.table.LocalVariableTableEntry;
import com.tonic.parser.attribute.table.LvtSupport;
import com.tonic.parser.constpool.Utf8Item;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Renames a local variable (or parameter) by editing its method's LocalVariableTable entry and re-parsing the
 * class. The decompiler renders local names from the LVT, so the new name appears on the next decompile - no
 * source rewriting and no cross-class reference updates (locals are method-scoped). For a stripped method (no
 * LVT) a synthetic table is materialized first (from the decompiler's recovered names) so the rename persists.
 */
public final class LocalVariableRenamer {

    private LocalVariableRenamer() {
    }

    /**
     * A located rename target: the method, the JVM local slot, the current name, the JVM descriptor, and the
     * exact LVT scope of the clicked occurrence. The scope is what distinguishes separate variables that reuse
     * one slot+name across disjoint regions (e.g. the {@code i} of three sequential {@code for} loops) - only
     * the entry matching this scope is renamed.
     */
    public static final class Target {
        public final String methodKey;
        public final int slot;
        public final String oldName;
        public final String descriptor;
        public final int startPc;
        public final int lengthPc;

        Target(String methodKey, int slot, String oldName, String descriptor, int startPc, int lengthPc) {
            this.methodKey = methodKey;
            this.slot = slot;
            this.oldName = oldName;
            this.descriptor = descriptor;
            this.startPc = startPc;
            this.lengthPc = lengthPc;
        }
    }

    /**
     * Resolves the local variable named {@code word} at 1-based source {@code line}, or null when the click
     * isn't a renamable local (no enclosing method, {@code this}, or no matching in-scope LVT entry). For a
     * stripped method this materializes the recovered LVT in memory to find the slot (without modifying it).
     */
    public static Target locate(ClassEntryModel classEntry, int line, String word) {
        if (word == null || word.isEmpty() || "this".equals(word)) {
            return null;
        }
        LineLoc loc = lineLoc(classEntry, line);
        if (loc == null) {
            return null;
        }
        MethodEntry method = methodEntry(classEntry, loc.methodKey);
        CodeAttribute code = method != null ? method.getCodeAttribute() : null;
        if (code == null) {
            return null;
        }
        ClassFile cf = classEntry.getClassFile();
        LocalVariableTableAttribute lvt = localVariableTable(code);
        if (lvt == null) {
            lvt = new ClassDecompiler(cf).localVariableTableFor(method);
        }
        if (lvt == null) {
            return null;
        }
        LocalVariableTableEntry match = bestEntry(lvt, cf, word, loc.offset);
        if (match == null) {
            return null;
        }
        return new Target(loc.methodKey, match.getIndex(), word, utf8(cf, match.getDescriptorIndex()),
                match.getStartPc(), match.getLengthPc());
    }

    /**
     * Whether {@code newName} already names a different in-scope local in the target's method (renaming would
     * shadow/collide). Disjoint-scope reuse of the name is allowed and reported as no conflict.
     */
    public static boolean wouldConflict(ClassEntryModel classEntry, Target target, String newName) {
        MethodEntry method = methodEntry(classEntry, target.methodKey);
        CodeAttribute code = method != null ? method.getCodeAttribute() : null;
        if (code == null) {
            return false;
        }
        ClassFile cf = classEntry.getClassFile();
        LocalVariableTableAttribute lvt = localVariableTable(code);
        if (lvt == null) {
            lvt = new ClassDecompiler(cf).localVariableTableFor(method);
        }
        if (lvt == null) {
            return false;
        }
        int start = target.startPc;
        int end = target.startPc + target.lengthPc;
        for (LocalVariableTableEntry e : lvt.getLocalVariableTable()) {
            if (e.getIndex() != target.slot && newName.equals(utf8(cf, e.getNameIndex()))
                    && e.getStartPc() < end && start < e.getStartPc() + e.getLengthPc()) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code e} is exactly the clicked occurrence's entry: same slot, name, and scope. */
    private static boolean isTargetEntry(LocalVariableTableEntry e, Target target, ClassFile cf) {
        return e.getIndex() == target.slot
                && e.getStartPc() == target.startPc
                && e.getLengthPc() == target.lengthPc
                && target.oldName.equals(utf8(cf, e.getNameIndex()));
    }

    /**
     * Applies the rename: edits the target slot's LVT entries (materializing a synthetic LVT first if the
     * method has none) and re-installs the re-parsed class on {@code classEntry}. Returns false if the method or
     * its LVT can't be resolved.
     */
    public static boolean rename(ClassEntryModel classEntry, Target target, String newName) {
        try {
            ClassFile cf = new ClassFile(new ByteArrayInputStream(classEntry.getClassFile().write()));
            MethodEntry method = methodEntry(cf, target.methodKey);
            CodeAttribute code = method != null ? method.getCodeAttribute() : null;
            if (code == null) {
                return false;
            }
            LocalVariableTableAttribute lvt = localVariableTable(code);
            if (lvt == null) {
                lvt = new ClassDecompiler(cf).localVariableTableFor(method);
                if (lvt == null) {
                    return false;
                }
                code.getAttributes().add(lvt);
            }
            ConstPool cp = cf.getConstPool();
            List<LocalVariableTableEntry> rebuilt = new ArrayList<>();
            for (LocalVariableTableEntry e : lvt.getLocalVariableTable()) {
                if (isTargetEntry(e, target, cf)) {
                    rebuilt.add(LvtSupport.entry(cp, e.getIndex(), newName,
                            utf8(cf, e.getDescriptorIndex()), e.getStartPc(), e.getLengthPc()));
                } else {
                    rebuilt.add(e);
                }
            }
            lvt.setLocalVariableTable(rebuilt);
            lvt.updateLength();
            code.updateLength();
            classEntry.updateClassFile(cf);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---- helpers ------------------------------------------------------------------------------------

    /** The LVT entry whose name matches {@code word} and whose scope covers {@code offset}, else any with that name. */
    private static LocalVariableTableEntry bestEntry(LocalVariableTableAttribute lvt, ClassFile cf,
                                                     String word, int offset) {
        LocalVariableTableEntry any = null;
        for (LocalVariableTableEntry e : lvt.getLocalVariableTable()) {
            if (!word.equals(utf8(cf, e.getNameIndex()))) {
                continue;
            }
            any = e;
            if (offset >= 0 && offset >= e.getStartPc() && offset < e.getStartPc() + e.getLengthPc()) {
                return e;
            }
        }
        return any;
    }

    private static LocalVariableTableAttribute localVariableTable(CodeAttribute code) {
        for (Attribute a : code.getAttributes()) {
            if (a instanceof LocalVariableTableAttribute) {
                return (LocalVariableTableAttribute) a;
            }
        }
        return null;
    }

    /** The method named by {@code "name(desc)"} on the project model's current class file. */
    private static MethodEntry methodEntry(ClassEntryModel classEntry, String methodKey) {
        int paren = methodKey.indexOf('(');
        if (paren <= 0) {
            return null;
        }
        MethodEntryModel me = classEntry.getMethod(methodKey.substring(0, paren), methodKey.substring(paren));
        return me != null ? me.getMethodEntry() : null;
    }

    /** The method named by {@code "name(desc)"} on a freshly parsed class file. */
    private static MethodEntry methodEntry(ClassFile cf, String methodKey) {
        int paren = methodKey.indexOf('(');
        if (paren <= 0) {
            return null;
        }
        String name = methodKey.substring(0, paren);
        String desc = methodKey.substring(paren);
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name) && m.getDesc().equals(desc)) {
                return m;
            }
        }
        return null;
    }

    /** The method key + a representative bytecode offset for a 1-based source line, via the decompile line maps. */
    private static LineLoc lineLoc(ClassEntryModel classEntry, int line) {
        Map<String, NavigableMap<Integer, Integer>> maps = classEntry.getSourceLineMaps();
        if (maps == null) {
            return null;
        }
        String bestKey = null;
        int bestOffset = Integer.MAX_VALUE;
        for (Map.Entry<String, NavigableMap<Integer, Integer>> m : maps.entrySet()) {
            for (Map.Entry<Integer, Integer> e : m.getValue().entrySet()) {
                if (e.getValue() == line && e.getKey() < bestOffset) {
                    bestOffset = e.getKey();
                    bestKey = m.getKey();
                }
            }
        }
        return bestKey == null ? null : new LineLoc(bestKey, bestOffset);
    }

    private static String utf8(ClassFile cf, int index) {
        Object item = cf.getConstPool().getItem(index);
        return item instanceof Utf8Item ? ((Utf8Item) item).getValue() : null;
    }

    private static final class LineLoc {
        final String methodKey;
        final int offset;

        LineLoc(String methodKey, int offset) {
            this.methodKey = methodKey;
            this.offset = offset;
        }
    }
}
