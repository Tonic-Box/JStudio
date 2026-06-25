package com.tonic.live.protocol;

import lombok.Getter;

/**
 * A live instance of a class in the target JVM, identified by an agent-held handle (a weak reference). Reads
 * and writes of its fields go to the real, live object via {@link LiveProtocol#MSG_INSTANCE_FIELDS} and
 * {@link LiveProtocol#MSG_SET_INSTANCE_FIELD}.
 */
@Getter
public final class LiveInstance {
    private final long handleId;
    private final String label;

    public LiveInstance(long handleId, String label) {
        this.handleId = handleId;
        this.label = label;
    }
}
