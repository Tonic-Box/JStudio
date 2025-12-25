package com.tonic.ui.vm.testgen.objectspec;

public class ParamSpec {

    private String name;
    private String typeDescriptor;
    private ValueMode mode = ValueMode.FUZZ;
    private Object fixedValue;
    private FuzzStrategy fuzzStrategy;
    private ObjectSpec nestedObjectSpec;
    private String templateName;

    public ParamSpec() {
        this.fuzzStrategy = FuzzStrategy.defaultStrategy();
    }

    public ParamSpec(String name, String typeDescriptor) {
        this.name = name;
        this.typeDescriptor = typeDescriptor;
        this.fuzzStrategy = FuzzStrategy.defaultStrategy();
    }

    public static ParamSpec fixed(String name, String typeDesc, Object value) {
        ParamSpec spec = new ParamSpec(name, typeDesc);
        spec.mode = ValueMode.FIXED;
        spec.fixedValue = value;
        return spec;
    }

    public static ParamSpec fuzz(String name, String typeDesc) {
        ParamSpec spec = new ParamSpec(name, typeDesc);
        spec.mode = ValueMode.FUZZ;
        return spec;
    }

    public static ParamSpec fuzz(String name, String typeDesc, FuzzStrategy strategy) {
        ParamSpec spec = new ParamSpec(name, typeDesc);
        spec.mode = ValueMode.FUZZ;
        spec.fuzzStrategy = strategy;
        return spec;
    }

    public static ParamSpec nullValue(String name, String typeDesc) {
        ParamSpec spec = new ParamSpec(name, typeDesc);
        spec.mode = ValueMode.NULL;
        return spec;
    }

    public static ParamSpec object(String name, String typeDesc, ObjectSpec objectSpec) {
        ParamSpec spec = new ParamSpec(name, typeDesc);
        spec.mode = ValueMode.OBJECT_SPEC;
        spec.nestedObjectSpec = objectSpec;
        return spec;
    }

    public boolean isPrimitive() {
        if (typeDescriptor == null) return false;
        return typeDescriptor.length() == 1 && "ZBCSIJFD".contains(typeDescriptor);
    }

    public boolean isString() {
        return "Ljava/lang/String;".equals(typeDescriptor);
    }

    public boolean isObjectType() {
        return typeDescriptor != null &&
               (typeDescriptor.startsWith("L") || typeDescriptor.startsWith("["));
    }

    public String getSimpleTypeName() {
        if (typeDescriptor == null) return "?";
        switch (typeDescriptor) {
            case "Z": return "boolean";
            case "B": return "byte";
            case "C": return "char";
            case "S": return "short";
            case "I": return "int";
            case "J": return "long";
            case "F": return "float";
            case "D": return "double";
            case "V": return "void";
            case "Ljava/lang/String;": return "String";
            default:
                if (typeDescriptor.startsWith("L") && typeDescriptor.endsWith(";")) {
                    String className = typeDescriptor.substring(1, typeDescriptor.length() - 1);
                    int lastSlash = className.lastIndexOf('/');
                    return lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
                }
                if (typeDescriptor.startsWith("[")) {
                    return getArrayTypeName(typeDescriptor);
                }
                return typeDescriptor;
        }
    }

    private String getArrayTypeName(String desc) {
        int dims = 0;
        while (dims < desc.length() && desc.charAt(dims) == '[') {
            dims++;
        }
        String base = desc.substring(dims);
        ParamSpec temp = new ParamSpec(null, base);
        StringBuilder sb = new StringBuilder(temp.getSimpleTypeName());
        for (int i = 0; i < dims; i++) {
            sb.append("[]");
        }
        return sb.toString();
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTypeDescriptor() { return typeDescriptor; }
    public void setTypeDescriptor(String typeDescriptor) { this.typeDescriptor = typeDescriptor; }

    public ValueMode getMode() { return mode; }
    public void setMode(ValueMode mode) { this.mode = mode; }

    public Object getFixedValue() { return fixedValue; }
    public void setFixedValue(Object fixedValue) { this.fixedValue = fixedValue; }

    public FuzzStrategy getFuzzStrategy() { return fuzzStrategy; }
    public void setFuzzStrategy(FuzzStrategy fuzzStrategy) { this.fuzzStrategy = fuzzStrategy; }

    public ObjectSpec getNestedObjectSpec() { return nestedObjectSpec; }
    public void setNestedObjectSpec(ObjectSpec nestedObjectSpec) { this.nestedObjectSpec = nestedObjectSpec; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    public String getSummary() {
        switch (mode) {
            case FIXED:
                if (fixedValue == null) return "null";
                if (fixedValue instanceof String) {
                    String s = (String) fixedValue;
                    if (s.length() > 20) return "\"" + s.substring(0, 17) + "...\"";
                    return "\"" + s + "\"";
                }
                return String.valueOf(fixedValue);
            case FUZZ:
                return "🎲 " + (fuzzStrategy != null ? fuzzStrategy.getDescription() : "fuzz");
            case OBJECT_SPEC:
                if (nestedObjectSpec != null) {
                    return "→ " + nestedObjectSpec.getSummary();
                }
                return "→ configured";
            case NULL:
                return "null";
            default:
                return mode.getDisplayName();
        }
    }

    public ParamSpec copy() {
        ParamSpec copy = new ParamSpec(name, typeDescriptor);
        copy.mode = mode;
        copy.fixedValue = fixedValue;
        copy.fuzzStrategy = fuzzStrategy;
        copy.nestedObjectSpec = nestedObjectSpec != null ? nestedObjectSpec.copy() : null;
        copy.templateName = templateName;
        return copy;
    }
}
