package com.tonic.cli.engine;

import com.tonic.cli.output.OutputFormat;
import lombok.Builder;
import lombok.Getter;

import java.io.File;

/** The settings for one headless run: target, plugin source, output, class and method filters, and run flags; built with its builder. */
@Getter
@Builder
public class ExecutionConfig
{

    private final File target;
    private final File plugin;
    private final File pluginDir;
    private final File outputFile;
    @Builder.Default
    private final OutputFormat outputFormat = OutputFormat.TEXT;
    private final String classPattern;
    private final String methodPattern;
    private final boolean verbose;
    private final boolean quiet;
    private final boolean dryRun;
    private final File exportDir;
    /** Whether the loaded target becomes the application's current project, which the VM debugging API needs; false keeps parallel runs from sharing it. */
    @Builder.Default
    private final boolean publishProject = true;
}
