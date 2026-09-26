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

/** Renames a local variable or parameter by editing its method's LocalVariableTable, adding one from the decompiler's recovered names when the method has none. */
public final class LocalVariableRenamer
{

    private LocalVariableRenamer()
    {
    }

    /** A located rename target: the method, local slot, current name, descriptor and the exact LVT scope of the clicked occurrence, so only that entry is renamed. */
    public static final class Target
    {
        public final String methodKey;
        public final int slot;
        public final String oldName;
        public final String descriptor;
        public final int startPc;
        public final int lengthPc;

        Target(String methodKey, int slot, String oldName, String descriptor, int startPc, int lengthPc)
        {
            this.methodKey = methodKey;
            this.slot = slot;
            this.oldName = oldName;
            this.descriptor = descriptor;
            this.startPc = startPc;
            this.lengthPc = lengthPc;
        }
    }

    /**
     * Finds the local variable a word on a decompiled source line refers to, using a recovered table in memory for a method with no LVT.
     *
     * @param classEntry the class shown in the source view
     * @param line the 1-based source line
     * @param word the clicked identifier
     * @return the target, preferring the entry in scope at the line, or null when the word is empty or this, the line maps to no method with code, or no entry has that name
     */
    public static Target locate(ClassEntryModel classEntry, int line, String word)
    {
        if (word == null || word.isEmpty() || "this".equals(word))
        {
            return null;
        }
        LineLoc loc = lineLoc(classEntry, line);
        if (loc == null)
        {
            return null;
        }
        MethodEntry method = methodEntry(classEntry, loc.methodKey);
        CodeAttribute code = method != null ? method.getCodeAttribute() : null;
        if (code == null)
        {
            return null;
        }
        ClassFile cf = classEntry.getClassFile();
        LocalVariableTableAttribute lvt = localVariableTable(code);
        if (lvt == null)
        {
            lvt = new ClassDecompiler(cf).localVariableTableFor(method);
        }
        if (lvt == null)
        {
            return null;
        }
        LocalVariableTableEntry match = bestEntry(lvt, cf, word, loc.offset);
        if (match == null)
        {
            return null;
        }
        return new Target(loc.methodKey, match.getIndex(), word, utf8(cf, match.getDescriptorIndex()), match.getStartPc(), match.getLengthPc());
    }

    /**
     * Checks whether a new name is already used by a local in another slot whose scope overlaps the target's.
     *
     * @param classEntry the class holding the method
     * @param target the variable being renamed
     * @param newName the proposed name
     * @return true if the rename would collide; false when it would not, or the method or its table cannot be resolved
     */
    public static boolean wouldConflict(ClassEntryModel classEntry, Target target, String newName)
    {
        MethodEntry method = methodEntry(classEntry, target.methodKey);
        CodeAttribute code = method != null ? method.getCodeAttribute() : null;
        if (code == null)
        {
            return false;
        }
        ClassFile cf = classEntry.getClassFile();
        LocalVariableTableAttribute lvt = localVariableTable(code);
        if (lvt == null)
        {
            lvt = new ClassDecompiler(cf).localVariableTableFor(method);
        }
        if (lvt == null)
        {
            return false;
        }
        int start = target.startPc;
        int end = target.startPc + target.lengthPc;
        for (LocalVariableTableEntry e : lvt.getLocalVariableTable())
        {
            if (e.getIndex() != target.slot && newName.equals(utf8(cf, e.getNameIndex()))
                    && e.getStartPc() < end && start < e.getStartPc() + e.getLengthPc())
            {
                return true;
            }
        }
        return false;
    }

    private static boolean isTargetEntry(LocalVariableTableEntry e, Target target, ClassFile cf)
    {
        return e.getIndex() == target.slot
                && e.getStartPc() == target.startPc
                && e.getLengthPc() == target.lengthPc
                && target.oldName.equals(utf8(cf, e.getNameIndex()));
    }

    /**
     * Renames the target's LVT entry in a re-parsed copy of the class, adding a recovered table first if the method has none, and installs the copy on the class entry.
     *
     * @param classEntry the class holding the method
     * @param target the variable to rename
     * @param newName the new name
     * @return true if renamed; false if the method or its table cannot be resolved or the class fails to rebuild
     */
    public static boolean rename(ClassEntryModel classEntry, Target target, String newName)
    {
        try
        {
            ClassFile cf = new ClassFile(new ByteArrayInputStream(classEntry.getClassFile().write()));
            MethodEntry method = methodEntry(cf, target.methodKey);
            CodeAttribute code = method != null ? method.getCodeAttribute() : null;
            if (code == null)
            {
                return false;
            }
            LocalVariableTableAttribute lvt = localVariableTable(code);
            if (lvt == null)
            {
                lvt = new ClassDecompiler(cf).localVariableTableFor(method);
                if (lvt == null)
                {
                    return false;
                }
                code.getAttributes().add(lvt);
            }
            ConstPool cp = cf.getConstPool();
            List<LocalVariableTableEntry> rebuilt = new ArrayList<>();
            for (LocalVariableTableEntry e : lvt.getLocalVariableTable())
            {
                if (isTargetEntry(e, target, cf))
                {
                    rebuilt.add(LvtSupport.entry(cp, e.getIndex(), newName, utf8(cf, e.getDescriptorIndex()), e.getStartPc(), e.getLengthPc()));
                }
                else
                {
                    rebuilt.add(e);
                }
            }
            lvt.setLocalVariableTable(rebuilt);
            lvt.updateLength();
            code.updateLength();
            classEntry.updateClassFile(cf);
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private static LocalVariableTableEntry bestEntry(LocalVariableTableAttribute lvt, ClassFile cf, String word, int offset)
    {
        LocalVariableTableEntry any = null;
        for (LocalVariableTableEntry e : lvt.getLocalVariableTable())
        {
            if (!word.equals(utf8(cf, e.getNameIndex())))
            {
                continue;
            }
            any = e;
            if (offset >= 0 && offset >= e.getStartPc() && offset < e.getStartPc() + e.getLengthPc())
            {
                return e;
            }
        }
        return any;
    }

    private static LocalVariableTableAttribute localVariableTable(CodeAttribute code)
    {
        for (Attribute a : code.getAttributes())
        {
            if (a instanceof LocalVariableTableAttribute)
            {
                return (LocalVariableTableAttribute) a;
            }
        }
        return null;
    }

    private static MethodEntry methodEntry(ClassEntryModel classEntry, String methodKey)
    {
        int paren = methodKey.indexOf('(');
        if (paren <= 0)
        {
            return null;
        }
        MethodEntryModel me = classEntry.getMethod(methodKey.substring(0, paren), methodKey.substring(paren));
        return me != null ? me.getMethodEntry() : null;
    }

    private static MethodEntry methodEntry(ClassFile cf, String methodKey)
    {
        int paren = methodKey.indexOf('(');
        if (paren <= 0)
        {
            return null;
        }
        String name = methodKey.substring(0, paren);
        String desc = methodKey.substring(paren);
        for (MethodEntry m : cf.getMethods())
        {
            if (m.getName().equals(name) && m.getDesc().equals(desc))
            {
                return m;
            }
        }
        return null;
    }

    private static LineLoc lineLoc(ClassEntryModel classEntry, int line)
    {
        Map<String, NavigableMap<Integer, Integer>> maps = classEntry.getSourceLineMaps();
        if (maps == null)
        {
            return null;
        }
        String bestKey = null;
        int bestOffset = Integer.MAX_VALUE;
        for (Map.Entry<String, NavigableMap<Integer, Integer>> m : maps.entrySet())
        {
            for (Map.Entry<Integer, Integer> e : m.getValue().entrySet())
            {
                if (e.getValue() == line && e.getKey() < bestOffset)
                {
                    bestOffset = e.getKey();
                    bestKey = m.getKey();
                }
            }
        }
        return bestKey == null ? null : new LineLoc(bestKey, bestOffset);
    }

    private static String utf8(ClassFile cf, int index)
    {
        Object item = cf.getConstPool().getItem(index);
        return item instanceof Utf8Item ? ((Utf8Item) item).getValue() : null;
    }

    private static final class LineLoc
    {
        final String methodKey;
        final int offset;

        LineLoc(String methodKey, int offset)
        {
            this.methodKey = methodKey;
            this.offset = offset;
        }
    }
}
