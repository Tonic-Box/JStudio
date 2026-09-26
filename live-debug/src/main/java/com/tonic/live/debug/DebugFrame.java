package com.tonic.live.debug;

import lombok.Getter;

/** One call-stack frame as display data, identified by its index from the top because JDI frames are invalidated when the target resumes. */
@Getter
public final class DebugFrame
{
    private final int index;
    private final DebugLocation location;
    private final String display;

    /**
     * Creates a frame.
     *
     * @param index the frame's depth, 0 for the top
     * @param location where the frame is executing
     * @param display the frame's display text
     */
    public DebugFrame(int index, DebugLocation location, String display)
    {
        this.index = index;
        this.location = location;
        this.display = display;
    }
}
