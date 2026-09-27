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

/** The PluginContext for resident GUI plugins, whose project-bound APIs follow the currently open project, or an empty project when none is open, so they never go stale or throw; the analysis API is kept per project so registered patterns and built graphs survive between calls. */
public class LiveGuiPluginContext implements PluginContext
{

    private final ConsolePluginLogger logger;
    private final MapPluginConfig config;
    private final ResultCollector results;
    private final Map<String, Object> environment = new ConcurrentHashMap<>();
    private File exportDir;
    private ProjectModel emptyProject;
    private AnalysisApiImpl analysis;
    private ProjectModel analysisProject;

    /**
     * Creates a context with its own logger, empty configuration and results.
     *
     * @param pluginName the name that prefixes log lines
     * @param pluginId the id stamped on the plugin's findings
     */
    public LiveGuiPluginContext(String pluginName, String pluginId)
    {
        this.logger = new ConsolePluginLogger(pluginName);
        this.config = new MapPluginConfig();
        this.results = new ResultCollector(pluginId);
    }

    private ProjectModel project()
    {
        ProjectModel current = ProjectService.getInstance().getCurrentProject();
        if (current != null)
        {
            return current;
        }
        if (emptyProject == null)
        {
            emptyProject = new ProjectModel();
        }
        return emptyProject;
    }

    @Override
    public PluginLogger getLogger()
    {
        return logger;
    }

    @Override
    public PluginConfig getConfig()
    {
        return config;
    }

    @Override
    public ProjectApi getProject()
    {
        return new ProjectApiImpl(project());
    }

    @Override
    public synchronized AnalysisApi getAnalysis()
    {
        ProjectModel current = project();
        if (analysis == null || analysisProject != current)
        {
            analysis = new AnalysisApiImpl(current);
            analysisProject = current;
        }
        return analysis;
    }

    @Override
    public YabrAccess getYabr()
    {
        return new YabrAccessImpl(project());
    }

    @Override
    public VmDebugApi getVmDebug()
    {
        return new VmDebugApiImpl();
    }

    @Override
    public LiveApi getLive()
    {
        return new LiveApiImpl();
    }

    @Override
    public ScriptApi getScript()
    {
        return new ScriptApiImpl();
    }

    @Override
    public RefactorApi getRefactor()
    {
        return new RefactorApiImpl(ProjectService.getInstance()::getCurrentProject);
    }

    @Override
    public ResultCollector getResults()
    {
        return results;
    }

    @Override
    public Optional<Object> getService(String name)
    {
        return Optional.empty();
    }

    @Override
    public Map<String, Object> getEnvironment()
    {
        return new HashMap<>(environment);
    }

    @Override
    public void setEnvironmentValue(String key, Object value)
    {
        environment.put(key, value);
    }

    @Override
    public File getExportDir()
    {
        return exportDir;
    }

    /**
     * Sets the directory getExportDir reports.
     *
     * @param exportDir the output directory, or null for none
     */
    public void setExportDir(File exportDir)
    {
        this.exportDir = exportDir;
    }
}
