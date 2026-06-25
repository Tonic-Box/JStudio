package com.tonic.live.protocol;

import lombok.Getter;

/**
 * One field of a live instance: its name, JVM type descriptor, current display value, and (for reference
 * fields) a {@code refHandleId} the UI can navigate into. {@code editable} is true for a non-final primitive
 * or String field, which the UI may edit in place.
 */
@Getter
public final class LiveField {
    private final String name;
    private final String typeDesc;
    private final String display;
    private final long refHandleId;
    private final boolean editable;

    public LiveField(String name, String typeDesc, String display, long refHandleId, boolean editable) {
        this.name = name;
        this.typeDesc = typeDesc;
        this.display = display;
        this.refHandleId = refHandleId;
        this.editable = editable;
    }

    public boolean isReference() {
        return refHandleId != 0;
    }

    /** True when the field is a boolean (descriptor {@code Z}) — the UI offers a true/false dropdown. */
    public boolean isBoolean() {
        return "Z".equals(typeDesc);
    }
}
