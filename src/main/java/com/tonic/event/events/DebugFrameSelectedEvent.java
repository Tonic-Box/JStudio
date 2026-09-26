package com.tonic.event.events;

import com.tonic.event.Event;
import com.tonic.live.debug.DebugFrame;
import lombok.Getter;

/** Posted on the EDT when the active debugger frame changes, on a pause or a pick in the call stack; source views render that frame's values inline. */
@Getter
public class DebugFrameSelectedEvent extends Event
{

    private final DebugFrame frame;

    /**
     * Creates the event.
     *
     * @param source the poster
     * @param frame the newly active frame
     */
    public DebugFrameSelectedEvent(Object source, DebugFrame frame)
    {
        super(source);
        this.frame = frame;
    }
}
