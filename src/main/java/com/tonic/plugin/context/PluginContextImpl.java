package com.tonic.plugin.context;

import com.tonic.plugin.api.*;
import com.tonic.plugin.result.ResultCollector;
import com.tonic.ui.model.ProjectModel;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class PluginContextImpl implements PluginContext {

    private final ProjectModel projectModel;
    private final ConsolePluginLogger logger;
    private final MapPluginConfig config;
    private final ProjectApiImpl projectApi;
    private final AnalysisApiImpl analysisApi;
    private final YabrAccessImpl yabrAccess;
    private final ResultCollector results;
    private final Map<String, Object> services = new ConcurrentHashMap<>();
    private final Map<String, Object> environment = new ConcurrentHashMap<>();

    public PluginContextImpl(ProjectModel projectModel, String pluginName) {
        this.projectModel = projectModel;
        this.logger = new ConsolePluginLogger(pluginName);
        this.config = new MapPluginConfig();
        this.projectApi = new ProjectApiImpl(projectModel);
        this.analysisApi = new AnalysisApiImpl(projectModel);
        this.yabrAccess = new YabrAccessImpl(projectModel);
        this.results = new ResultCollector(pluginName);
    }

    public PluginContextImpl(ProjectModel projectModel, String pluginName,
                             Map<String, String> configProperties) {
        this(projectModel, pluginName);
        this.config.putAll(configProperties);
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
        return projectApi;
    }

    @Override
    public AnalysisApi getAnalysis() {
        return analysisApi;
    }

    @Override
    public YabrAccess getYabr() {
        return yabrAccess;
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

    public ConsolePluginLogger getLoggerImpl() {
        return logger;
    }

    public MapPluginConfig getConfigImpl() {
        return config;
    }
}
