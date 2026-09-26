package com.tonic.event.events;

import com.tonic.event.Event;
import com.tonic.live.debug.DebugFrame;
import com.tonic.live.debug.DebugLocation;
import lombok.Getter;

import java.util.List;

/** Posted on the EDT when the target suspends at a breakpoint, carrying the top location and the paused thread's call stack. */
@Getter
public class DebugPausedEvent extends Event
{

    private final DebugLocation location;
    private final List<DebugFrame> frames;

    /**
     * Creates the event.
     *
     * @param source the poster
     * @param location the location to navigate to and highlight
     * @param frames the paused thread's call stack, top frame first
     */
    public DebugPausedEvent(Object source, DebugLocation location, List<DebugFrame> frames)
    {
        super(source);
        this.location = location;
        this.frames = frames;
    }
}
