package com.tonic.ui.vm.heap.model;

public class AllocationEvent {
    public enum AllocationType {
        NEW(0xBB),
        NEWARRAY(0xBC),
        ANEWARRAY(0xBD),
        MULTIANEWARRAY(0xC5);

        private final int opcode;

        AllocationType(int opcode) {
            this.opcode = opcode;
        }

        public int getOpcode() {
            return opcode;
        }

        public static AllocationType fromOpcode(int opcode) {
            for (AllocationType type : values()) {
                if (type.opcode == opcode) {
                    return type;
                }
            }
            return NEW;
        }
    }

    private final int objectId;
    private final String className;
    private final AllocationType allocationType;
    private final int opcode;
    private final long instructionCount;
    private final ProvenanceInfo provenance;
    private final int arrayLength;
    private final int[] arrayDimensions;

    private AllocationEvent(Builder builder) {
        this.objectId = builder.objectId;
        this.className = builder.className;
        this.allocationType = builder.allocationType;
        this.opcode = builder.opcode;
        this.instructionCount = builder.instructionCount;
        this.provenance = builder.provenance;
        this.arrayLength = builder.arrayLength;
        this.arrayDimensions = builder.arrayDimensions;
    }

    public int getObjectId() {
        return objectId;
    }

    public String getClassName() {
        return className;
    }

    public AllocationType getAllocationType() {
        return allocationType;
    }

    public int getOpcode() {
        return opcode;
    }

    public long getInstructionCount() {
        return instructionCount;
    }

    public ProvenanceInfo getProvenance() {
        return provenance;
    }

    public int getArrayLength() {
        return arrayLength;
    }

    public int[] getArrayDimensions() {
        return arrayDimensions;
    }

    public boolean isArray() {
        return allocationType != AllocationType.NEW;
    }

    public String getShortDescription() {
        if (isArray()) {
            return className + "[" + arrayLength + "]";
        }
        return className;
    }

    @Override
    public String toString() {
        return "AllocationEvent{" +
            "objectId=" + objectId +
            ", className='" + className + '\'' +
            ", type=" + allocationType +
            ", at=" + instructionCount +
            ", provenance=" + (provenance != null ? provenance.getShortLocation() : "unknown") +
            '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int objectId;
        private String className = "";
        private AllocationType allocationType = AllocationType.NEW;
        private int opcode;
        private long instructionCount;
        private ProvenanceInfo provenance;
        private int arrayLength = -1;
        private int[] arrayDimensions;

        public Builder objectId(int objectId) {
            this.objectId = objectId;
            return this;
        }

        public Builder className(String className) {
            this.className = className;
            return this;
        }

        public Builder allocationType(AllocationType allocationType) {
            this.allocationType = allocationType;
            return this;
        }

        public Builder opcode(int opcode) {
            this.opcode = opcode;
            this.allocationType = AllocationType.fromOpcode(opcode);
            return this;
        }

        public Builder instructionCount(long instructionCount) {
            this.instructionCount = instructionCount;
            return this;
        }

        public Builder provenance(ProvenanceInfo provenance) {
            this.provenance = provenance;
            return this;
        }

        public Builder arrayLength(int arrayLength) {
            this.arrayLength = arrayLength;
            return this;
        }

        public Builder arrayDimensions(int[] arrayDimensions) {
            this.arrayDimensions = arrayDimensions;
            return this;
        }

        public AllocationEvent build() {
            return new AllocationEvent(this);
        }
    }
}
