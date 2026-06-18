package com.tonic.event.events;

import com.tonic.event.Event;
import lombok.Getter;

/**
 * Posted when a project Run process starts or finishes, so UI (e.g. the editor gutter run/stop badge) can switch
 * between the run and terminate affordances.
 */
@Getter
public class RunStateEvent extends Event {

    private final boolean running;

    public RunStateEvent(Object source, boolean running) {
        super(source);
        this.running = running;
    }
}
