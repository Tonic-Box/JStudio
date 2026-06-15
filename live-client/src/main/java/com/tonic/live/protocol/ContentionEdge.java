package com.tonic.live.protocol;

import lombok.Getter;

/**
 * One edge of the live wait-for graph: a thread blocked entering a monitor, and the thread that currently
 * owns that monitor. A cycle among these edges is a deadlock (see {@link com.tonic.live.Deadlocks}).
 */
@Getter
public final class ContentionEdge {
    private final long threadId;
    private final String threadName;
    private final String monitorClass;
    private final long ownerThreadId;
    private final String ownerThreadName;

    public ContentionEdge(long threadId, String threadName, String monitorClass,
                          long ownerThreadId, String ownerThreadName) {
        this.threadId = threadId;
        this.threadName = threadName;
        this.monitorClass = monitorClass;
        this.ownerThreadId = ownerThreadId;
        this.ownerThreadName = ownerThreadName;
    }

    @Override
    public String toString() {
        return threadName + " (t#" + threadId + ") waits on " + monitorClass + " held by "
                + ownerThreadName + " (t#" + ownerThreadId + ")";
    }
}
