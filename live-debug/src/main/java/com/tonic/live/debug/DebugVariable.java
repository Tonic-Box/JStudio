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
    /** Handle to the underlying object for click-to-expand, or 0 when this is not a reference. */
    private final long refHandle;
    /** True for a non-char array (gets the element tooltip + viewer dialog); char[] is shown as a string. */
    private final boolean array;
    /** Element count when {@link #array}, else 0. */
    private final int arrayLength;

    public DebugVariable(String name, String typeDescriptor, String display, boolean reference, long refHandle,
                         boolean array, int arrayLength) {
        this.name = name;
        this.typeDescriptor = typeDescriptor;
        this.display = display;
        this.reference = reference;
        this.refHandle = refHandle;
        this.array = array;
        this.arrayLength = arrayLength;
    }
}
