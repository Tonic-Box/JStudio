package com.tonic.ui.vm.heap.model;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import lombok.Getter;

/** A snapshot of one field's value on a heap object, with the referenced object id when the value is an object. */
@Getter
public class FieldValue
{
    private final String owner;
    private final String name;
    private final String descriptor;
    private final Object value;
    private final boolean reference;
    private final int referenceId;

    /**
     * Creates a field value and records whether it is a reference and which object it points to.
     *
     * @param owner the declaring class's internal name
     * @param name the field name
     * @param descriptor the field's type descriptor
     * @param value the field's value; an object instance for references, a boxed value for primitives, or null
     */
    public FieldValue(String owner, String name, String descriptor, Object value)
    {
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        this.value = value;
        this.reference = isReferenceDescriptor(descriptor);
        this.referenceId = extractReferenceId(value);
    }

    private boolean isReferenceDescriptor(String desc)
    {
        return desc != null && (desc.startsWith("L") || desc.startsWith("["));
    }

    private int extractReferenceId(Object val)
    {
        if (val == null)
        {
            return -1;
        }
        if (val instanceof ObjectInstance)
        {
            return ((ObjectInstance) val).getId();
        }
        return -1;
    }

    /**
     * Tells whether the value is null.
     *
     * @return true when the value is null
     */
    public boolean isNull()
    {
        return value == null;
    }

    /**
     * Tells whether the value points to a heap object with an id.
     *
     * @return true when the value is an object instance
     */
    public boolean hasReferenceId()
    {
        return referenceId >= 0;
    }

    /**
     * Converts the descriptor to a readable type name.
     *
     * @return the Java type name, such as int or java.lang.String[], or unknown when there is no descriptor
     */
    public String getTypeName()
    {
        return descriptorToTypeName(descriptor);
    }

    /**
     * Formats the value for display.
     *
     * @return null, the array component type and length with its id, the class name with its id, or the value as a string
     */
    public String getDisplayValue()
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
            ObjectInstance obj =
                    (ObjectInstance) value;
            return obj.getClassName() + " #" + obj.getId();
        }
        return String.valueOf(value);
    }

    private String descriptorToTypeName(String desc)
    {
        if (desc == null || desc.isEmpty())
        {
            return "unknown";
        }
        switch (desc.charAt(0))
        {
            case 'B':
                return "byte";
            case 'C':
                return "char";
            case 'D':
                return "double";
            case 'F':
                return "float";
            case 'I':
                return "int";
            case 'J':
                return "long";
            case 'S':
                return "short";
            case 'Z':
                return "boolean";
            case 'V':
                return "void";
            case '[':
                return descriptorToTypeName(desc.substring(1)) + "[]";
            case 'L':
                int end = desc.indexOf(';');
                if (end > 0)
                {
                    return desc.substring(1, end).replace('/', '.');
                }
                return desc;
            default:
                return desc;
        }
    }

    /**
     * Builds the key that identifies this field.
     *
     * @return owner.name:descriptor
     */
    public String getKey()
    {
        return owner + "." + name + ":" + descriptor;
    }

    @Override
    public String toString()
    {
        return name + ": " + getDisplayValue();
    }

    /**
     * Starts a new field value builder.
     *
     * @return an empty builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /** Builder for field values; owner, name and descriptor default to empty. */
    public static class Builder
    {
        private String owner = "";
        private String name = "";
        private String descriptor = "";
        private Object value;

        /**
         * Sets the declaring class.
         *
         * @param owner the declaring class's internal name
         * @return this builder
         */
        public Builder owner(String owner)
        {
            this.owner = owner;
            return this;
        }

        /**
         * Sets the field name.
         *
         * @param name the field name
         * @return this builder
         */
        public Builder name(String name)
        {
            this.name = name;
            return this;
        }

        /**
         * Sets the field's type descriptor.
         *
         * @param descriptor the field's type descriptor
         * @return this builder
         */
        public Builder descriptor(String descriptor)
        {
            this.descriptor = descriptor;
            return this;
        }

        /**
         * Sets the field's value.
         *
         * @param value the field's value, or null
         * @return this builder
         */
        public Builder value(Object value)
        {
            this.value = value;
            return this;
        }

        /**
         * Builds the field value.
         *
         * @return the field value
         */
        public FieldValue build()
        {
            return new FieldValue(owner, name, descriptor, value);
        }
    }
}
