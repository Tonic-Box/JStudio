package com.tonic.cli.engine;

import com.tonic.plugin.api.Plugin;
import com.tonic.plugin.api.PluginContext;
import com.tonic.plugin.api.PluginInfo;
import com.tonic.plugin.loader.JarPluginScanner;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class PluginLoader {

    public Plugin load(File file) {
        String name = file.getName().toLowerCase();

        if (name.endsWith(".jar")) {
            return loadJarPlugin(file);
        } else if (name.endsWith(".groovy")) {
            return loadGroovyScript(file);
        } else if (name.endsWith(".java")) {
            return loadJavaSource(file);
        }

        throw new IllegalArgumentException("Unsupported plugin format: " + name);
    }

    public Plugin loadFromDirectory(File dir) {
        List<Plugin> plugins = new ArrayList<>();

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                try {
                    if (file.isFile()) {
                        Plugin plugin = load(file);
                        if (plugin != null) {
                            plugins.add(plugin);
                        }
                    }
                } catch (Exception e) {
                    // Skip invalid files
                }
            }
        }

        if (plugins.isEmpty()) {
            return null;
        }
        if (plugins.size() == 1) {
            return plugins.get(0);
        }

        return new CompositePlugin(plugins);
    }

    private Plugin loadJarPlugin(File jarFile) {
        // The loader must outlive this method (the plugin loads its classes lazily through it), so ownership is
        // handed to the returned JarPlugin, which closes it on dispose. On any non-success path we close it here.
        JarPluginScanner.ScanResult result = JarPluginScanner.scan(jarFile, getClass().getClassLoader());
        if (result.plugins.isEmpty()) {
            JarPluginScanner.closeQuietly(result.loader);
            throw new RuntimeException("No plugin found in JAR: " + jarFile);
        }
        return new JarPlugin(result.plugins.get(0), result.loader);
    }

    private Plugin loadGroovyScript(File scriptFile) {
        try {
            String scriptContent = new String(Files.readAllBytes(scriptFile.toPath()));

            Binding binding = new Binding();
            GroovyShell shell = new GroovyShell(binding);
            Script script = shell.parse(scriptContent);

            return new GroovyScriptPlugin(scriptFile.getName(), script, binding);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Groovy script: " + e.getMessage(), e);
        }
    }

    private Plugin loadJavaSource(File sourceFile) {
        throw new UnsupportedOperationException("Java source compilation not yet implemented. Use pre-compiled JARs.");
    }

    private static class GroovyScriptPlugin implements Plugin {
        private final String name;
        private final Script script;
        private final Binding binding;

        GroovyScriptPlugin(String name, Script script, Binding binding) {
            this.name = name;
            this.script = script;
            this.binding = binding;
        }

        @Override
        public PluginInfo getInfo() {
            return PluginInfo.builder()
                .id(name)
                .name(name)
                .version("1.0")
                .description("Groovy script plugin")
                .build();
        }

        @Override
        public void init(PluginContext context) {
            binding.setVariable("context", context);
            binding.setVariable("project", context.getProject());
            binding.setVariable("analysis", context.getAnalysis());
            binding.setVariable("yabr", context.getYabr());
            binding.setVariable("results", context.getResults());
            binding.setVariable("log", context.getLogger());
            binding.setVariable("config", context.getConfig());
            binding.setVariable("exportDir", context.getExportDir());
        }

        @Override
        public void execute() {
            script.run();
        }

    }

    private static class CompositePlugin implements Plugin {
        private final List<Plugin> plugins;

        CompositePlugin(List<Plugin> plugins) {
            this.plugins = plugins;
        }

        @Override
        public PluginInfo getInfo() {
            return PluginInfo.builder()
                .id("composite")
                .name("Composite Plugin")
                .version("1.0")
                .description("Multiple plugins")
                .build();
        }

        @Override
        public void init(PluginContext context) {
            for (Plugin plugin : plugins) {
                plugin.init(context);
            }
        }

        @Override
        public void execute() {
            for (Plugin plugin : plugins) {
                plugin.execute();
            }
        }

        @Override
        public void dispose() {
            for (Plugin plugin : plugins) {
                plugin.dispose();
            }
        }
    }

    /** Wraps a JAR-loaded plugin, owning its {@link URLClassLoader} so it is closed when the plugin is disposed. */
    private static class JarPlugin implements Plugin {
        private final Plugin delegate;
        private final URLClassLoader loader;

        JarPlugin(Plugin delegate, URLClassLoader loader) {
            this.delegate = delegate;
            this.loader = loader;
        }

        @Override
        public PluginInfo getInfo() {
            return delegate.getInfo();
        }

        @Override
        public void init(PluginContext context) {
            delegate.init(context);
        }

        @Override
        public void execute() {
            delegate.execute();
        }

        @Override
        public void dispose() {
            try {
                delegate.dispose();
            } finally {
                JarPluginScanner.closeQuietly(loader);
            }
        }
    }
}
