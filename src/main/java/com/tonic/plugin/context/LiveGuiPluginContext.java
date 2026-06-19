package com.tonic.plugin.context;

import com.tonic.model.ProjectModel;
import com.tonic.plugin.api.AnalysisApi;
import com.tonic.plugin.api.LiveApi;
import com.tonic.plugin.api.PluginConfig;
import com.tonic.plugin.api.PluginContext;
import com.tonic.plugin.api.PluginLogger;
import com.tonic.plugin.api.ProjectApi;
import com.tonic.plugin.api.RefactorApi;
import com.tonic.plugin.api.ScriptApi;
import com.tonic.plugin.api.VmDebugApi;
import com.tonic.plugin.api.YabrAccess;
import com.tonic.plugin.result.ResultCollector;
import com.tonic.service.ProjectService;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link PluginContext} for resident GUI plugins. Unlike {@link PluginContextImpl}, which binds one fixed
 * {@link ProjectModel}, this resolves the project <em>live</em> on every access: the desktop's current project
 * changes as the user opens/closes files. The project-bound APIs ({@link #getProject()}/{@link #getAnalysis()}/
 * {@link #getYabr()}) are constructed on demand from {@link ProjectService}'s current project, so there is nothing
 * to invalidate and no staleness window.
 * <p>
 * When no project is loaded the getters operate on an empty sentinel model rather than throwing, so a
 * background-polling plugin never sees an exception from a getter. {@code host.currentProject()} (nullable) is the
 * authoritative way to check whether a project is actually open. Per-plugin state (logger, config, results,
 * services, environment) is stable for the plugin's lifetime.
 */
public class LiveGuiPluginContext implements PluginContext {

    private final ConsolePluginLogger logger;
    private final MapPluginConfig config;
    private final ResultCollector results;
    private final Map<String, Object> services = new ConcurrentHashMap<>();
    private final Map<String, Object> environment = new ConcurrentHashMap<>();
    private File exportDir;
    private ProjectModel emptyProject;

    public LiveGuiPluginContext(String pluginName) {
        this.logger = new ConsolePluginLogger(pluginName);
        this.config = new MapPluginConfig();
        this.results = new ResultCollector(pluginName);
    }

    /** The live current project, or a shared empty sentinel when none is loaded (so the impls never see null). */
    private ProjectModel project() {
        ProjectModel current = ProjectService.getInstance().getCurrentProject();
        if (current != null) {
            return current;
        }
        if (emptyProject == null) {
            emptyProject = new ProjectModel();
        }
        return emptyProject;
    }

    @Override
    public PluginLogger getLogger() {
        return logger;
    }

    @Override
    public PluginConfig getConfig() {
        return config;
    }

    @Override
    public ProjectApi getProject() {
        return new ProjectApiImpl(project());
    }

    @Override
    public AnalysisApi getAnalysis() {
        return new AnalysisApiImpl(project());
    }

    @Override
    public YabrAccess getYabr() {
        return new YabrAccessImpl(project());
    }

    @Override
    public VmDebugApi getVmDebug() {
        return new VmDebugApiImpl();
    }

    @Override
    public LiveApi getLive() {
        return new LiveApiImpl();
    }

    @Override
    public ScriptApi getScript() {
        return new ScriptApiImpl();
    }

    @Override
    public RefactorApi getRefactor() {
        return new RefactorApiImpl();
    }

    @Override
    public ResultCollector getResults() {
        return results;
    }

    @Override
    public Optional<Object> getService(String name) {
        return Optional.ofNullable(services.get(name));
    }

    @Override
    public Map<String, Object> getEnvironment() {
        return new HashMap<>(environment);
    }

    @Override
    public void setEnvironmentValue(String key, Object value) {
        environment.put(key, value);
    }

    public void registerService(String name, Object service) {
        services.put(name, service);
    }

    public void setDebugEnabled(boolean enabled) {
        logger.setDebugEnabled(enabled);
    }

    @Override
    public File getExportDir() {
        return exportDir;
    }

    public void setExportDir(File exportDir) {
        this.exportDir = exportDir;
    }
}
