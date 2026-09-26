package com.tonic.live.debug;

import java.util.List;

/** Callbacks from the JDI event pump, invoked on the session's event thread rather than the EDT. */
public interface DebugListener
{
    /**
     * Reports that the target suspended at a breakpoint.
     *
     * @param location where the paused thread stopped, or null when it cannot be read
     * @param frames the paused thread's call stack, top first
     */
    void onPaused(DebugLocation location, List<DebugFrame> frames);

    /** Reports that the target resumed. */
    void onResumed();

    /** Reports that the debug connection ended, because the target died, disconnected, or the session was disposed. */
    void onDisconnected();

    /**
     * Reports that a class with a pending breakpoint was just prepared, before its methods run, while its thread is suspended; a no-op by default.
     *
     * @param className the prepared class's binary name
     */
    default void onClassPrepared(String className)
    {
    }
}
