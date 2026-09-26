package com.tonic.ui.vm.testgen.objectspec;

import lombok.Getter;

/** How an object argument is built: by constructor, factory method, field injection, expression or saved template, or left null. */
@Getter
public enum ConstructionMode
{
    CONSTRUCTOR("Use Constructor"),
    FACTORY_METHOD("Use Factory Method"),
    FIELD_INJECTION("Direct Field Injection"),
    EXPRESSION("Java Expression"),
    TEMPLATE("Use Saved Template"),
    NULL("Null Reference");

    private final String displayName;

    ConstructionMode(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
