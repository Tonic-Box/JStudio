package com.tonic.event.events;

import com.tonic.event.Event;
import lombok.Getter;

/** Posted when a live JVM session attaches or detaches, so attach-only UI can show or hide. */
@Getter
public class LiveSessionEvent extends Event
{

    private final boolean attached;

    /**
     * Creates the event.
     *
     * @param source the poster
     * @param attached whether a session is now attached
     */
    public LiveSessionEvent(Object source, boolean attached)
    {
        super(source);
        this.attached = attached;
    }
}
