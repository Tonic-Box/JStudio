package com.tonic.live.protocol;

import lombok.Getter;

/**
 * A static field of a class in the target JVM, with its current live value (read via reflection). The
 * {@code kind} ({@link LiveProtocol#STATIC_READONLY}/{@code PRIMITIVE}/{@code STRING}/{@code REFERENCE})
 * tells the UI how the value may be edited.
 */
@Getter
public final class StaticField {
    private final String name;
    private final String typeDesc;
    private final String value;
    private final int kind;

    public StaticField(String name, String typeDesc, String value, int kind) {
        this.name = name;
        this.typeDesc = typeDesc;
        this.value = value;
        this.kind = kind;
    }

}
