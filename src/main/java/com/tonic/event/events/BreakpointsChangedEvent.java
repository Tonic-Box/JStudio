package com.tonic.event.events;

import com.tonic.event.Event;

/** Posted when the breakpoint set changes, so the source and bytecode gutters re-render their dots. */
public class BreakpointsChangedEvent extends Event
{

    /**
     * Creates the event.
     *
     * @param source the poster
     */
    public BreakpointsChangedEvent(Object source)
    {
        super(source);
    }
}
