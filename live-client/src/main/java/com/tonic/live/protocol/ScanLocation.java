package com.tonic.live.protocol;

import lombok.Getter;

/** One value-scan result: a stable agent-side id, the owning field's identity, a readable path, and the current value. */
public final class ScanLocation
{

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

    /**
     * Creates a scan result.
     *
     * @param id the agent's stable id for the location
     * @param declaringClass the internal name of the field's declaring class, or empty when there is none
     * @param fieldName the field name, or empty when there is none
     * @param fieldDesc the field's JVM descriptor
     * @param displayPath the readable path from a root to the value
     * @param type the value's type name
     * @param value the current value as text
     * @param flags the pinned, frozen and collected bits
     */
    public ScanLocation(long id, String declaringClass, String fieldName, String fieldDesc, String displayPath, String type, String value, int flags)
    {
        this.id = id;
        this.declaringClass = declaringClass;
        this.fieldName = fieldName;
        this.fieldDesc = fieldDesc;
        this.displayPath = displayPath;
        this.type = type;
        this.value = value;
        this.flags = flags;
    }

    /**
     * Reports whether the location is on the watch list.
     *
     * @return true when pinned
     */
    public boolean isPinned()
    {
        return (flags & LiveProtocol.FLAG_PINNED) != 0;
    }

    /**
     * Reports whether the location's value is held by a freeze.
     *
     * @return true when frozen
     */
    public boolean isFrozen()
    {
        return (flags & LiveProtocol.FLAG_FROZEN) != 0;
    }

    /**
     * Reports whether the object holding the location has been garbage collected.
     *
     * @return true when collected
     */
    public boolean isCollected()
    {
        return (flags & LiveProtocol.FLAG_COLLECTED) != 0;
    }

    /**
     * Reports whether the result names a declared field that the static tools, such as usages and rename, can act on.
     *
     * @return true when both the declaring class and the field name are set
     */
    public boolean hasField()
    {
        return declaringClass != null && !declaringClass.isEmpty() && fieldName != null && !fieldName.isEmpty();
    }
}
