package com.tonic.cli.engine;

import com.tonic.plugin.api.Plugin;
import com.tonic.plugin.api.AnalyzerPlugin;
import com.tonic.plugin.api.TransformerPlugin;
import com.tonic.plugin.context.PluginContextImpl;
import com.tonic.plugin.result.Finding;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.service.ProjectService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/** Runs one plugin over one loaded target for the command line and turns the outcome into an execution result. */
public class ExecutionEngine
{

    private final PluginLoader pluginLoader;

    /** Creates an engine with its own plugin loader. */
    public ExecutionEngine()
    {
        this.pluginLoader = new PluginLoader();
    }

    /**
     * Loads the target and plugin, runs the plugin (or only initializes it on a dry run), exports classes when an export directory is set, and reports the outcome; a loaded plugin is always disposed.
     *
     * @param config what to load, run and export
     * @return a success result with counts and findings, or a failure result carrying the error message; never throws
     */
    public ExecutionResult execute(ExecutionConfig config)
    {
        long startTime = System.currentTimeMillis();
        Plugin plugin = null;

        try
        {
            ProjectModel project = loadTarget(config);
            if (project == null)
            {
                return ExecutionResult.failure("Failed to load target: " + config.getTarget());
            }

            plugin = loadPlugin(config);
            if (plugin == null)
            {
                return ExecutionResult.failure("Failed to load plugin: " + config.getPlugin());
            }

            String pluginName = plugin.getInfo() != null ? plugin.getInfo().getName() : "plugin";
            String pluginId = plugin.getInfo() != null ? plugin.getInfo().getId() : "plugin";
            PluginContextImpl context = new PluginContextImpl(project, pluginName, pluginId);
            context.setExportDir(config.getExportDir());
            plugin.init(context);

            if (config.isDryRun())
            {
                return ExecutionResult.success(project.getClassCount(), 0, System.currentTimeMillis() - startTime, "Dry run - no changes made", context.getResults().getFindings());
            }

            plugin.execute();

            if (config.getExportDir() != null)
            {
                exportClasses(project, config.getExportDir());
            }

            long duration = System.currentTimeMillis() - startTime;
            List<Finding> findings = context.getResults().getFindings();

            return ExecutionResult.success(project.getClassCount(), countMethods(project), duration, buildSummary(plugin, findings), findings);
        }
        catch (Exception e)
        {
            return ExecutionResult.failure(e.getMessage());
        }
        finally
        {
            if (plugin != null)
            {
                try
                {
                    plugin.dispose();
                }
                catch (RuntimeException e)
                {
                    System.err.println("Plugin dispose failed: " + e.getMessage());
                }
            }
        }
    }

    private ProjectModel loadTarget(ExecutionConfig config)
    {
        try
        {
            File target = config.getTarget();
            ProjectService service = ProjectService.getInstance();
            String name = target.getName().toLowerCase();

            ProjectModel project;
            if (target.isDirectory())
            {
                project = service.readDirectory(target, null);
            }
            else if (name.endsWith(".jar") || name.endsWith(".zip"))
            {
                project = service.readJar(target, null);
            }
            else if (name.endsWith(".class"))
            {
                project = service.readClassFile(target);
            }
            else
            {
                return null;
            }
            if (config.isPublishProject())
            {
                service.makeCurrent(project, "Loaded " + target.getName());
            }
            return project;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private Plugin loadPlugin(ExecutionConfig config)
    {
        if (config.getPlugin() != null)
        {
            return pluginLoader.load(config.getPlugin());
        }
        if (config.getPluginDir() != null)
        {
            return pluginLoader.loadFromDirectory(config.getPluginDir());
        }
        return null;
    }

    private int countMethods(ProjectModel project)
    {
        int count = 0;
        for (ClassEntryModel entry : project.getUserClasses())
        {
            count += entry.getMethods().size();
        }
        return count;
    }

    private String buildSummary(Plugin plugin, List<Finding> findings)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Plugin: ").append(plugin.getInfo().getName()).append("\n");
        sb.append("Findings: ").append(findings.size());

        if (plugin instanceof AnalyzerPlugin)
        {
            sb.append(" (analysis complete)");
        }
        else if (plugin instanceof TransformerPlugin)
        {
            sb.append(" (transformation complete)");
        }

        return sb.toString();
    }

    private void exportClasses(ProjectModel project, File exportDir) throws Exception
    {
        if (!exportDir.exists() && !exportDir.mkdirs())
        {
            throw new IOException("Failed to create export directory: " + exportDir);
        }

        for (ClassEntryModel classEntry : project.getUserClasses())
        {
            String className = classEntry.getClassName();
            int lastSlash = className.lastIndexOf('/');

            File targetDir = exportDir;
            if (lastSlash > 0)
            {
                String packageDir = className.substring(0, lastSlash);
                targetDir = new File(exportDir, packageDir);
                if (!targetDir.mkdirs() && !targetDir.exists())
                {
                    throw new IOException("Failed to create package directory: " + targetDir);
                }
            }

            String simpleName = lastSlash > 0 ? className.substring(lastSlash + 1) : className;
            File outputFile = new File(targetDir, simpleName + ".class");

            try (FileOutputStream fos = new FileOutputStream(outputFile))
            {
                byte[] data = classEntry.getClassFile().write();
                fos.write(data);
            }
        }
    }
}
