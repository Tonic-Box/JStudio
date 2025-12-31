package com.tonic.ui.vm.heap.model;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import lombok.Getter;

@Getter
public class FieldValue {
    private final String owner;
    private final String name;
    private final String descriptor;
    private final Object value;
    private final boolean reference;
    private final int referenceId;

    public FieldValue(String owner, String name, String descriptor, Object value) {
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        this.value = value;
        this.reference = isReferenceDescriptor(descriptor);
        this.referenceId = extractReferenceId(value);
    }

    private boolean isReferenceDescriptor(String desc) {
        return desc != null && (desc.startsWith("L") || desc.startsWith("["));
    }

    private int extractReferenceId(Object val) {
        if (val == null) {
            return -1;
        }
        if (val instanceof com.tonic.analysis.execution.heap.ObjectInstance) {
            return ((com.tonic.analysis.execution.heap.ObjectInstance) val).getId();
        }
        return -1;
    }

    public boolean isNull() {
        return value == null;
    }

    public boolean hasReferenceId() {
        return referenceId >= 0;
    }

    public String getTypeName() {
        return descriptorToTypeName(descriptor);
    }

    public String getDisplayValue() {
        if (value == null) {
            return "null";
        }
        if (value instanceof ArrayInstance) {
            ArrayInstance arr =
                    (ArrayInstance) value;
            return arr.getComponentType() + "[" + arr.getLength() + "] #" + arr.getId();
        }
        if (value instanceof ObjectInstance) {
            ObjectInstance obj =
                (ObjectInstance) value;
            return obj.getClassName() + " #" + obj.getId();
        }
        return String.valueOf(value);
    }

    private String descriptorToTypeName(String desc) {
        if (desc == null || desc.isEmpty()) {
            return "unknown";
        }
        switch (desc.charAt(0)) {
            case 'B': return "byte";
            case 'C': return "char";
            case 'D': return "double";
            case 'F': return "float";
            case 'I': return "int";
            case 'J': return "long";
            case 'S': return "short";
            case 'Z': return "boolean";
            case 'V': return "void";
            case '[':
                return descriptorToTypeName(desc.substring(1)) + "[]";
            case 'L':
                int end = desc.indexOf(';');
                if (end > 0) {
                    return desc.substring(1, end).replace('/', '.');
                }
                return desc;
            default:
                return desc;
        }
    }

    public String getKey() {
        return owner + "." + name + ":" + descriptor;
    }

    @Override
    public String toString() {
        return name + ": " + getDisplayValue();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String owner = "";
        private String name = "";
        private String descriptor = "";
        private Object value;

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder descriptor(String descriptor) {
            this.descriptor = descriptor;
            return this;
        }

        public Builder value(Object value) {
            this.value = value;
            return this;
        }

        public FieldValue build() {
            return new FieldValue(owner, name, descriptor, value);
        }
    }
}
