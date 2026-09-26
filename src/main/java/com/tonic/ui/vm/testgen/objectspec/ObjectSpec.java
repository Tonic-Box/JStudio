package com.tonic.ui.vm.testgen.objectspec;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** How to build one object argument: the construction mode plus its constructor, factory, expression, template or field values. */
@Getter
@Setter
public class ObjectSpec
{

    private String typeName;
    private ConstructionMode mode = ConstructionMode.CONSTRUCTOR;
    private String constructorDescriptor;
    private List<ParamSpec> constructorArgs = new ArrayList<>();
    private String factoryMethodName;
    private String factoryMethodDescriptor;
    private List<ParamSpec> factoryArgs = new ArrayList<>();
    private String expression;
    private String templateName;
    private Map<String, ParamSpec> fieldOverrides = new LinkedHashMap<>();

    /** Creates an empty spec using the constructor mode. */
    public ObjectSpec()
    {
    }

    /**
     * Creates a spec for a type using the constructor mode.
     *
     * @param typeName the class's internal name, with slashes
     */
    public ObjectSpec(String typeName)
    {
        this.typeName = typeName;
    }

    /**
     * Creates a spec that passes null.
     *
     * @param typeName the class's internal name, with slashes
     * @return the new spec
     */
    public static ObjectSpec nullSpec(String typeName)
    {
        ObjectSpec spec = new ObjectSpec(typeName);
        spec.mode = ConstructionMode.NULL;
        return spec;
    }

    /**
     * Creates a spec that calls a constructor.
     *
     * @param typeName the class's internal name, with slashes
     * @param constructorDesc the constructor's descriptor
     * @return the new spec, with no arguments yet
     */
    public static ObjectSpec withConstructor(String typeName, String constructorDesc)
    {
        ObjectSpec spec = new ObjectSpec(typeName);
        spec.mode = ConstructionMode.CONSTRUCTOR;
        spec.constructorDescriptor = constructorDesc;
        return spec;
    }

    /**
     * Creates a spec that calls a static factory method.
     *
     * @param typeName the class's internal name, with slashes
     * @param methodName the factory method's name
     * @param methodDesc the factory method's descriptor
     * @return the new spec, with no arguments yet
     */
    public static ObjectSpec withFactory(String typeName, String methodName, String methodDesc)
    {
        ObjectSpec spec = new ObjectSpec(typeName);
        spec.mode = ConstructionMode.FACTORY_METHOD;
        spec.factoryMethodName = methodName;
        spec.factoryMethodDescriptor = methodDesc;
        return spec;
    }

    /**
     * Creates a spec given by a Java expression.
     *
     * @param typeName the class's internal name, with slashes
     * @param expr the expression's source text
     * @return the new spec
     */
    public static ObjectSpec withExpression(String typeName, String expr)
    {
        ObjectSpec spec = new ObjectSpec(typeName);
        spec.mode = ConstructionMode.EXPRESSION;
        spec.expression = expr;
        return spec;
    }

    /**
     * Creates a spec that uses a saved template.
     *
     * @param typeName the class's internal name, with slashes
     * @param templateName the template's name
     * @return the new spec
     */
    public static ObjectSpec fromTemplate(String typeName, String templateName)
    {
        ObjectSpec spec = new ObjectSpec(typeName);
        spec.mode = ConstructionMode.TEMPLATE;
        spec.templateName = templateName;
        return spec;
    }

    /**
     * Appends a constructor argument.
     *
     * @param arg the argument's spec
     */
    public void addConstructorArg(ParamSpec arg)
    {
        constructorArgs.add(arg);
    }

    /**
     * Appends a factory method argument.
     *
     * @param arg the argument's spec
     */
    public void addFactoryArg(ParamSpec arg)
    {
        factoryArgs.add(arg);
    }

    /**
     * Sets the value for a field, replacing any earlier one.
     *
     * @param fieldName the field's name
     * @param value the value's spec
     */
    public void setFieldOverride(String fieldName, ParamSpec value)
    {
        fieldOverrides.put(fieldName, value);
    }

    /**
     * Strips the package from the type name.
     *
     * @return the type's simple name, or "?" when no type is set
     */
    public String getSimpleTypeName()
    {
        if (typeName == null) return "?";
        String name = typeName.replace('/', '.');
        int lastDot = name.lastIndexOf('.');
        return lastDot >= 0 ? name.substring(lastDot + 1) : name;
    }

    /**
     * Describes the spec for display.
     *
     * @return a short summary for the mode, such as Point(2 args), a shortened expression, or "null"
     */
    public String getSummary()
    {
        switch (mode)
        {
            case NULL:
                return "null";
            case CONSTRUCTOR:
                int argCount = constructorArgs.size();
                return getSimpleTypeName() + "(" + argCount + " args)";
            case FACTORY_METHOD:
                return getSimpleTypeName() + "." + factoryMethodName + "()";
            case EXPRESSION:
                if (expression != null && expression.length() > 30)
                {
                    return expression.substring(0, 27) + "...";
                }
                return expression;
            case TEMPLATE:
                return "template:" + templateName;
            case FIELD_INJECTION:
                return getSimpleTypeName() + "{fields}";
            default:
                return mode.getDisplayName();
        }
    }

    /**
     * Checks whether any argument or field value is fuzzed, following nested object specs of constructor arguments only.
     *
     * @return true if a value is fuzzed
     */
    public boolean hasAnyFuzzParams()
    {
        for (ParamSpec arg : constructorArgs)
        {
            if (arg.getMode() == ValueMode.FUZZ) return true;
            if (arg.getMode() == ValueMode.OBJECT_SPEC &&
                    arg.getNestedObjectSpec() != null &&
                    arg.getNestedObjectSpec().hasAnyFuzzParams())
            {
                return true;
            }
        }
        for (ParamSpec arg : factoryArgs)
        {
            if (arg.getMode() == ValueMode.FUZZ) return true;
        }
        for (ParamSpec field : fieldOverrides.values())
        {
            if (field.getMode() == ValueMode.FUZZ) return true;
        }
        return false;
    }

    /**
     * Copies the spec, deep-copying the argument and field specs.
     *
     * @return the copy
     */
    public ObjectSpec copy()
    {
        ObjectSpec copy = new ObjectSpec(typeName);
        copy.mode = mode;
        copy.constructorDescriptor = constructorDescriptor;
        copy.factoryMethodName = factoryMethodName;
        copy.factoryMethodDescriptor = factoryMethodDescriptor;
        copy.expression = expression;
        copy.templateName = templateName;

        for (ParamSpec arg : constructorArgs)
        {
            copy.constructorArgs.add(arg.copy());
        }
        for (ParamSpec arg : factoryArgs)
        {
            copy.factoryArgs.add(arg.copy());
        }
        for (Map.Entry<String, ParamSpec> entry : fieldOverrides.entrySet())
        {
            copy.fieldOverrides.put(entry.getKey(), entry.getValue().copy());
        }
        return copy;
    }
}
