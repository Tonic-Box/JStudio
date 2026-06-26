package com.tonic.event.events;

import com.tonic.event.Event;

/** Posted when the breakpoint set changes, so the source and bytecode gutters re-render their dots. */
public class BreakpointsChangedEvent extends Event {

    public BreakpointsChangedEvent(Object source) {
        super(source);
    }
}
