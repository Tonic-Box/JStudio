package com.tonic.ui.vm.heap.model;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import lombok.Getter;

@Getter
public class MutationEvent {
    @Getter
    public enum MutationType {
        PUTFIELD(0xB5),
        PUTSTATIC(0xB3);

        private final int opcode;

        MutationType(int opcode) {
            this.opcode = opcode;
        }

        public static MutationType fromOpcode(int opcode) {
            return opcode == 0xB3 ? PUTSTATIC : PUTFIELD;
        }
    }

    private final int objectId;
    private final String fieldOwner;
    private final String fieldName;
    private final String fieldDescriptor;
    private final Object oldValue;
    private final Object newValue;
    private final long instructionCount;
    private final MutationType mutationType;
    private final ProvenanceInfo provenance;

    private MutationEvent(Builder builder) {
        this.objectId = builder.objectId;
        this.fieldOwner = builder.fieldOwner;
        this.fieldName = builder.fieldName;
        this.fieldDescriptor = builder.fieldDescriptor;
        this.oldValue = builder.oldValue;
        this.newValue = builder.newValue;
        this.instructionCount = builder.instructionCount;
        this.mutationType = builder.mutationType;
        this.provenance = builder.provenance;
    }

    public boolean isStatic() {
        return mutationType == MutationType.PUTSTATIC;
    }

    public String getFieldKey() {
        return fieldOwner + "." + fieldName + ":" + fieldDescriptor;
    }

    public String getDisplayOldValue() {
        return formatValue(oldValue);
    }

    public String getDisplayNewValue() {
        return formatValue(newValue);
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof ArrayInstance) {
            com.tonic.analysis.execution.heap.ArrayInstance arr =
                    (com.tonic.analysis.execution.heap.ArrayInstance) value;
            return arr.getComponentType() + "[" + arr.getLength() + "] #" + arr.getId();
        }
        if (value instanceof ObjectInstance) {
            ObjectInstance obj = (ObjectInstance) value;
            return obj.getClassName() + " #" + obj.getId();
        }
        return String.valueOf(value);
    }

    @Override
    public String toString() {
        return "MutationEvent{" +
            (isStatic() ? "static " : "#" + objectId + ".") +
            fieldName + " = " + getDisplayOldValue() + " -> " + getDisplayNewValue() +
            " @ " + instructionCount +
            '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int objectId = -1;
        private String fieldOwner = "";
        private String fieldName = "";
        private String fieldDescriptor = "";
        private Object oldValue;
        private Object newValue;
        private long instructionCount;
        private MutationType mutationType = MutationType.PUTFIELD;
        private ProvenanceInfo provenance;

        public Builder objectId(int objectId) {
            this.objectId = objectId;
            return this;
        }

        public Builder fieldOwner(String fieldOwner) {
            this.fieldOwner = fieldOwner;
            return this;
        }

        public Builder fieldName(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public Builder fieldDescriptor(String fieldDescriptor) {
            this.fieldDescriptor = fieldDescriptor;
            return this;
        }

        public Builder oldValue(Object oldValue) {
            this.oldValue = oldValue;
            return this;
        }

        public Builder newValue(Object newValue) {
            this.newValue = newValue;
            return this;
        }

        public Builder instructionCount(long instructionCount) {
            this.instructionCount = instructionCount;
            return this;
        }

        public Builder mutationType(MutationType mutationType) {
            this.mutationType = mutationType;
            return this;
        }

        public Builder opcode(int opcode) {
            this.mutationType = MutationType.fromOpcode(opcode);
            return this;
        }

        public Builder provenance(ProvenanceInfo provenance) {
            this.provenance = provenance;
            return this;
        }

        public MutationEvent build() {
            return new MutationEvent(this);
        }
    }
}
