package com.tonic.plugin.gui;

import com.tonic.plugin.api.Plugin;
import com.tonic.plugin.api.PluginInfo;
import com.tonic.plugin.api.ui.Registration;
import com.tonic.plugin.api.ui.UiPlugin;
import lombok.Getter;

import java.io.File;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * One plugin tracked by the {@link GuiPluginManager}: its metadata, the jar/class-loader it came from, its current
 * lifecycle state, and the list of UI contributions to undo when it is disabled or the app exits. EDT-confined.
 */
public final class LoadedPlugin {

    public enum State {
        /** Active: its contributions are live. */
        ENABLED,
        /** Loaded but not started (user-disabled, or a non-active sibling). */
        DISABLED,
        /** init/start threw; see {@link #error}. */
        ERROR,
        /** A non-UI {@code @JStudioPlugin} found in a GUI-dropped jar; inert here. */
        NOT_UI
    }

    @Getter
    final File jar;
    @Getter
    final PluginInfo info;
    /** Shared with sibling plugins from the same jar; null for scan-failure entries. */
    final URLClassLoader loader;
    /** The plugin instance; null for scan-failure entries. */
    final Plugin plugin;
    /** Removers for everything this plugin contributed during its active period. */
    final List<Registration> contributions = new ArrayList<>();
    JStudioHostImpl host;
    @Getter
    volatile State state;
    @Getter
    volatile Throwable error;

    LoadedPlugin(File jar, PluginInfo info, URLClassLoader loader, Plugin plugin, State state) {
        this.jar = jar;
        this.info = info;
        this.loader = loader;
        this.plugin = plugin;
        this.state = state;
    }

    boolean isUi() {
        return plugin instanceof UiPlugin;
    }

}
