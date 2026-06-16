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
import lombok.Getter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies a selected subset of a {@link DeadCodeReport} to the project: removes dead classes
 * ({@link ProjectModel#removeClass}), dead methods/fields ({@link ClassFile#removeMethod}/{@code removeField}),
 * and - for write-only fields - first rewrites each writer's store into pop(s) so the field can be dropped
 * safely.
 *
 * <p>The write-only rewrite is a same-size in-place byte patch: {@code putstatic}/{@code putfield} are 3 bytes
 * and have identical net stack effect to {@code pop}/{@code pop2} (+ a second {@code pop} for the object ref on
 * {@code putfield}) padded with {@code nop}, so offsets, branches, frames, and maxStack are untouched.
 */
public final class DeadCodeRemover {

    private static final int OP_PUTSTATIC = 0xB3;
    private static final int OP_PUTFIELD = 0xB5;
    private static final byte POP = 0x57;
    private static final byte POP2 = 0x58;
    private static final byte NOP = 0x00;

    private DeadCodeRemover() {
    }

    /** Outcome of an apply: counts plus the classes mutated (caches to invalidate) and fully removed. */
    @Getter
    public static final class Result {
        private final int classesRemoved;
        private final int methodsRemoved;
        private final int fieldsRemoved;
        /**
         * -- GETTER --
         * Internal names of classes that were mutated (members removed) but not deleted - invalidate their caches.
         */
        private final Set<String> touchedClasses;
        /**
         * -- GETTER --
         * Internal names of classes removed entirely.
         */
        private final Set<String> removedClasses;

        Result(int classesRemoved, int methodsRemoved, int fieldsRemoved,
               Set<String> touchedClasses, Set<String> removedClasses) {
            this.classesRemoved = classesRemoved;
            this.methodsRemoved = methodsRemoved;
            this.fieldsRemoved = fieldsRemoved;
            this.touchedClasses = touchedClasses;
            this.removedClasses = removedClasses;
        }

    }

    public static Result apply(ProjectModel project, List<DeadItem> items) {
        Set<String> touched = new LinkedHashSet<>();

        // 1. Patch write-only fields' writers before the fields are removed.
        for (DeadItem item : items) {
            if (item.getKind() == DeadItem.Kind.FIELD && item.isWriteOnly()) {
                for (MethodReference writer : item.getWriters()) {
                    ClassFile cf = classFile(project, writer.getOwner());
                    if (cf != null && patchWriter(cf, writer, item.getOwner(), item.getName(), item.getDesc())) {
                        touched.add(writer.getOwner());
                    }
                }
            }
        }

        // 2. Remove dead methods and fields.
        int methods = 0;
        int fields = 0;
        for (DeadItem item : items) {
            ClassFile cf = classFile(project, item.getOwner());
            if (cf == null) {
                continue;
            }
            if (item.getKind() == DeadItem.Kind.METHOD && cf.removeMethod(item.getName(), item.getDesc())) {
                methods++;
                touched.add(item.getOwner());
            } else if (item.getKind() == DeadItem.Kind.FIELD && cf.removeField(item.getName(), item.getDesc())) {
                fields++;
                touched.add(item.getOwner());
            }
        }

        // 3. Remove whole dead classes (rebuilds the pool + fires ProjectUpdatedEvent itself).
        int classes = 0;
        Set<String> removed = new LinkedHashSet<>();
        for (DeadItem item : items) {
            if (item.getKind() == DeadItem.Kind.CLASS && project.removeClass(item.getOwner())) {
                classes++;
                removed.add(item.getOwner());
            }
        }

        // 4. Invalidate decompilation caches for mutated (still-present) classes.
        touched.removeAll(removed);
        for (String owner : touched) {
            ClassEntryModel entry = project.getClass(owner);
            if (entry != null) {
                entry.invalidateDecompilationCache();
            }
        }
        return new Result(classes, methods, fields, touched, removed);
    }

    /** Rewrites every store of {@code (fOwner,fName,fDesc)} in {@code writer} into pop(s); returns whether it changed. */
    private static boolean patchWriter(ClassFile cf, MethodReference writer, String fOwner, String fName, String fDesc) {
        MethodEntry method = findMethod(cf, writer.getName(), writer.getDescriptor());
        if (method == null || method.getCodeAttribute() == null) {
            return false;
        }
        CodeAttribute code = method.getCodeAttribute();
        byte[] bytes = code.getCode();
        List<Instruction> instructions = InstructionFactory.parse(bytes, cf.getConstPool());
        boolean cat2 = fDesc.equals("J") || fDesc.equals("D");
        boolean changed = false;
        for (Instruction ins : instructions) {
            if (!(ins instanceof PutFieldInstruction)) {
                continue;
            }
            PutFieldInstruction put = (PutFieldInstruction) ins;
            if (!fOwner.equals(put.getOwnerClass()) || !fName.equals(put.getFieldName())
                    || !fDesc.equals(put.getFieldDescriptor()) || ins.getLength() != 3) {
                continue;
            }
            int off = ins.getOffset();
            if (put.getOpcode() == OP_PUTSTATIC) {
                bytes[off] = cat2 ? POP2 : POP;
                bytes[off + 1] = NOP;
                bytes[off + 2] = NOP;
            } else if (put.getOpcode() == OP_PUTFIELD) {
                bytes[off] = cat2 ? POP2 : POP;
                bytes[off + 1] = POP;
                bytes[off + 2] = NOP;
            } else {
                continue;
            }
            changed = true;
        }
        if (changed) {
            code.setCode(bytes);
        }
        return changed;
    }

    private static MethodEntry findMethod(ClassFile cf, String name, String desc) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name) && m.getDesc().equals(desc)) {
                return m;
            }
        }
        return null;
    }

    private static ClassFile classFile(ProjectModel project, String internalName) {
        ClassEntryModel entry = project.getClass(internalName);
        return entry != null ? entry.getClassFile() : null;
    }
}
