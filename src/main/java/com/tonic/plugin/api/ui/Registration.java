package com.tonic.plugin.api.ui;

/**
 * A handle to something a plugin contributed to the UI (a tab, a menu item, an event subscription, a thread).
 * Calling {@link #remove()} undoes the contribution. Implementations must be idempotent: calling it more than
 * once (e.g. the plugin removes it itself and the host later removes it again on unload) is a no-op after the
 * first call.
 */
@FunctionalInterface
public interface Registration {

    /** Undoes the contribution. Idempotent. */
    void remove();
}
