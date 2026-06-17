package com.tonic.plugin.api.ui;

import com.tonic.plugin.api.Plugin;

/**
 * A plugin that contributes to the JStudio desktop UI. Discovered the same way as headless {@link Plugin}s
 * (annotated with {@code @JStudioPlugin}, loaded from {@code ~/.jstudio/plugins/*.jar}), but instead of the
 * one-shot {@link #execute()} contract it is given a {@link JStudioHost} and stays resident.
 * <p>
 * The host calls {@link #init(com.tonic.plugin.api.PluginContext)} then {@link #start(JStudioHost)} on the Swing
 * event dispatch thread once the main window exists. Register tool windows, views, menu items, etc. in
 * {@code start} via {@link JStudioHost#ui()}; each contribution returns a {@link Registration} the host tracks
 * and removes automatically when the plugin is disabled or the app exits.
 */
public interface UiPlugin extends Plugin {

    /** UI plugins are resident, not one-shot; the headless execute step is unused. */
    @Override
    default void execute() {
    }

    /**
     * Registers the plugin's UI contributions. Called on the EDT after the main window is realized. Do all Swing
     * work here (not in the constructor). Anything not expressed as a {@link Registration} - background threads,
     * extra windows, raw listeners - should be handed to {@link JStudioHost#track(Registration)} or torn down in
     * {@link #dispose()}.
     */
    void start(JStudioHost host);

    /**
     * Called on the EDT when the plugin is disabled/reloaded or the app exits, <em>after</em> the host has already
     * removed every tracked {@link Registration}. Stop anything the host can't see here (threads, timers, windows).
     */
    @Override
    default void dispose() {
    }
}
