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
}
