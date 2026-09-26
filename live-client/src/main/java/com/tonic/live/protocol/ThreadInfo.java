package com.tonic.live.protocol;

import lombok.Getter;

/** A live thread: id, name, and state ordinal (java.lang.Thread.State). */

@Getter
public final class ThreadInfo
{
    private final long id;
    private final String name;
    private final int state;

    /**
     * Creates a thread entry.
     *
     * @param id the thread id
     * @param name the thread name
     * @param state the Thread.State ordinal
     */
    public ThreadInfo(long id, String name, int state)
    {
        this.id = id;
        this.name = name;
        this.state = state;
    }

    @Override
    public String toString()
    {
        return name + " (#" + id + ")";
    }
}
