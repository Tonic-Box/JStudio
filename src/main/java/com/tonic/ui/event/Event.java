package com.tonic.ui.event;

import lombok.Getter;

/**
 * Base class for all JStudio events.
 */
@Getter
public abstract class Event {

    private final long timestamp;
    private final Object source;

    protected Event(Object source) {
        this.timestamp = System.currentTimeMillis();
        this.source = source;
    }

}
