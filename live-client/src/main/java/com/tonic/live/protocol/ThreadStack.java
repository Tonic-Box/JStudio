package com.tonic.live.protocol;

import lombok.Getter;

import java.util.List;

/** A thread in the target JVM with a point-in-time stack: its id, name, Thread.State ordinal and frames. */
@Getter
public final class ThreadStack
{
    private final long id;
    private final String name;
    private final int state;
    private final List<StackFrame> frames;

    /**
     * Creates a thread stack.
     *
     * @param id the thread id
     * @param name the thread name
     * @param state the Thread.State ordinal
     * @param frames the stack frames, innermost first
     */
    public ThreadStack(long id, String name, int state, List<StackFrame> frames)
    {
        this.id = id;
        this.name = name;
        this.state = state;
        this.frames = frames;
    }

    /**
     * Maps the state ordinal to a thread state.
     *
     * @return the state, or RUNNABLE when the ordinal is out of range
     */
    public Thread.State getStateEnum()
    {
        Thread.State[] values = Thread.State.values();
        return state >= 0 && state < values.length ? values[state] : Thread.State.RUNNABLE;
    }
}
