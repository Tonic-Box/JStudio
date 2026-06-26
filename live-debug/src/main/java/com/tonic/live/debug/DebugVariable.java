package com.tonic.live.debug;

import lombok.Getter;

/**
 * One variable visible in a frame (a local, an argument, or {@code this}): its name, best-effort JVM type
 * descriptor, current display value, and whether it is a reference (object/array, hence expandable) as opposed
 * to a primitive, {@code null}, or String.
 */
@Getter
public final class DebugVariable {
    private final String name;
    private final String typeDescriptor;
    private final String display;
    private final boolean reference;

    public DebugVariable(String name, String typeDescriptor, String display, boolean reference) {
        this.name = name;
        this.typeDescriptor = typeDescriptor;
        this.display = display;
        this.reference = reference;
    }
}
