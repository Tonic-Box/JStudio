package com.tonic.cli.engine;

import com.tonic.cli.output.OutputFormat;

import java.io.File;

public class ExecutionConfig {

    private final File target;
    private final File plugin;
    private final File pluginDir;
    private final File outputFile;
    private final OutputFormat outputFormat;
    private final String classPattern;
    private final String methodPattern;
    private final boolean verbose;
    private final boolean quiet;
    private final boolean dryRun;
    private final File exportDir;

    private ExecutionConfig(Builder builder) {
        this.target = builder.target;
        this.plugin = builder.plugin;
        this.pluginDir = builder.pluginDir;
        this.outputFile = builder.outputFile;
        this.outputFormat = builder.outputFormat;
        this.classPattern = builder.classPattern;
        this.methodPattern = builder.methodPattern;
        this.verbose = builder.verbose;
        this.quiet = builder.quiet;
        this.dryRun = builder.dryRun;
        this.exportDir = builder.exportDir;
    }

    public File getTarget() { return target; }
    public File getPlugin() { return plugin; }
    public File getPluginDir() { return pluginDir; }
    public File getOutputFile() { return outputFile; }
    public OutputFormat getOutputFormat() { return outputFormat; }
    public String getClassPattern() { return classPattern; }
    public String getMethodPattern() { return methodPattern; }
    public boolean isVerbose() { return verbose; }
    public boolean isQuiet() { return quiet; }
    public boolean isDryRun() { return dryRun; }
    public File getExportDir() { return exportDir; }

    public static class Builder {
        private File target;
        private File plugin;
        private File pluginDir;
        private File outputFile;
        private OutputFormat outputFormat = OutputFormat.TEXT;
        private String classPattern;
        private String methodPattern;
        private boolean verbose;
        private boolean quiet;
        private boolean dryRun;
        private File exportDir;

        public Builder target(File target) { this.target = target; return this; }
        public Builder plugin(File plugin) { this.plugin = plugin; return this; }
        public Builder pluginDir(File pluginDir) { this.pluginDir = pluginDir; return this; }
        public Builder outputFile(File outputFile) { this.outputFile = outputFile; return this; }
        public Builder outputFormat(OutputFormat format) { this.outputFormat = format; return this; }
        public Builder classPattern(String pattern) { this.classPattern = pattern; return this; }
        public Builder methodPattern(String pattern) { this.methodPattern = pattern; return this; }
        public Builder verbose(boolean verbose) { this.verbose = verbose; return this; }
        public Builder quiet(boolean quiet) { this.quiet = quiet; return this; }
        public Builder dryRun(boolean dryRun) { this.dryRun = dryRun; return this; }
        public Builder exportDir(File exportDir) { this.exportDir = exportDir; return this; }

        public ExecutionConfig build() {
            return new ExecutionConfig(this);
        }
    }
}
