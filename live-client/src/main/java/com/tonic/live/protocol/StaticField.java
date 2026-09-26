package com.tonic.live.protocol;

import lombok.Getter;

/** A static field of a class in the target JVM with its current value; its kind, one of the protocol's STATIC_ constants, tells the UI how it may be edited. */
@Getter
public final class StaticField
{
    private final String name;
    private final String typeDesc;
    private final String value;
    private final int kind;

    /**
     * Creates a static field entry.
     *
     * @param name the field name
     * @param typeDesc the field's JVM type descriptor
     * @param value the current value as text
     * @param kind how the value may be edited, one of the protocol's STATIC_ constants
     */
    public StaticField(String name, String typeDesc, String value, int kind)
    {
        this.name = name;
        this.typeDesc = typeDesc;
        this.value = value;
        this.kind = kind;
    }

}
