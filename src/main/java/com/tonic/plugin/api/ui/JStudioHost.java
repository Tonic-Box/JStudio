package com.tonic.plugin.api.ui;

import com.tonic.event.Event;
import com.tonic.event.EventBus;
import com.tonic.model.ProjectModel;
import com.tonic.plugin.api.PluginContext;
import com.tonic.plugin.api.PluginInfo;
import com.tonic.plugin.api.PluginLogger;

import javax.swing.JFrame;
import java.util.function.Consumer;

/** The handle a UiPlugin receives in start: the UI contribution surface, the live plugin context, the current project, the event bus and the main window, with cleanup tracked for unload. */
public interface JStudioHost
{

    /**
     * Returns this plugin's context, whose project-bound APIs follow the currently open project.
     *
     * @return the context, the same one passed to init
     */
    PluginContext context();

    /**
     * Returns the surface for adding tool windows, views, menu items, toolbar buttons and navigator actions.
     *
     * @return the UI API, the same one for the plugin's lifetime
     */
    UiApi ui();

    /**
     * Returns the application event bus; prefer onEvent, which unsubscribes on unload.
     *
     * @return the shared event bus
     */
    EventBus events();

    /**
     * Returns the main application window, for use as a dialog owner.
     *
     * @return the main window
     */
    JFrame frame();

    /**
     * Returns the project open right now, read afresh on each call; the authoritative check for whether a project is open.
     *
     * @return the current project, or null when none is open
     */
    ProjectModel currentProject();

    /**
     * Returns this plugin's metadata.
     *
     * @return the info from the plugin's getInfo
     */
    PluginInfo info();

    /**
     * Returns this plugin's logger.
     *
     * @return the logger of the plugin's context
     */
    PluginLogger log();

    /**
     * Subscribes to events of exactly the given class; the handler runs on the EDT and is unsubscribed automatically when the plugin is disabled, reloaded or the app exits.
     *
     * @param <T> the event type
     * @param type the event class, matched exactly, not by subclass
     * @param handler called with each event
     */
    <T extends Event> void onEvent(Class<T> type, Consumer<T> handler);

    /**
     * Hands the host a cleanup to run on unload, for things it cannot see such as threads, timers, extra windows and raw listeners; cleanups run in reverse order of registration, before dispose.
     *
     * @param cleanup the action to run once on unload
     */
    void track(Registration cleanup);
}
