package com.tonic.plugin.api;

import com.tonic.plugin.result.ResultCollector;

import java.io.File;
import java.util.Map;
import java.util.Optional;

public interface PluginContext {

    PluginLogger getLogger();

    PluginConfig getConfig();

    ProjectApi getProject();

    AnalysisApi getAnalysis();

    YabrAccess getYabr();

    VmDebugApi getVmDebug();

    LiveApi getLive();

    ResultCollector getResults();

    Optional<Object> getService(String name);

    Map<String, Object> getEnvironment();

    void setEnvironmentValue(String key, Object value);

    File getExportDir();
}
