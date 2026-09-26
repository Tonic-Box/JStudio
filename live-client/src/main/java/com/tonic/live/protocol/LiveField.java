package com.tonic.live.protocol;

import lombok.Getter;

/** One field of a live instance, with its current display value and, for a reference, a handle the UI can navigate into. */
@Getter
public final class LiveField
{
    private final String name;
    private final String typeDesc;
    private final String display;
    private final long refHandleId;
    private final boolean editable;

    /**
     * Creates a field entry.
     *
     * @param name the field name
     * @param typeDesc the field's JVM type descriptor
     * @param display the current value as display text
     * @param refHandleId the handle of the referenced object, or 0 for a primitive or null
     * @param editable whether it is a non-final primitive or String field the UI may edit in place
     */
    public LiveField(String name, String typeDesc, String display, long refHandleId, boolean editable)
    {
        this.name = name;
        this.typeDesc = typeDesc;
        this.display = display;
        this.refHandleId = refHandleId;
        this.editable = editable;
    }

    /**
     * Reports whether the field refers to an object the UI can navigate into.
     *
     * @return true when there is a reference handle
     */
    public boolean isReference()
    {
        return refHandleId != 0;
    }

    /**
     * Reports whether the field is a boolean, for which the UI offers a true or false dropdown.
     *
     * @return true when the descriptor is Z
     */
    public boolean isBoolean()
    {
        return "Z".equals(typeDesc);
    }
}
