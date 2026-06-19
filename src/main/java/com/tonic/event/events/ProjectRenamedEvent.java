package com.tonic.event.events;

import com.tonic.event.Event;
import lombok.Getter;

/**
 * Posted after a programmatic (AI-driven) rename of a class/method/field so the UI refreshes to the new names: the
 * navigator tree, and any open editor tabs. {@code MainFrame} handles it on the EDT, since the rename itself runs on
 * the chat worker thread.
 */
@Getter
public class ProjectRenamedEvent extends Event {

    public enum Kind {
        CLASS, METHOD, FIELD
    }

    private final Kind kind;
    private final String oldClass;
    private final String newClass;
    private final String member;

    public ProjectRenamedEvent(Object source, Kind kind, String oldClass, String newClass, String member) {
        super(source);
        this.kind = kind;
        this.oldClass = oldClass;
        this.newClass = newClass;
        this.member = member;
    }
}
