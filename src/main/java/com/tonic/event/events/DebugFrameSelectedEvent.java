package com.tonic.event.events;

import com.tonic.event.Event;
import com.tonic.live.debug.DebugFrame;
import lombok.Getter;

/**
 * Posted (on the EDT) when the active debugger frame changes - on a pause (top frame) or when the user picks a
 * frame in the call stack. Source views use it to know which frame's runtime values to render inline.
 */
@Getter
public class DebugFrameSelectedEvent extends Event {

    private final DebugFrame frame;

    public DebugFrameSelectedEvent(Object source, DebugFrame frame) {
        super(source);
        this.frame = frame;
    }
}
