package com.tonic.event.events;

import com.tonic.event.Event;
import lombok.Getter;

/**
 * Posted when a live JVM session is attached or detached, so UI elements that are only meaningful while
 * attached (e.g. the "Live Instances" view mode) can show or hide themselves.
 */
@Getter
public class LiveSessionEvent extends Event {

    private final boolean attached;

    public LiveSessionEvent(Object source, boolean attached) {
        super(source);
        this.attached = attached;
    }
}
