package com.tonic.ui.vm.heap.model;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import lombok.Getter;

/** One field write seen during heap forensics: the field, its old and new values, and when and where it happened. */
@Getter
public class MutationEvent
{
    /** The bytecode instruction that wrote a field. */
    @Getter
    public enum MutationType
    {
        PUTFIELD(0xB5),
        PUTSTATIC(0xB3);

        private final int opcode;

        MutationType(int opcode)
        {
            this.opcode = opcode;
        }

        /**
         * Maps a field-write opcode to its type.
         *
         * @param opcode the bytecode opcode
         * @return PUTSTATIC for 0xB3, otherwise PUTFIELD
         */
        public static MutationType fromOpcode(int opcode)
        {
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

    private MutationEvent(Builder builder)
    {
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

    /**
     * Tells whether the write was to a static field.
     *
     * @return true for PUTSTATIC
     */
    public boolean isStatic()
    {
        return mutationType == MutationType.PUTSTATIC;
    }

    /**
     * Builds the key of the written field.
     *
     * @return owner.name:descriptor
     */
    public String getFieldKey()
    {
        return fieldOwner + "." + fieldName + ":" + fieldDescriptor;
    }

    /**
     * Formats the value before the write.
     *
     * @return null, the array component type and length with its id, the class name with its id, or the value as a string
     */
    public String getDisplayOldValue()
    {
        return formatValue(oldValue);
    }

    /**
     * Formats the value after the write.
     *
     * @return null, the array component type and length with its id, the class name with its id, or the value as a string
     */
    public String getDisplayNewValue()
    {
        return formatValue(newValue);
    }

    private String formatValue(Object value)
    {
        if (value == null)
        {
            return "null";
        }
        if (value instanceof ArrayInstance)
        {
            ArrayInstance arr =
                    (ArrayInstance) value;
            return arr.getComponentType() + "[" + arr.getLength() + "] #" + arr.getId();
        }
        if (value instanceof ObjectInstance)
        {
            ObjectInstance obj = (ObjectInstance) value;
            return obj.getClassName() + " #" + obj.getId();
        }
        return String.valueOf(value);
    }

    @Override
    public String toString()
    {
        return "MutationEvent{" +
                (isStatic() ? "static " : "#" + objectId + ".") +
                fieldName + " = " + getDisplayOldValue() + " -> " + getDisplayNewValue() +
                " @ " + instructionCount +
                '}';
    }

    /**
     * Starts a new mutation builder.
     *
     * @return an empty builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /** Builder for mutation events; the object id defaults to -1, the strings to empty and the type to PUTFIELD. */
    public static class Builder
    {
        private int objectId = -1;
        private String fieldOwner = "";
        private String fieldName = "";
        private String fieldDescriptor = "";
        private Object oldValue;
        private Object newValue;
        private long instructionCount;
        private MutationType mutationType = MutationType.PUTFIELD;
        private ProvenanceInfo provenance;

        /**
         * Sets the written object.
         *
         * @param objectId the heap id of the object written, or -1 for a static field
         * @return this builder
         */
        public Builder objectId(int objectId)
        {
            this.objectId = objectId;
            return this;
        }

        /**
         * Sets the field's declaring class.
         *
         * @param fieldOwner the declaring class's internal name
         * @return this builder
         */
        public Builder fieldOwner(String fieldOwner)
        {
            this.fieldOwner = fieldOwner;
            return this;
        }

        /**
         * Sets the field name.
         *
         * @param fieldName the field name
         * @return this builder
         */
        public Builder fieldName(String fieldName)
        {
            this.fieldName = fieldName;
            return this;
        }

        /**
         * Sets the field's type descriptor.
         *
         * @param fieldDescriptor the field's type descriptor
         * @return this builder
         */
        public Builder fieldDescriptor(String fieldDescriptor)
        {
            this.fieldDescriptor = fieldDescriptor;
            return this;
        }

        /**
         * Sets the value before the write.
         *
         * @param oldValue the previous value, or null
         * @return this builder
         */
        public Builder oldValue(Object oldValue)
        {
            this.oldValue = oldValue;
            return this;
        }

        /**
         * Sets the value written.
         *
         * @param newValue the new value, or null
         * @return this builder
         */
        public Builder newValue(Object newValue)
        {
            this.newValue = newValue;
            return this;
        }

        /**
         * Sets when the write happened.
         *
         * @param instructionCount the executed instruction count at the write
         * @return this builder
         */
        public Builder instructionCount(long instructionCount)
        {
            this.instructionCount = instructionCount;
            return this;
        }

        /**
         * Sets the mutation type.
         *
         * @param mutationType the instruction kind that wrote
         * @return this builder
         */
        public Builder mutationType(MutationType mutationType)
        {
            this.mutationType = mutationType;
            return this;
        }

        /**
         * Sets the mutation type from an opcode.
         *
         * @param opcode the writing bytecode opcode
         * @return this builder
         */
        public Builder opcode(int opcode)
        {
            this.mutationType = MutationType.fromOpcode(opcode);
            return this;
        }

        /**
         * Sets where the write happened.
         *
         * @param provenance the writing location, or null if unknown
         * @return this builder
         */
        public Builder provenance(ProvenanceInfo provenance)
        {
            this.provenance = provenance;
            return this;
        }

        /**
         * Builds the event.
         *
         * @return the mutation event
         */
        public MutationEvent build()
        {
            return new MutationEvent(this);
        }
    }
}
