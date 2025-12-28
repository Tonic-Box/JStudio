package com.tonic.ui.vm.debugger.inspector;

import com.tonic.analysis.execution.state.ValueTag;

public class FieldInfo {

    private final String name;
    private final String descriptor;
    private final String ownerClass;
    private final Object value;
    private final ValueTag valueTag;
    private final boolean isFinal;
    private final boolean isStatic;

    public FieldInfo(String name, String descriptor, String ownerClass,
                     Object value, ValueTag valueTag, boolean isFinal, boolean isStatic) {
        this.name = name;
        this.descriptor = descriptor;
        this.ownerClass = ownerClass;
        this.value = value;
        this.valueTag = valueTag;
        this.isFinal = isFinal;
        this.isStatic = isStatic;
    }

    public String getName() {
        return name;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public String getOwnerClass() {
        return ownerClass;
    }

    public Object getValue() {
        return value;
    }

    public ValueTag getValueTag() {
        return valueTag;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public boolean isEditable() {
        return !isFinal && valueTag != null;
    }

    public String getTypeName() {
        return descriptorToTypeName(descriptor);
    }

    public String getValueString() {
        if (value == null) {
            return "null";
        }
        return value.toString();
    }

    private static String descriptorToTypeName(String desc) {
        if (desc == null || desc.isEmpty()) {
            return "unknown";
        }

        switch (desc.charAt(0)) {
            case 'I': return "int";
            case 'J': return "long";
            case 'F': return "float";
            case 'D': return "double";
            case 'Z': return "boolean";
            case 'B': return "byte";
            case 'C': return "char";
            case 'S': return "short";
            case 'V': return "void";
            case '[':
                return descriptorToTypeName(desc.substring(1)) + "[]";
            case 'L':
                int end = desc.indexOf(';');
                if (end > 1) {
                    String className = desc.substring(1, end);
                    int lastSlash = className.lastIndexOf('/');
                    return lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
                }
                return desc;
            default:
                return desc;
        }
    }
}
