package com.tonic.live.protocol;

import lombok.Getter;

import java.util.List;

/** A thread in the target JVM with a point-in-time stack: id, name, {@link Thread.State} ordinal, frames. */
@Getter
public final class ThreadStack {
    private final long id;
    private final String name;
    private final int state;
    private final List<StackFrame> frames;

    public ThreadStack(long id, String name, int state, List<StackFrame> frames) {
        this.id = id;
        this.name = name;
        this.state = state;
        this.frames = frames;
    }

    public Thread.State getStateEnum() {
        Thread.State[] values = Thread.State.values();
        return state >= 0 && state < values.length ? values[state] : Thread.State.RUNNABLE;
    }
}
