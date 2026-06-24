package com.tonic.live.protocol;

import lombok.Getter;

/**
 * One live value-scan result: a stable agent-side handle id plus the owning field's identity (declaring class /
 * name / descriptor - for the static launchpad), a human-readable path, and the current value.
 */
public final class ScanLocation {

    @Getter
    private final long id;
    @Getter
    private final String declaringClass;
    @Getter
    private final String fieldName;
    @Getter
    private final String fieldDesc;
    @Getter
    private final String displayPath;
    @Getter
    private final String type;
    @Getter
    private final String value;
    private final int flags;

    public ScanLocation(long id, String declaringClass, String fieldName, String fieldDesc,
                        String displayPath, String type, String value, int flags) {
        this.id = id;
        this.declaringClass = declaringClass;
        this.fieldName = fieldName;
        this.fieldDesc = fieldDesc;
        this.displayPath = displayPath;
        this.type = type;
        this.value = value;
        this.flags = flags;
    }

    public boolean isPinned() {
        return (flags & LiveProtocol.FLAG_PINNED) != 0;
    }

    public boolean isFrozen() {
        return (flags & LiveProtocol.FLAG_FROZEN) != 0;
    }

    public boolean isCollected() {
        return (flags & LiveProtocol.FLAG_COLLECTED) != 0;
    }

    /** True when this result names a concrete declared field that the static tools (usages/rename) can act on. */
    public boolean hasField() {
        return declaringClass != null && !declaringClass.isEmpty() && fieldName != null && !fieldName.isEmpty();
    }
}
