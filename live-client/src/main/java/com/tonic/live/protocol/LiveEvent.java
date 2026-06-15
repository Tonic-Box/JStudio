package com.tonic.live.protocol;

import lombok.Getter;

/**
 * An asynchronous event pushed by the agent. With the pure-Java agent the only streamed event is a
 * runtime class load ({@link Kind#CLASS_LOADED}); {@link Kind#VM_DEATH} is synthesized client-side when
 * the connection drops.
 */
@Getter
public final class LiveEvent {
    public enum Kind { VM_DEATH, CLASS_LOADED }

    private final Kind kind;
    /**
     * -- GETTER --
     * Loaded class internal name for
     * ; "" otherwise.
     */
    private final String className;
    /**
     * -- GETTER --
     * Captured class bytes for
     *  events;
     *  otherwise.
     */
    private final byte[] classBytes;

    private LiveEvent(Kind kind, String className, byte[] classBytes) {
        this.kind = kind;
        this.className = className;
        this.classBytes = classBytes;
    }

    public static LiveEvent vmDeath() {
        return new LiveEvent(Kind.VM_DEATH, "", null);
    }

    /** A runtime class-load capture: {@code internalName} loaded with its real bytes. */
    public static LiveEvent classLoaded(String internalName, byte[] classBytes) {
        return new LiveEvent(Kind.CLASS_LOADED, internalName, classBytes);
    }

    @Override
    public String toString() {
        if (kind == Kind.VM_DEATH) {
            return "VM_DEATH";
        }
        return "CLASS_LOADED " + className + " (" + (classBytes == null ? 0 : classBytes.length) + " bytes)";
    }
}
