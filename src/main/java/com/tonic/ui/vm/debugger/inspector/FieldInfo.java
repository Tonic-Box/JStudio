package com.tonic.ui.vm.debugger.inspector;

import com.tonic.analysis.execution.state.ValueTag;
import lombok.Getter;

/** One field of an inspected object: its declaration, current value and value kind. */
public class FieldInfo
{

    @Getter
    private final String name;
    @Getter
    private final String descriptor;
    @Getter
    private final String ownerClass;
    @Getter
    private final Object value;
    @Getter
    private final ValueTag valueTag;
    private final boolean isFinal;
    private final boolean isStatic;

    /**
     * Creates the field description.
     *
     * @param name the field's name
     * @param descriptor the field's descriptor
     * @param ownerClass the internal name of the declaring class
     * @param value the field's current value
     * @param valueTag the kind of value, or null if unknown
     * @param isFinal whether the field is final
     * @param isStatic whether the field is static
     */
    public FieldInfo(String name, String descriptor, String ownerClass, Object value, ValueTag valueTag, boolean isFinal, boolean isStatic)
    {
        this.name = name;
        this.descriptor = descriptor;
        this.ownerClass = ownerClass;
        this.value = value;
        this.valueTag = valueTag;
        this.isFinal = isFinal;
        this.isStatic = isStatic;
    }

    /** @return whether the field is final */
    public boolean isFinal()
    {
        return isFinal;
    }

    /** @return whether the field is static */
    public boolean isStatic()
    {
        return isStatic;
    }

    /**
     * Returns whether the field can be edited.
     *
     * @return true if it is not final and its value kind is known
     */
    public boolean isEditable()
    {
        return !isFinal && valueTag != null;
    }

    /**
     * Returns the field's type as a short readable name.
     *
     * @return the type, such as int, String or int[], or unknown if there is no descriptor
     */
    public String getTypeName()
    {
        return descriptorToTypeName(descriptor);
    }

    /**
     * Returns the value as display text.
     *
     * @return the value's text, or "null"
     */
    public String getValueString()
    {
        if (value == null)
        {
            return "null";
        }
        return value.toString();
    }

    private static String descriptorToTypeName(String desc)
    {
        if (desc == null || desc.isEmpty())
        {
            return "unknown";
        }

        switch (desc.charAt(0))
        {
            case 'I':
                return "int";
            case 'J':
                return "long";
            case 'F':
                return "float";
            case 'D':
                return "double";
            case 'Z':
                return "boolean";
            case 'B':
                return "byte";
            case 'C':
                return "char";
            case 'S':
                return "short";
            case 'V':
                return "void";
            case '[':
                return descriptorToTypeName(desc.substring(1)) + "[]";
            case 'L':
                int end = desc.indexOf(';');
                if (end > 1)
                {
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
