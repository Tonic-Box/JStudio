package com.tonic.cli.commands;

import com.tonic.cli.engine.ExecutionEngine;
import com.tonic.cli.engine.ExecutionConfig;
import com.tonic.cli.engine.ExecutionResult;
import com.tonic.cli.output.OutputFormat;
import com.tonic.cli.output.OutputHandler;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.concurrent.Callable;

@Command(
    name = "run",
    description = "Execute a plugin or script on target files",
    mixinStandardHelpOptions = true
)
public class RunCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Target JAR file, class file, or directory")
    private File target;

    @Option(names = {"-p", "--plugin"}, description = "Plugin or script file (.java, .groovy, .jar)")
    private File plugin;

    @Option(names = {"-d", "--plugin-dir"}, description = "Directory containing plugins")
    private File pluginDir;

    @Option(names = {"-o", "--output"}, description = "Output file path")
    private File output;

    @Option(names = {"-f", "--format"}, description = "Output format: text, json, csv", defaultValue = "text")
    private OutputFormat format;

    @Option(names = {"-c", "--class"}, description = "Target specific class (glob pattern)")
    private String classPattern;

    @Option(names = {"-m", "--method"}, description = "Target specific method pattern")
    private String methodPattern;

    @Option(names = {"-v", "--verbose"}, description = "Verbose output")
    private boolean verbose;

    @Option(names = {"-q", "--quiet"}, description = "Quiet mode")
    private boolean quiet;

    @Option(names = {"--dry-run"}, description = "Show what would be done without executing")
    private boolean dryRun;

    @Option(names = {"--export"}, description = "Export modified classes to directory")
    private File exportDir;

    @Override
    public Integer call() {
        if (target == null || !target.exists()) {
            System.err.println("Error: Target file not found: " + target);
            return 1;
        }

        if (plugin == null && pluginDir == null) {
            System.err.println("Error: Must specify --plugin or --plugin-dir");
            return 1;
        }

        ExecutionConfig config = ExecutionConfig.builder()
            .target(target)
            .plugin(plugin)
            .pluginDir(pluginDir)
            .outputFile(output)
            .outputFormat(format)
            .classPattern(classPattern)
            .methodPattern(methodPattern)
            .verbose(verbose)
            .quiet(quiet)
            .dryRun(dryRun)
            .exportDir(exportDir)
            .build();

        try {
            ExecutionEngine engine = new ExecutionEngine();
            ExecutionResult result = engine.execute(config);

            OutputHandler handler = OutputHandler.forFormat(format, output);
            handler.writeResult(result);

            if (!quiet) {
                System.out.println();
                System.out.println("Execution completed in " + result.getDurationMs() + "ms");
                System.out.println("Classes processed: " + result.getClassesProcessed());
                System.out.println("Findings: " + result.getFindingsCount());
            }

            return result.isSuccess() ? 0 : 1;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }
}
