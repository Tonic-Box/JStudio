package com.tonic.ui.vm.testgen.objectspec;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class ObjectSpec {

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

    public ObjectSpec() {
    }

    public ObjectSpec(String typeName) {
        this.typeName = typeName;
    }

    public static ObjectSpec nullSpec(String typeName) {
        ObjectSpec spec = new ObjectSpec(typeName);
        spec.mode = ConstructionMode.NULL;
        return spec;
    }

    public static ObjectSpec withConstructor(String typeName, String constructorDesc) {
        ObjectSpec spec = new ObjectSpec(typeName);
        spec.mode = ConstructionMode.CONSTRUCTOR;
        spec.constructorDescriptor = constructorDesc;
        return spec;
    }

    public static ObjectSpec withFactory(String typeName, String methodName, String methodDesc) {
        ObjectSpec spec = new ObjectSpec(typeName);
        spec.mode = ConstructionMode.FACTORY_METHOD;
        spec.factoryMethodName = methodName;
        spec.factoryMethodDescriptor = methodDesc;
        return spec;
    }

    public static ObjectSpec withExpression(String typeName, String expr) {
        ObjectSpec spec = new ObjectSpec(typeName);
        spec.mode = ConstructionMode.EXPRESSION;
        spec.expression = expr;
        return spec;
    }

    public static ObjectSpec fromTemplate(String typeName, String templateName) {
        ObjectSpec spec = new ObjectSpec(typeName);
        spec.mode = ConstructionMode.TEMPLATE;
        spec.templateName = templateName;
        return spec;
    }

    public void addConstructorArg(ParamSpec arg) {
        constructorArgs.add(arg);
    }

    public void addFactoryArg(ParamSpec arg) {
        factoryArgs.add(arg);
    }

    public void setFieldOverride(String fieldName, ParamSpec value) {
        fieldOverrides.put(fieldName, value);
    }

    public String getSimpleTypeName() {
        if (typeName == null) return "?";
        String name = typeName.replace('/', '.');
        int lastDot = name.lastIndexOf('.');
        return lastDot >= 0 ? name.substring(lastDot + 1) : name;
    }

    public String getSummary() {
        switch (mode) {
            case NULL:
                return "null";
            case CONSTRUCTOR:
                int argCount = constructorArgs.size();
                return getSimpleTypeName() + "(" + argCount + " args)";
            case FACTORY_METHOD:
                return getSimpleTypeName() + "." + factoryMethodName + "()";
            case EXPRESSION:
                if (expression != null && expression.length() > 30) {
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

    public boolean hasAnyFuzzParams() {
        for (ParamSpec arg : constructorArgs) {
            if (arg.getMode() == ValueMode.FUZZ) return true;
            if (arg.getMode() == ValueMode.OBJECT_SPEC &&
                arg.getNestedObjectSpec() != null &&
                arg.getNestedObjectSpec().hasAnyFuzzParams()) {
                return true;
            }
        }
        for (ParamSpec arg : factoryArgs) {
            if (arg.getMode() == ValueMode.FUZZ) return true;
        }
        for (ParamSpec field : fieldOverrides.values()) {
            if (field.getMode() == ValueMode.FUZZ) return true;
        }
        return false;
    }

    public ObjectSpec copy() {
        ObjectSpec copy = new ObjectSpec(typeName);
        copy.mode = mode;
        copy.constructorDescriptor = constructorDescriptor;
        copy.factoryMethodName = factoryMethodName;
        copy.factoryMethodDescriptor = factoryMethodDescriptor;
        copy.expression = expression;
        copy.templateName = templateName;

        for (ParamSpec arg : constructorArgs) {
            copy.constructorArgs.add(arg.copy());
        }
        for (ParamSpec arg : factoryArgs) {
            copy.factoryArgs.add(arg.copy());
        }
        for (Map.Entry<String, ParamSpec> entry : fieldOverrides.entrySet()) {
            copy.fieldOverrides.put(entry.getKey(), entry.getValue().copy());
        }
        return copy;
    }
}
