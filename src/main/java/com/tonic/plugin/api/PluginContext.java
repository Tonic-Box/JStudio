package com.tonic.plugin.api;

import com.tonic.plugin.result.ResultCollector;

import java.io.File;
import java.util.Map;
import java.util.Optional;

/** What a plugin gets from JStudio: its logger, config and results plus the project, analysis, bytecode, VM, live and refactor APIs; in the GUI the project-bound APIs follow the currently open project. */
public interface PluginContext
{

    /**
     * Returns the plugin's logger.
     *
     * @return the logger, which writes to the console prefixed with the plugin name
     */
    PluginLogger getLogger();

    /**
     * Returns the plugin's configuration.
     *
     * @return the configuration; neither the CLI nor the GUI supplies values yet, so it is empty
     */
    PluginConfig getConfig();

    /**
     * Returns the project API; in the GUI it is built fresh on each call from the currently open project, or from an empty project when none is open, so do not cache it across project changes.
     *
     * @return the project API, never null
     */
    ProjectApi getProject();

    /**
     * Returns the analysis API; in the GUI it is built fresh on each call from the currently open project, or from an empty project when none is open.
     *
     * @return the analysis API, never null
     */
    AnalysisApi getAnalysis();

    /**
     * Returns direct access to the YABR bytecode model; in the GUI it is built fresh on each call from the currently open project, or from an empty project when none is open.
     *
     * @return the bytecode access, never null
     */
    YabrAccess getYabr();

    /**
     * Returns the bytecode VM debugging API.
     *
     * @return the API, never null; in the GUI a new instance on each call
     */
    VmDebugApi getVmDebug();

    /**
     * Returns the API for the attached live JVM.
     *
     * @return the API, never null; in the GUI a new instance on each call
     */
    LiveApi getLive();

    /**
     * Returns the script-running API.
     *
     * @return the API, never null; in the GUI a new instance on each call
     */
    ScriptApi getScript();

    /**
     * Returns the refactoring API.
     *
     * @return the API, never null; in the GUI a new instance on each call
     */
    RefactorApi getRefactor();

    /**
     * Returns the collector for this plugin's findings.
     *
     * @return the collector, the same one for the plugin's lifetime
     */
    ResultCollector getResults();

    /**
     * Looks up a named service; no services are registered yet, so this always returns empty.
     *
     * @param name the service name
     * @return the service, or empty when none is registered under the name
     */
    Optional<Object> getService(String name);

    /**
     * Returns the plugin's environment values.
     *
     * @return a copy; changes to it do not write back
     */
    Map<String, Object> getEnvironment();

    /**
     * Stores a value in the plugin's environment, replacing any earlier value for the key.
     *
     * @param key the entry's name, not null
     * @param value the value to store, not null
     */
    void setEnvironmentValue(String key, Object value);

    /**
     * Returns the directory the plugin may write output to.
     *
     * @return in the GUI the export folder under the plugin's directory in ~/.jstudio/plugins, which may not exist yet; in the CLI the export directory given on the command line, or null when none was given
     */
    File getExportDir();
}
