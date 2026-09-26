package com.tonic.live.protocol;

import lombok.Getter;

/** One edge of the live wait-for graph: a thread blocked entering a monitor, and the thread that owns it. */
@Getter
public final class ContentionEdge
{
    private final long threadId;
    private final String threadName;
    private final String monitorClass;
    private final long ownerThreadId;
    private final String ownerThreadName;

    /**
     * Creates an edge.
     *
     * @param threadId the blocked thread's id
     * @param threadName the blocked thread's name
     * @param monitorClass the class of the monitor object
     * @param ownerThreadId the owning thread's id
     * @param ownerThreadName the owning thread's name
     */
    public ContentionEdge(long threadId, String threadName, String monitorClass, long ownerThreadId, String ownerThreadName)
    {
        this.threadId = threadId;
        this.threadName = threadName;
        this.monitorClass = monitorClass;
        this.ownerThreadId = ownerThreadId;
        this.ownerThreadName = ownerThreadName;
    }

    @Override
    public String toString()
    {
        return threadName + " (t#" + threadId + ") waits on " + monitorClass + " held by "
                + ownerThreadName + " (t#" + ownerThreadId + ")";
    }
}
