package com.tonic.event.events;

import com.tonic.event.Event;
import com.tonic.live.debug.DebugFrame;
import com.tonic.live.debug.DebugLocation;
import lombok.Getter;

import java.util.List;

/**
 * Posted (on the EDT) when the target suspends at a breakpoint: carries the top location to navigate/highlight
 * and the paused thread's call stack for the Debugger tool window.
 */
@Getter
public class DebugPausedEvent extends Event {

    private final DebugLocation location;
    private final List<DebugFrame> frames;

    public DebugPausedEvent(Object source, DebugLocation location, List<DebugFrame> frames) {
        super(source);
        this.location = location;
        this.frames = frames;
    }
}
