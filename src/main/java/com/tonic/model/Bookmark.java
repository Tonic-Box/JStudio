package com.tonic.model;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** A user bookmark on a class, member or line, optionally bound to one of the ten quick slots. */
@Getter
@Setter
public class Bookmark
{

    public static final int NO_SLOT = -1;

    private String id;
    private String name;
    private String className;
    private String memberName;
    private int lineNumber;
    private int slot;
    private long timestamp;
    private String notes;

    /** Creates an unnamed, unslotted bookmark with a fresh id and the current time. */
    public Bookmark()
    {
        this.id = UUID.randomUUID().toString();
        this.lineNumber = -1;
        this.slot = NO_SLOT;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Creates a bookmark on a class.
     *
     * @param className the class's internal name, with slashes
     * @param name the user's label, or null to fall back to the location
     */
    public Bookmark(String className, String name)
    {
        this();
        this.className = className;
        this.name = name;
    }

    /**
     * Reports whether the bookmark is bound to a quick slot.
     *
     * @return true if the slot is between 0 and 9
     */
    public boolean hasSlot()
    {
        return slot >= 0 && slot <= 9;
    }

    /**
     * Builds the location key: the class, then #member and :line when set.
     *
     * @return the location key
     */
    public String getLocationKey()
    {
        StringBuilder key = new StringBuilder(className);
        if (memberName != null && !memberName.isEmpty())
        {
            key.append("#").append(memberName);
        }
        if (lineNumber >= 0)
        {
            key.append(":").append(lineNumber);
        }
        return key.toString();
    }

    /**
     * The name shown in lists.
     *
     * @return the user's label, or else the simple class name with #member when a member is set
     */
    public String getDisplayName()
    {
        if (name != null && !name.isEmpty())
        {
            return name;
        }
        String simple = className;
        int lastSlash = className.lastIndexOf('/');
        if (lastSlash >= 0)
        {
            simple = className.substring(lastSlash + 1);
        }
        if (memberName != null && !memberName.isEmpty())
        {
            return simple + "#" + memberName;
        }
        return simple;
    }

    @Override
    public String toString()
    {
        String prefix = hasSlot() ? "[" + slot + "] " : "";
        return prefix + getDisplayName();
    }
}
