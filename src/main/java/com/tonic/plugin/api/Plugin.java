package com.tonic.plugin.api;

/** A JStudio plugin: the CLI calls init, then execute, then dispose, while the GUI calls init and dispose and runs only plugins that also implement UiPlugin. */
public interface Plugin
{

    /**
     * Returns this plugin's metadata; called before init, and a throw here marks the plugin as failed in the GUI.
     *
     * @return the plugin's id, name, version and description
     */
    PluginInfo getInfo();

    /**
     * Receives the context before any other lifecycle call; does nothing by default.
     *
     * @param context the plugin's access to the project, analysis, live and debug APIs, config, logging and results
     */
    default void init(PluginContext context)
    {
    }

    /** Runs the plugin's work; called by the CLI after init, never by the GUI. */
    void execute();

    /** Releases the plugin's resources; called last, and in the GUI also when the plugin is disabled or reloaded. Does nothing by default. */
    default void dispose()
    {
    }
}
