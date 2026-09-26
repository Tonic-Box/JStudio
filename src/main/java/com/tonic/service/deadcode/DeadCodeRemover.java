package com.tonic.service.deadcode;

import com.tonic.analysis.common.MethodReference;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.InstructionFactory;
import com.tonic.analysis.instruction.PutFieldInstruction;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Removes selected dead classes, methods and fields from a project, first patching stores to write-only fields into same-size pops. */
public final class DeadCodeRemover
{

    private static final int OP_PUTSTATIC = 0xB3;
    private static final int OP_PUTFIELD = 0xB5;
    private static final byte POP = 0x57;
    private static final byte POP2 = 0x58;
    private static final byte NOP = 0x00;

    private DeadCodeRemover()
    {
    }

    /** Outcome of an apply: counts plus the classes mutated (caches to invalidate) and fully removed. */
    @Getter
    public static final class Result
    {
        private final int classesRemoved;
        private final int methodsRemoved;
        private final int fieldsRemoved;
        @Getter(AccessLevel.NONE)
        private final Set<String> touchedClasses;
        /**
         * -- GETTER --
         * Internal names of classes removed entirely.
         */
        private final Set<String> removedClasses;

        Result(int classesRemoved, int methodsRemoved, int fieldsRemoved, Set<String> touchedClasses, Set<String> removedClasses)
        {
            this.classesRemoved = classesRemoved;
            this.methodsRemoved = methodsRemoved;
            this.fieldsRemoved = fieldsRemoved;
            this.touchedClasses = touchedClasses;
            this.removedClasses = removedClasses;
        }

    }

    /**
     * Removes the given items: patches writers of write-only fields, drops methods and fields, then whole classes, and invalidates decompilation of touched classes.
     *
     * @param project the project to modify
     * @param items the dead items to remove
     * @return the removal counts and the touched and removed classes
     */
    public static Result apply(ProjectModel project, List<DeadItem> items)
    {
        Set<String> touched = new LinkedHashSet<>();

        for (DeadItem item : items)
        {
            if (item.getKind() == DeadItem.Kind.FIELD && item.isWriteOnly())
            {
                for (MethodReference writer : item.getWriters())
                {
                    ClassFile cf = classFile(project, writer.getOwner());
                    if (cf != null && patchWriter(cf, writer, item.getOwner(), item.getName(), item.getDesc()))
                    {
                        touched.add(writer.getOwner());
                    }
                }
            }
        }

        int methods = 0;
        int fields = 0;
        for (DeadItem item : items)
        {
            ClassFile cf = classFile(project, item.getOwner());
            if (cf == null)
            {
                continue;
            }
            if (item.getKind() == DeadItem.Kind.METHOD && cf.removeMethod(item.getName(), item.getDesc()))
            {
                methods++;
                touched.add(item.getOwner());
            }
            else if (item.getKind() == DeadItem.Kind.FIELD && cf.removeField(item.getName(), item.getDesc()))
            {
                fields++;
                touched.add(item.getOwner());
            }
        }

        int classes = 0;
        Set<String> removed = new LinkedHashSet<>();
        for (DeadItem item : items)
        {
            if (item.getKind() == DeadItem.Kind.CLASS && project.removeClass(item.getOwner()))
            {
                classes++;
                removed.add(item.getOwner());
            }
        }

        touched.removeAll(removed);
        for (String owner : touched)
        {
            ClassEntryModel entry = project.getClass(owner);
            if (entry != null)
            {
                entry.invalidateDecompilationCache();
            }
        }
        return new Result(classes, methods, fields, touched, removed);
    }

    private static boolean patchWriter(ClassFile cf, MethodReference writer, String fOwner, String fName, String fDesc)
    {
        MethodEntry method = findMethod(cf, writer.getName(), writer.getDescriptor());
        if (method == null || method.getCodeAttribute() == null)
        {
            return false;
        }
        CodeAttribute code = method.getCodeAttribute();
        byte[] bytes = code.getCode();
        List<Instruction> instructions = InstructionFactory.parse(bytes, cf.getConstPool());
        boolean cat2 = fDesc.equals("J") || fDesc.equals("D");
        boolean changed = false;
        for (Instruction ins : instructions)
        {
            if (!(ins instanceof PutFieldInstruction))
            {
                continue;
            }
            PutFieldInstruction put = (PutFieldInstruction) ins;
            if (!fOwner.equals(put.getOwnerClass()) || !fName.equals(put.getFieldName())
                    || !fDesc.equals(put.getFieldDescriptor()) || ins.getLength() != 3)
            {
                continue;
            }
            int off = ins.getOffset();
            if (put.getOpcode() == OP_PUTSTATIC)
            {
                bytes[off] = cat2 ? POP2 : POP;
                bytes[off + 1] = NOP;
                bytes[off + 2] = NOP;
            }
            else if (put.getOpcode() == OP_PUTFIELD)
            {
                bytes[off] = cat2 ? POP2 : POP;
                bytes[off + 1] = POP;
                bytes[off + 2] = NOP;
            }
            else
            {
                continue;
            }
            changed = true;
        }
        if (changed)
        {
            code.setCode(bytes);
        }
        return changed;
    }

    private static MethodEntry findMethod(ClassFile cf, String name, String desc)
    {
        for (MethodEntry m : cf.getMethods())
        {
            if (m.getName().equals(name) && m.getDesc().equals(desc))
            {
                return m;
            }
        }
        return null;
    }

    private static ClassFile classFile(ProjectModel project, String internalName)
    {
        ClassEntryModel entry = project.getClass(internalName);
        return entry != null ? entry.getClassFile() : null;
    }
}
