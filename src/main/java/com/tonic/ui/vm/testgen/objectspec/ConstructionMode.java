package com.tonic.ui.vm.testgen.objectspec;

public enum ConstructionMode {
    CONSTRUCTOR("Use Constructor"),
    FACTORY_METHOD("Use Factory Method"),
    FIELD_INJECTION("Direct Field Injection"),
    EXPRESSION("Java Expression"),
    TEMPLATE("Use Saved Template"),
    NULL("Null Reference");

    private final String displayName;

    ConstructionMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
