package com.tonic.event.events;

import com.tonic.event.Event;
import lombok.Getter;

/** Posted after a programmatic rename of a class, method or field so the navigator and open tabs refresh; posted from the chat worker thread. */
@Getter
public class ProjectRenamedEvent extends Event
{

    /** The kind of symbol renamed. */
    public enum Kind
    {
        CLASS, METHOD, FIELD
    }

    private final Kind kind;
    private final String oldClass;
    private final String newClass;
    private final String member;

    /**
     * Creates the event.
     *
     * @param source the poster
     * @param kind what was renamed
     * @param oldClass the owning class's name before the rename
     * @param newClass the owning class's name after the rename
     * @param member the renamed member's name, for a method or field rename
     */
    public ProjectRenamedEvent(Object source, Kind kind, String oldClass, String newClass, String member)
    {
        super(source);
        this.kind = kind;
        this.oldClass = oldClass;
        this.newClass = newClass;
        this.member = member;
    }
}
