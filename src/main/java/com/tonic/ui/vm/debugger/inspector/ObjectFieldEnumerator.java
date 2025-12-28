package com.tonic.ui.vm.debugger.inspector;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ValueTag;
import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;

import java.util.ArrayList;
import java.util.List;

public class ObjectFieldEnumerator {

    private static final int ACC_STATIC = 0x0008;
    private static final int ACC_FINAL = 0x0010;
    private static final int ACC_SYNTHETIC = 0x1000;

    private final ClassResolver classResolver;
    private final boolean includeSynthetic;
    private final boolean includeStatic;

    public ObjectFieldEnumerator(ClassResolver classResolver) {
        this(classResolver, false, false);
    }

    public ObjectFieldEnumerator(ClassResolver classResolver, boolean includeSynthetic, boolean includeStatic) {
        this.classResolver = classResolver;
        this.includeSynthetic = includeSynthetic;
        this.includeStatic = includeStatic;
    }

    public List<FieldInfo> enumerate(ObjectInstance obj) {
        List<FieldInfo> fields = new ArrayList<>();

        if (obj == null) {
            return fields;
        }

        String className = obj.getClassName();
        enumerateHierarchy(obj, className, fields);

        return fields;
    }

    private void enumerateHierarchy(ObjectInstance obj, String className, List<FieldInfo> fields) {
        if (className == null || className.equals("java/lang/Object")) {
            return;
        }

        try {
            ClassFile classFile = classResolver.resolveClass(className);
            if (classFile != null) {
                for (FieldEntry field : classFile.getFields()) {
                    int access = field.getAccess();

                    boolean isStatic = (access & ACC_STATIC) != 0;
                    if (isStatic && !includeStatic) {
                        continue;
                    }

                    boolean isSynthetic = (access & ACC_SYNTHETIC) != 0;
                    if (isSynthetic && !includeSynthetic) {
                        continue;
                    }

                    if (isSyntheticName(field.getName()) && !includeSynthetic) {
                        continue;
                    }

                    boolean isFinal = (access & ACC_FINAL) != 0;
                    String name = field.getName();
                    String descriptor = field.getDesc();

                    Object value = obj.getField(className, name, descriptor);
                    ValueTag tag = descriptorToValueTag(descriptor, value);

                    fields.add(new FieldInfo(
                        name,
                        descriptor,
                        className,
                        value,
                        tag,
                        isFinal,
                        isStatic
                    ));
                }

                String superClass = classFile.getSuperClassName();
                if (superClass != null && !superClass.equals("java/lang/Object")) {
                    enumerateHierarchy(obj, superClass, fields);
                }
            }
        } catch (Exception e) {
            System.err.println("[ObjectFieldEnumerator] Error enumerating " + className + ": " + e.getMessage());
        }
    }

    private boolean isSyntheticName(String name) {
        return name.startsWith("this$") ||
               name.startsWith("val$") ||
               name.startsWith("access$") ||
               name.contains("$assertionsDisabled");
    }

    private ValueTag descriptorToValueTag(String descriptor, Object value) {
        if (descriptor == null || descriptor.isEmpty()) {
            return null;
        }

        switch (descriptor.charAt(0)) {
            case 'I':
            case 'Z':
            case 'B':
            case 'C':
            case 'S':
                return ValueTag.INT;
            case 'J':
                return ValueTag.LONG;
            case 'F':
                return ValueTag.FLOAT;
            case 'D':
                return ValueTag.DOUBLE;
            case 'L':
            case '[':
                if (value == null) {
                    return ValueTag.NULL;
                }
                return ValueTag.REFERENCE;
            default:
                return null;
        }
    }
}
