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

/** Builds class bytes for a live debugger whose LocalVariableTables name locals across the whole method, recovering a table where none exists. */
public final class SyntheticLvtInjector
{

    private SyntheticLvtInjector()
    {
    }

    /**
     * Rebuilds a class from a fresh parse with each method's LVT recovered if missing and each single-typed slot widened to the whole method; the given class is not modified.
     *
     * @param original the class
     * @return the rebuilt bytes, or null when the class is null, nothing changed, or rebuilding fails
     */
    public static byte[] augment(ClassFile original)
    {
        if (original == null)
        {
            return null;
        }
        try
        {
            ClassFile cf = new ClassFile(new ByteArrayInputStream(original.write()));
            ClassDecompiler decompiler = new ClassDecompiler(cf);
            ConstPool constPool = cf.getConstPool();
            boolean changed = false;
            for (MethodEntry method : cf.getMethods())
            {
                CodeAttribute code = method.getCodeAttribute();
                if (code == null || code.getCode() == null || code.getCode().length == 0)
                {
                    continue;
                }
                int codeLen = code.getCode().length;

                LocalVariableTableAttribute lvt = findLvt(code);
                boolean recovered = false;
                if (lvt == null)
                {
                    lvt = decompiler.localVariableTableFor(method);
                    if (lvt == null)
                    {
                        continue;
                    }
                    recovered = true;
                }

                List<LocalVariableTableEntry> current = lvt.getLocalVariableTable();
                List<LocalVariableTableEntry> widened = widen(current, constPool, codeLen);
                if (!recovered && !differs(current, widened))
                {
                    continue;
                }
                current.clear();
                current.addAll(widened);
                lvt.updateLength();
                if (recovered)
                {
                    code.getAttributes().add(lvt);
                }
                changed = true;
            }
            return changed ? cf.write() : null;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static LocalVariableTableAttribute findLvt(CodeAttribute code)
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

    private static List<LocalVariableTableEntry> widen(List<LocalVariableTableEntry> entries, ConstPool constPool, int codeLen)
    {
        Map<Integer, List<LocalVariableTableEntry>> bySlot = new LinkedHashMap<>();
        for (LocalVariableTableEntry e : entries)
        {
            bySlot.computeIfAbsent(e.getIndex(), k -> new ArrayList<>()).add(e);
        }
        List<LocalVariableTableEntry> out = new ArrayList<>();
        for (List<LocalVariableTableEntry> group : bySlot.values())
        {
            LocalVariableTableEntry first = group.get(0);
            boolean sameType = true;
            for (LocalVariableTableEntry e : group)
            {
                if (e.getDescriptorIndex() != first.getDescriptorIndex())
                {
                    sameType = false;
                    break;
                }
            }
            if (sameType)
            {
                out.add(new LocalVariableTableEntry(constPool, 0, codeLen, first.getNameIndex(), first.getDescriptorIndex(), first.getIndex()));
            }
            else
            {
                out.addAll(group);
            }
        }
        return out;
    }

    private static boolean differs(List<LocalVariableTableEntry> current, List<LocalVariableTableEntry> widened)
    {
        if (current.size() != widened.size())
        {
            return true;
        }
        for (int i = 0; i < current.size(); i++)
        {
            LocalVariableTableEntry a = current.get(i);
            LocalVariableTableEntry b = widened.get(i);
            if (a.getStartPc() != b.getStartPc() || a.getLengthPc() != b.getLengthPc()
                    || a.getNameIndex() != b.getNameIndex() || a.getDescriptorIndex() != b.getDescriptorIndex()
                    || a.getIndex() != b.getIndex())
            {
                return true;
            }
        }
        return false;
    }
}
