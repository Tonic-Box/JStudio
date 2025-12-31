package com.tonic.ui.vm.heap.model;

import com.tonic.analysis.execution.heap.ArrayInstance;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class HeapArray extends HeapObject {
    private final String componentType;
    private final int length;
    private final boolean primitive;
    private final Object[] elements;

    private HeapArray(Builder builder) {
        super(builder);
        this.componentType = builder.componentType;
        this.length = builder.length;
        this.primitive = detectPrimitive(builder.componentType);
        this.elements = builder.elements;
    }

    private boolean detectPrimitive(String type) {
        if (type == null || type.isEmpty()) return false;
        char c = type.charAt(0);
        return c == 'B' || c == 'C' || c == 'D' || c == 'F' ||
               c == 'I' || c == 'J' || c == 'S' || c == 'Z';
    }

    public Object getElement(int index) {
        if (elements != null && index >= 0 && index < elements.length) {
            return elements[index];
        }
        return null;
    }

    public byte[] asByteArray() {
        if (!"B".equals(componentType) || elements == null) {
            return null;
        }
        byte[] bytes = new byte[elements.length];
        for (int i = 0; i < elements.length; i++) {
            if (elements[i] instanceof Number) {
                bytes[i] = ((Number) elements[i]).byteValue();
            }
        }
        return bytes;
    }

    public char[] asCharArray() {
        if (!"C".equals(componentType) || elements == null) {
            return null;
        }
        char[] chars = new char[elements.length];
        for (int i = 0; i < elements.length; i++) {
            if (elements[i] instanceof Character) {
                chars[i] = (Character) elements[i];
            } else if (elements[i] instanceof Number) {
                chars[i] = (char) ((Number) elements[i]).intValue();
            }
        }
        return chars;
    }

    public String asString() {
        char[] chars = asCharArray();
        return chars != null ? new String(chars) : null;
    }

    public String getComponentTypeName() {
        if (componentType == null || componentType.isEmpty()) return "?";
        switch (componentType.charAt(0)) {
            case 'B': return "byte";
            case 'C': return "char";
            case 'D': return "double";
            case 'F': return "float";
            case 'I': return "int";
            case 'J': return "long";
            case 'S': return "short";
            case 'Z': return "boolean";
            case 'L':
                int end = componentType.indexOf(';');
                if (end > 0) {
                    String className = componentType.substring(1, end);
                    int lastSlash = className.lastIndexOf('/');
                    return lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
                }
                return componentType;
            case '[':
                return getArrayTypeName(componentType);
            default:
                return componentType;
        }
    }

    private String getArrayTypeName(String desc) {
        int dims = 0;
        while (dims < desc.length() && desc.charAt(dims) == '[') {
            dims++;
        }
        String elementDesc = desc.substring(dims);
        String elementName;
        if (elementDesc.startsWith("L") && elementDesc.endsWith(";")) {
            String className = elementDesc.substring(1, elementDesc.length() - 1);
            int lastSlash = className.lastIndexOf('/');
            elementName = lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
        } else {
            elementName = getComponentTypeName();
        }
        return elementName + "[]".repeat(dims);
    }

    public static HeapArray fromArrayInstance(ArrayInstance instance, long allocationTime,
                                               ProvenanceInfo provenance,
                                               List<MutationEvent> mutations) {
        Object[] elements = new Object[instance.getLength()];
        String compType = instance.getComponentType();

        for (int i = 0; i < instance.getLength(); i++) {
            elements[i] = getArrayElement(instance, compType, i);
        }

        return builder()
            .id(instance.getId())
            .className(compType + "[]")
            .allocationTime(allocationTime)
            .provenance(provenance)
            .mutations(mutations != null ? mutations : new ArrayList<>())
            .isArray(true)
            .componentType(compType)
            .length(instance.getLength())
            .elements(elements)
            .build();
    }

    private static Object getArrayElement(ArrayInstance arr, String compType, int index) {
        try {
            if (compType == null || compType.isEmpty()) return null;
            switch (compType.charAt(0)) {
                case 'B': return arr.getByte(index);
                case 'C': return arr.getChar(index);
                case 'D': return arr.getDouble(index);
                case 'F': return arr.getFloat(index);
                case 'I': return arr.getInt(index);
                case 'J': return arr.getLong(index);
                case 'S': return arr.getShort(index);
                case 'Z': return arr.getBoolean(index);
                default:
                    return arr.get(index);
            }
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return getComponentTypeName() + "[" + length + "] #" + getId();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends HeapObject.Builder {
        private String componentType = "";
        private int length;
        private Object[] elements;

        public Builder componentType(String componentType) {
            this.componentType = componentType;
            return this;
        }

        public Builder length(int length) {
            this.length = length;
            return this;
        }

        public Builder elements(Object[] elements) {
            this.elements = elements;
            return this;
        }

        @Override
        public Builder id(int id) {
            super.id(id);
            return this;
        }

        @Override
        public Builder className(String className) {
            super.className(className);
            return this;
        }

        @Override
        public Builder allocationTime(long allocationTime) {
            super.allocationTime(allocationTime);
            return this;
        }

        @Override
        public Builder provenance(ProvenanceInfo provenance) {
            super.provenance(provenance);
            return this;
        }

        @Override
        public Builder mutations(List<MutationEvent> mutations) {
            super.mutations(mutations);
            return this;
        }

        @Override
        public Builder isArray(boolean isArray) {
            super.isArray(isArray);
            return this;
        }

        @Override
        public HeapArray build() {
            return new HeapArray(this);
        }
    }
}
