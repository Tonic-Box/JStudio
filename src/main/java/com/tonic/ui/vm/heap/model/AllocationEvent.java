package com.tonic.ui.vm.heap.model;

import lombok.Getter;

/** One object or array allocation seen during heap forensics: what was allocated, by which opcode, when and where. */
@Getter
public class AllocationEvent
{
    /** The bytecode instruction that performed an allocation. */
    @Getter
    public enum AllocationType
    {
        NEW(0xBB),
        NEWARRAY(0xBC),
        ANEWARRAY(0xBD),
        MULTIANEWARRAY(0xC5);

        private final int opcode;

        AllocationType(int opcode)
        {
            this.opcode = opcode;
        }

        /**
         * Maps an allocation opcode to its type.
         *
         * @param opcode the bytecode opcode
         * @return the matching type, or NEW when the opcode is not an allocation opcode
         */
        public static AllocationType fromOpcode(int opcode)
        {
            for (AllocationType type : values())
            {
                if (type.opcode == opcode)
                {
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

    private AllocationEvent(Builder builder)
    {
        this.objectId = builder.objectId;
        this.className = builder.className;
        this.allocationType = builder.allocationType;
        this.opcode = builder.opcode;
        this.instructionCount = builder.instructionCount;
        this.provenance = builder.provenance;
        this.arrayLength = builder.arrayLength;
        this.arrayDimensions = builder.arrayDimensions;
    }

    /**
     * Tells whether this allocation made an array.
     *
     * @return true for any array allocation type
     */
    public boolean isArray()
    {
        return allocationType != AllocationType.NEW;
    }

    /**
     * Describes the allocation briefly.
     *
     * @return the class name, followed by the length in brackets for arrays
     */
    public String getShortDescription()
    {
        if (isArray())
        {
            return className + "[" + arrayLength + "]";
        }
        return className;
    }

    @Override
    public String toString()
    {
        return "AllocationEvent{" +
                "objectId=" + objectId +
                ", className='" + className + '\'' +
                ", type=" + allocationType +
                ", at=" + instructionCount +
                ", provenance=" + (provenance != null ? provenance.getShortLocation() : "unknown") +
                '}';
    }

    /**
     * Starts a new event builder.
     *
     * @return an empty builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /** Builder for allocation events; the class name defaults to empty, the type to NEW and the array length to -1. */
    public static class Builder
    {
        private int objectId;
        private String className = "";
        private AllocationType allocationType = AllocationType.NEW;
        private int opcode;
        private long instructionCount;
        private ProvenanceInfo provenance;
        private int arrayLength = -1;
        private int[] arrayDimensions;

        /**
         * Sets the allocated object's id.
         *
         * @param objectId the heap id of the new object
         * @return this builder
         */
        public Builder objectId(int objectId)
        {
            this.objectId = objectId;
            return this;
        }

        /**
         * Sets the allocated class.
         *
         * @param className the allocated class or array component name
         * @return this builder
         */
        public Builder className(String className)
        {
            this.className = className;
            return this;
        }

        /**
         * Sets the allocation type.
         *
         * @param allocationType the instruction kind that allocated
         * @return this builder
         */
        public Builder allocationType(AllocationType allocationType)
        {
            this.allocationType = allocationType;
            return this;
        }

        /**
         * Sets the opcode and derives the allocation type from it.
         *
         * @param opcode the allocating bytecode opcode
         * @return this builder
         */
        public Builder opcode(int opcode)
        {
            this.opcode = opcode;
            this.allocationType = AllocationType.fromOpcode(opcode);
            return this;
        }

        /**
         * Sets when the allocation happened.
         *
         * @param instructionCount the executed instruction count at the allocation
         * @return this builder
         */
        public Builder instructionCount(long instructionCount)
        {
            this.instructionCount = instructionCount;
            return this;
        }

        /**
         * Sets where the allocation happened.
         *
         * @param provenance the allocating location, or null if unknown
         * @return this builder
         */
        public Builder provenance(ProvenanceInfo provenance)
        {
            this.provenance = provenance;
            return this;
        }

        /**
         * Sets the array length.
         *
         * @param arrayLength the length of the new array, or -1 for objects
         * @return this builder
         */
        public Builder arrayLength(int arrayLength)
        {
            this.arrayLength = arrayLength;
            return this;
        }

        /**
         * Sets the dimensions of a multi-dimensional array allocation.
         *
         * @param arrayDimensions the length of each allocated dimension
         * @return this builder
         */
        public Builder arrayDimensions(int[] arrayDimensions)
        {
            this.arrayDimensions = arrayDimensions;
            return this;
        }

        /**
         * Builds the event.
         *
         * @return the allocation event
         */
        public AllocationEvent build()
        {
            return new AllocationEvent(this);
        }
    }
}
