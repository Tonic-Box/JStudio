package com.tonic.live.debug;

import java.util.List;

/**
 * Callbacks from the JDI event pump. These are invoked off the EDT (on the session's event thread), so an
 * implementation that touches Swing must marshal to the UI thread.
 */
public interface DebugListener {
    /** The target suspended at {@code location}; {@code frames} is the paused thread's call stack (top first). */
    void onPaused(DebugLocation location, List<DebugFrame> frames);

    /** The target resumed and is no longer suspended. */
    void onResumed();

    /** The debug connection ended (target died, disconnected, or the session was disposed). */
    void onDisconnected();

    /**
     * A class that has a pending breakpoint has just been prepared in the target (its methods have not run yet).
     * The hook for injecting a synthetic LocalVariableTable into a stripped class before it executes. No-op by
     * default. Invoked on the event thread while the prepared thread is suspended.
     */
    default void onClassPrepared(String className) {
    }
}
