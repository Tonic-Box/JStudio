package com.tonic.cli.engine;

import com.tonic.plugin.api.Plugin;
import com.tonic.plugin.api.AnalyzerPlugin;
import com.tonic.plugin.api.TransformerPlugin;
import com.tonic.plugin.context.PluginContextImpl;
import com.tonic.plugin.result.Finding;
import com.tonic.plugin.result.ResultCollector;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.service.ProjectService;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class ExecutionEngine {

    private final PluginLoader pluginLoader;

    public ExecutionEngine() {
        this.pluginLoader = new PluginLoader();
    }

    public ExecutionResult execute(ExecutionConfig config) {
        long startTime = System.currentTimeMillis();

        try {
            ProjectModel project = loadTarget(config);
            if (project == null) {
                return ExecutionResult.failure("Failed to load target: " + config.getTarget());
            }

            Plugin plugin = loadPlugin(config);
            if (plugin == null) {
                return ExecutionResult.failure("Failed to load plugin: " + config.getPlugin());
            }

            String pluginName = plugin.getInfo() != null ? plugin.getInfo().getName() : "plugin";
            PluginContextImpl context = new PluginContextImpl(project, pluginName);
            plugin.init(context);

            if (config.isDryRun()) {
                return ExecutionResult.success(
                    project.getClassCount(), 0,
                    System.currentTimeMillis() - startTime,
                    "Dry run - no changes made",
                    context.getResults().getFindings()
                );
            }

            plugin.execute();

            if (config.getExportDir() != null && plugin instanceof TransformerPlugin) {
                exportClasses(project, config.getExportDir());
            }

            plugin.dispose();

            long duration = System.currentTimeMillis() - startTime;
            List<Finding> findings = context.getResults().getFindings();

            return ExecutionResult.success(
                project.getClassCount(),
                countMethods(project),
                duration,
                buildSummary(plugin, findings),
                findings
            );
        } catch (Exception e) {
            return ExecutionResult.failure(e.getMessage());
        }
    }

    private ProjectModel loadTarget(ExecutionConfig config) {
        try {
            File target = config.getTarget();
            ProjectService service = ProjectService.getInstance();
            String name = target.getName().toLowerCase();

            if (target.isDirectory()) {
                return service.loadDirectory(target, null);
            } else if (name.endsWith(".jar") || name.endsWith(".zip")) {
                return service.loadJar(target, null);
            } else if (name.endsWith(".class")) {
                return service.loadClassFile(target);
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Plugin loadPlugin(ExecutionConfig config) {
        if (config.getPlugin() != null) {
            return pluginLoader.load(config.getPlugin());
        }
        if (config.getPluginDir() != null) {
            return pluginLoader.loadFromDirectory(config.getPluginDir());
        }
        return null;
    }

    private int countMethods(ProjectModel project) {
        int count = 0;
        for (ClassEntryModel entry : project.getAllClasses()) {
            count += entry.getMethods().size();
        }
        return count;
    }

    private String buildSummary(Plugin plugin, List<Finding> findings) {
        StringBuilder sb = new StringBuilder();
        sb.append("Plugin: ").append(plugin.getInfo().getName()).append("\n");
        sb.append("Findings: ").append(findings.size());

        if (plugin instanceof AnalyzerPlugin) {
            sb.append(" (analysis complete)");
        } else if (plugin instanceof TransformerPlugin) {
            sb.append(" (transformation complete)");
        }

        return sb.toString();
    }

    private void exportClasses(ProjectModel project, File exportDir) throws Exception {
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }

        for (ClassEntryModel classEntry : project.getAllClasses()) {
            String className = classEntry.getClassName();
            int lastSlash = className.lastIndexOf('/');

            File targetDir = exportDir;
            if (lastSlash > 0) {
                String packageDir = className.substring(0, lastSlash);
                targetDir = new File(exportDir, packageDir);
                targetDir.mkdirs();
            }

            String simpleName = lastSlash > 0 ? className.substring(lastSlash + 1) : className;
            File outputFile = new File(targetDir, simpleName + ".class");

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                byte[] data = classEntry.getClassFile().write();
                fos.write(data);
            }
        }
    }
}
