package com.tonic.event.events;

import com.tonic.event.Event;
import lombok.Getter;

/** Posted when the JDI debug session connects or disconnects, so debugger-only UI can show or tear down. */
@Getter
public class DebugSessionEvent extends Event
{

    private final boolean connected;

    /**
     * Creates the event.
     *
     * @param source the poster
     * @param connected whether the session is now connected
     */
    public DebugSessionEvent(Object source, boolean connected)
    {
        super(source);
        this.connected = connected;
    }
}
