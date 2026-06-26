package com.tonic.live.debug;

import lombok.Getter;

/**
 * One call-stack frame as display data. JDI {@code StackFrame}s are invalidated when the target resumes, so a
 * frame is identified by its {@link #index} (0 = top) and re-fetched from the paused thread when its variables
 * are read.
 */
@Getter
public final class DebugFrame {
    private final int index;
    private final DebugLocation location;
    private final String display;

    public DebugFrame(int index, DebugLocation location, String display) {
        this.index = index;
        this.location = location;
        this.display = display;
    }
}
