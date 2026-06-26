package com.tonic.event.events;

import com.tonic.event.Event;
import lombok.Getter;

/**
 * Posted when the optional JDI debug session connects or disconnects, so debugger-only UI (the breakpoint
 * gutters, the Debugger tool window, the suspend toggle) can show or tear down.
 */
@Getter
public class DebugSessionEvent extends Event {

    private final boolean connected;

    public DebugSessionEvent(Object source, boolean connected) {
        super(source);
        this.connected = connected;
    }
}
