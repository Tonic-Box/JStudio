package com.tonic.live.debug;

import lombok.Getter;

/** One variable visible in a frame, such as a local, an argument or this, with its display value and whether it is an expandable reference. */
@Getter
public final class DebugVariable
{
    private final String name;
    private final String typeDescriptor;
    private final String display;
    private final boolean reference;
    /** Handle to the underlying object for click-to-expand, or 0 when this is not a reference. */
    private final long refHandle;
    /** True for a non-char array (gets the element tooltip + viewer dialog); char[] is shown as a string. */
    private final boolean array;
    /** The element count for an array, else 0. */
    private final int arrayLength;

    /**
     * Creates a variable.
     *
     * @param name the variable name
     * @param typeDescriptor the best-effort JVM type descriptor, or empty when unknown
     * @param display the current value as display text
     * @param reference whether it is an object or array that can be expanded, as opposed to a primitive, null or String
     * @param refHandle the session handle for expanding it, or 0 when it is not a reference
     * @param array whether it is a non-char array; a char array is shown as a string
     * @param arrayLength the element count for an array, else 0
     */
    public DebugVariable(String name, String typeDescriptor, String display, boolean reference, long refHandle, boolean array, int arrayLength)
    {
        this.name = name;
        this.typeDescriptor = typeDescriptor;
        this.display = display;
        this.reference = reference;
        this.refHandle = refHandle;
        this.array = array;
        this.arrayLength = arrayLength;
    }
}
