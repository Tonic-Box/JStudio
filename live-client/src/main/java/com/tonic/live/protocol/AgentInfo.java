package com.tonic.live.protocol;

import lombok.Getter;

/** Result of the HELLO handshake: the agent's version marker, capability bits, and loaded-class count. */
@Getter
public final class AgentInfo {
    private final int version;
    private final int capabilities;
    private final int loadedClassCount;

    public AgentInfo(int version, int capabilities, int loadedClassCount) {
        this.version = version;
        this.capabilities = capabilities;
        this.loadedClassCount = loadedClassCount;
    }

}
