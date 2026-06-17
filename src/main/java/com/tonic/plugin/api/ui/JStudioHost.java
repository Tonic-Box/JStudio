package com.tonic.plugin.api.ui;

import com.tonic.event.Event;
import com.tonic.event.EventBus;
import com.tonic.model.ProjectModel;
import com.tonic.plugin.api.PluginContext;
import com.tonic.plugin.api.PluginInfo;
import com.tonic.plugin.api.PluginLogger;

import javax.swing.JFrame;
import java.util.function.Consumer;

/**
 * The handle a {@link UiPlugin} receives in {@link UiPlugin#start(JStudioHost)}. It is the curated entry point to
 * the running application: the UI contribution surface ({@link #ui()}), the live analysis context
 * ({@link #context()}), the current project, the event bus, and the main window.
 * <p>
 * This interface is intentionally small. Plugins have full, direct access to every app singleton
 * ({@code ProjectService}, {@code EventBus}, {@code JStudioTheme}, {@code ThemeManager}, {@code Settings}, the
 * YABR {@code ClassPool}, etc.) simply by importing them - the parent class loader makes them resolvable. The
 * host exists only to expose the things a plugin can't otherwise reach (UI contribution) and to offer leak-safe
 * conveniences ({@link #onEvent}, {@link #track}).
 */
public interface JStudioHost {

    /** Live analysis context (logger, config, project/analysis/YABR access, results) for this plugin. */
    PluginContext context();

    /** The UI contribution surface: add tool windows, views, menu items, toolbar buttons, navigator actions. */
    UiApi ui();

    /** The application event bus ({@code EventBus.getInstance()}). Prefer {@link #onEvent} for auto-cleanup. */
    EventBus events();

    /** The main application window - use as a dialog owner. */
    JFrame frame();

    /** The currently loaded project, or {@code null} if none is open. The authoritative nullable source of truth. */
    ProjectModel currentProject();

    /** This plugin's metadata. */
    PluginInfo info();

    /** This plugin's logger. */
    PluginLogger log();

    /**
     * Subscribes to an event bus type. The subscription is unregistered automatically when the plugin is
     * disabled/unloaded, so plugins need not track it themselves.
     */
    <T extends Event> void onEvent(Class<T> type, Consumer<T> handler);

    /**
     * Hands the host a cleanup action to run on unload (in addition to the {@link Registration}s returned by
     * {@link #ui()}). Use it for things the host can't see: background threads, timers, extra windows, raw listeners.
     */
    void track(Registration cleanup);
}
