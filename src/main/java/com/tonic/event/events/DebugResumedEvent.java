package com.tonic.event.events;

import com.tonic.event.Event;

/** Posted (on the EDT) when the target resumes from a breakpoint, so the current-line highlight can clear. */
public class DebugResumedEvent extends Event
{

    /**
     * Creates the event.
     *
     * @param source the poster
     */
    public DebugResumedEvent(Object source)
    {
        super(source);
    }
}
