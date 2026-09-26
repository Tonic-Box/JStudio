package com.tonic.event.events;

import com.tonic.event.Event;
import lombok.Getter;

/** Posted when a project Run process starts or finishes, so the UI can switch between run and terminate controls. */
@Getter
public class RunStateEvent extends Event
{

    private final boolean running;

    /**
     * Creates the event.
     *
     * @param source the poster
     * @param running whether a run process is now running
     */
    public RunStateEvent(Object source, boolean running)
    {
        super(source);
        this.running = running;
    }
}
