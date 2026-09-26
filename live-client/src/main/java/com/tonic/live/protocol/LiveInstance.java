package com.tonic.live.protocol;

import lombok.Getter;

/** A live instance of a class in the target JVM, identified by a handle the agent holds as a weak reference. */
@Getter
public final class LiveInstance
{
    private final long handleId;
    private final String label;

    /**
     * Creates an instance entry.
     *
     * @param handleId the agent's handle for the object
     * @param label the object's display label
     */
    public LiveInstance(long handleId, String label)
    {
        this.handleId = handleId;
        this.label = label;
    }
}
