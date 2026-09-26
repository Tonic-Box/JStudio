package com.tonic.ui.vm.testgen.objectspec;

import lombok.Getter;
import lombok.Setter;

/** How one parameter gets its values: its name, type descriptor, value mode, and the fixed value, fuzz strategy or nested object spec that mode uses. */
@Getter
@Setter
public class ParamSpec
{

    private String name;
    private String typeDescriptor;
    private ValueMode mode = ValueMode.FUZZ;
    private Object fixedValue;
    private FuzzStrategy fuzzStrategy;
    private ObjectSpec nestedObjectSpec;
    private String templateName;

    /** Creates an unnamed fuzzed spec with the default strategy. */
    public ParamSpec()
    {
        this.fuzzStrategy = FuzzStrategy.defaultStrategy();
    }

    /**
     * Creates a fuzzed spec with the default strategy.
     *
     * @param name the parameter's name
     * @param typeDescriptor the parameter's type descriptor
     */
    public ParamSpec(String name, String typeDescriptor)
    {
        this.name = name;
        this.typeDescriptor = typeDescriptor;
        this.fuzzStrategy = FuzzStrategy.defaultStrategy();
    }

    /**
     * Creates a spec with a fixed value.
     *
     * @param name the parameter's name
     * @param typeDesc the parameter's type descriptor
     * @param value the value to pass
     * @return the new spec
     */
    public static ParamSpec fixed(String name, String typeDesc, Object value)
    {
        ParamSpec spec = new ParamSpec(name, typeDesc);
        spec.mode = ValueMode.FIXED;
        spec.fixedValue = value;
        return spec;
    }

    /**
     * Creates a fuzzed spec with the default strategy.
     *
     * @param name the parameter's name
     * @param typeDesc the parameter's type descriptor
     * @return the new spec
     */
    public static ParamSpec fuzz(String name, String typeDesc)
    {
        ParamSpec spec = new ParamSpec(name, typeDesc);
        spec.mode = ValueMode.FUZZ;
        return spec;
    }

    /**
     * Creates a fuzzed spec with a given strategy.
     *
     * @param name the parameter's name
     * @param typeDesc the parameter's type descriptor
     * @param strategy how to fuzz the value
     * @return the new spec
     */
    public static ParamSpec fuzz(String name, String typeDesc, FuzzStrategy strategy)
    {
        ParamSpec spec = new ParamSpec(name, typeDesc);
        spec.mode = ValueMode.FUZZ;
        spec.fuzzStrategy = strategy;
        return spec;
    }

    /**
     * Creates a spec that passes null.
     *
     * @param name the parameter's name
     * @param typeDesc the parameter's type descriptor
     * @return the new spec
     */
    public static ParamSpec nullValue(String name, String typeDesc)
    {
        ParamSpec spec = new ParamSpec(name, typeDesc);
        spec.mode = ValueMode.NULL;
        return spec;
    }

    /**
     * Creates a spec whose value is built from an object spec.
     *
     * @param name the parameter's name
     * @param typeDesc the parameter's type descriptor
     * @param objectSpec how to build the object
     * @return the new spec
     */
    public static ParamSpec object(String name, String typeDesc, ObjectSpec objectSpec)
    {
        ParamSpec spec = new ParamSpec(name, typeDesc);
        spec.mode = ValueMode.OBJECT_SPEC;
        spec.nestedObjectSpec = objectSpec;
        return spec;
    }

    /**
     * Checks for a primitive type.
     *
     * @return true if the type is a primitive other than void
     */
    public boolean isPrimitive()
    {
        if (typeDescriptor == null) return false;
        return typeDescriptor.length() == 1 && "ZBCSIJFD".contains(typeDescriptor);
    }

    /**
     * Checks for the String type.
     *
     * @return true if the type is String
     */
    public boolean isString()
    {
        return "Ljava/lang/String;".equals(typeDescriptor);
    }

    /**
     * Checks for a class or array type.
     *
     * @return true if the type is a class or array type
     */
    public boolean isObjectType()
    {
        return typeDescriptor != null &&
                (typeDescriptor.startsWith("L") || typeDescriptor.startsWith("["));
    }

    /**
     * Converts the type descriptor to a Java type name.
     *
     * @return the Java name of the type, simple for classes and with brackets for arrays, or "?" when no type is set
     */
    public String getSimpleTypeName()
    {
        if (typeDescriptor == null) return "?";
        switch (typeDescriptor)
        {
            case "Z":
                return "boolean";
            case "B":
                return "byte";
            case "C":
                return "char";
            case "S":
                return "short";
            case "I":
                return "int";
            case "J":
                return "long";
            case "F":
                return "float";
            case "D":
                return "double";
            case "V":
                return "void";
            case "Ljava/lang/String;":
                return "String";
            default:
                if (typeDescriptor.startsWith("L") && typeDescriptor.endsWith(";"))
                {
                    String className = typeDescriptor.substring(1, typeDescriptor.length() - 1);
                    int lastSlash = className.lastIndexOf('/');
                    return lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
                }
                if (typeDescriptor.startsWith("["))
                {
                    return getArrayTypeName(typeDescriptor);
                }
                return typeDescriptor;
        }
    }

    private String getArrayTypeName(String desc)
    {
        int dims = 0;
        while (dims < desc.length() && desc.charAt(dims) == '[')
        {
            dims++;
        }
        String base = desc.substring(dims);
        ParamSpec temp = new ParamSpec(null, base);
        return temp.getSimpleTypeName() + "[]".repeat(dims);
    }

    /**
     * Describes the spec for display.
     *
     * @return a short summary for the mode, such as the quoted fixed value, the fuzz strategy or the nested object's summary
     */
    public String getSummary()
    {
        switch (mode)
        {
            case FIXED:
                if (fixedValue == null) return "null";
                if (fixedValue instanceof String)
                {
                    String s = (String) fixedValue;
                    if (s.length() > 20) return "\"" + s.substring(0, 17) + "...\"";
                    return "\"" + s + "\"";
                }
                return String.valueOf(fixedValue);
            case FUZZ:
                return "🎲 " + (fuzzStrategy != null ? fuzzStrategy.getDescription() : "fuzz");
            case OBJECT_SPEC:
                if (nestedObjectSpec != null)
                {
                    return "-> " + nestedObjectSpec.getSummary();
                }
                return "-> configured";
            case NULL:
                return "null";
            default:
                return mode.getDisplayName();
        }
    }

    /**
     * Copies the spec, deep-copying the fuzz strategy and the nested object spec.
     *
     * @return the copy
     */
    public ParamSpec copy()
    {
        ParamSpec copy = new ParamSpec(name, typeDescriptor);
        copy.mode = mode;
        copy.fixedValue = fixedValue;
        copy.fuzzStrategy = fuzzStrategy != null ? fuzzStrategy.copy() : null;
        copy.nestedObjectSpec = nestedObjectSpec != null ? nestedObjectSpec.copy() : null;
        copy.templateName = templateName;
        return copy;
    }
}
