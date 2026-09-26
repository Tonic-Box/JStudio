package com.tonic.live.protocol;

import lombok.Getter;

/** An asynchronous event from the agent: a runtime class load, or a VM death the client synthesizes when the connection drops. */
@Getter
public final class LiveEvent
{
    /** The kinds of live event. */
    public enum Kind
    {VM_DEATH, CLASS_LOADED}

    private final Kind kind;
    /** The loaded class's internal name for a class-loaded event; empty otherwise. */
    private final String className;
    /** The loaded class's bytes for a class-loaded event; null otherwise. */
    private final byte[] classBytes;

    private LiveEvent(Kind kind, String className, byte[] classBytes)
    {
        this.kind = kind;
        this.className = className;
        this.classBytes = classBytes;
    }

    /**
     * Creates the event for a dropped connection.
     *
     * @return a VM death event
     */
    public static LiveEvent vmDeath()
    {
        return new LiveEvent(Kind.VM_DEATH, "", null);
    }

    /**
     * Creates the event for a runtime class load.
     *
     * @param internalName the loaded class's internal name
     * @param classBytes the class file bytes as loaded
     * @return a class-loaded event
     */
    public static LiveEvent classLoaded(String internalName, byte[] classBytes)
    {
        return new LiveEvent(Kind.CLASS_LOADED, internalName, classBytes);
    }

    @Override
    public String toString()
    {
        if (kind == Kind.VM_DEATH)
        {
            return "VM_DEATH";
        }
        return "CLASS_LOADED " + className + " (" + (classBytes == null ? 0 : classBytes.length) + " bytes)";
    }
}
