package com.tonic.plugin.api.ui;

import com.tonic.plugin.api.Plugin;

/** A plugin that stays resident in the desktop app: loaded from a jar in ~/.jstudio/plugins, it gets init and then start on the EDT once the main window exists, and dispose when disabled, reloaded or the app exits. */
public interface UiPlugin extends Plugin
{

    /** Does nothing; UI plugins stay resident and are never executed. */
    @Override
    default void execute()
    {
    }

    /**
     * Registers the plugin's UI contributions; called on the EDT after init. A throw marks the plugin as failed, removes what it had registered and calls dispose.
     *
     * @param host the plugin's handle to the app
     */
    void start(JStudioHost host);

    /** Stops what the host cannot see, such as threads, timers and windows; called on the EDT after the host has removed every tracked Registration. Does nothing by default. */
    @Override
    default void dispose()
    {
    }
}
