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

/** One plugin the GuiPluginManager tracks: its metadata, the jar and class loader it came from, its state, and the contributions to undo when it is torn down; used on the EDT. */
public final class LoadedPlugin
{

    /** Where a plugin is in its lifecycle. */
    public enum State
    {
        /** Started; its contributions are live. */
        ENABLED,
        /** Loaded but not started, because the user disabled it. */
        DISABLED,
        /** Loading, init or start threw; the error holds why. */
        ERROR,
        /** A plugin that does not implement UiPlugin; the GUI never runs it. */
        NOT_UI
    }

    @Getter
    final File jar;
    @Getter
    final PluginInfo info;
    final URLClassLoader loader;
    final Plugin plugin;
    final List<Registration> contributions = new ArrayList<>();
    JStudioHostImpl host;
    @Getter
    volatile State state;
    @Getter
    volatile Throwable error;

    LoadedPlugin(File jar, PluginInfo info, URLClassLoader loader, Plugin plugin, State state)
    {
        this.jar = jar;
        this.info = info;
        this.loader = loader;
        this.plugin = plugin;
        this.state = state;
    }

    boolean isUi()
    {
        return plugin instanceof UiPlugin;
    }

}
