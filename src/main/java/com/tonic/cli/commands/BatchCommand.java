package com.tonic.cli.commands;

import com.tonic.cli.engine.ExecutionConfig;
import com.tonic.cli.engine.ExecutionEngine;
import com.tonic.cli.engine.ExecutionResult;
import com.tonic.cli.output.OutputFormat;
import com.tonic.cli.output.OutputHandler;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Command(
    name = "batch",
    description = "Batch process multiple targets with a plugin",
    mixinStandardHelpOptions = true
)
public class BatchCommand implements Callable<Integer> {

    @Parameters(description = "Target files or directories (supports glob patterns)")
    private List<File> targets;

    @Option(names = {"-p", "--plugin"}, required = true, description = "Plugin or script file")
    private File plugin;

    @Option(names = {"-o", "--output-dir"}, description = "Output directory for results")
    private File outputDir;

    @Option(names = {"-f", "--format"}, description = "Output format", defaultValue = "text")
    private OutputFormat format;

    @Option(names = {"--parallel"}, description = "Process targets in parallel")
    private boolean parallel;

    @Option(names = {"-j", "--jobs"}, description = "Number of parallel jobs", defaultValue = "4")
    private int jobs;

    @Option(names = {"-v", "--verbose"}, description = "Verbose output")
    private boolean verbose;

    @Option(names = {"-q", "--quiet"}, description = "Quiet mode")
    private boolean quiet;

    @Option(names = {"--continue-on-error"}, description = "Continue processing after errors")
    private boolean continueOnError;

    @Override
    public Integer call() {
        if (targets == null || targets.isEmpty()) {
            System.err.println("Error: No targets specified");
            return 1;
        }

        if (!plugin.exists()) {
            System.err.println("Error: Plugin not found: " + plugin);
            return 1;
        }

        if (outputDir != null && !outputDir.exists()) {
            outputDir.mkdirs();
        }

        int successCount = 0;
        int failCount = 0;
        long totalTime = 0;

        if (parallel && targets.size() > 1) {
            ExecutorService executor = Executors.newFixedThreadPool(Math.min(jobs, targets.size()));
            List<Future<ExecutionResult>> futures = new ArrayList<>();

            for (File target : targets) {
                futures.add(executor.submit(() -> processTarget(target)));
            }

            for (int i = 0; i < futures.size(); i++) {
                try {
                    ExecutionResult result = futures.get(i).get();
                    if (result.isSuccess()) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                    totalTime += result.getDurationMs();
                } catch (Exception e) {
                    failCount++;
                    if (!continueOnError) {
                        executor.shutdownNow();
                        break;
                    }
                }
            }

            executor.shutdown();
        } else {
            for (File target : targets) {
                try {
                    ExecutionResult result = processTarget(target);
                    if (result.isSuccess()) {
                        successCount++;
                    } else {
                        failCount++;
                        if (!continueOnError) break;
                    }
                    totalTime += result.getDurationMs();
                } catch (Exception e) {
                    failCount++;
                    if (!continueOnError) break;
                }
            }
        }

        if (!quiet) {
            System.out.println();
            System.out.println("Batch processing complete:");
            System.out.println("  Targets processed: " + (successCount + failCount));
            System.out.println("  Successful: " + successCount);
            System.out.println("  Failed: " + failCount);
            System.out.println("  Total time: " + totalTime + "ms");
        }

        return failCount > 0 ? 1 : 0;
    }

    private ExecutionResult processTarget(File target) throws Exception {
        if (!quiet) {
            System.out.println("Processing: " + target.getName());
        }

        ExecutionConfig config = ExecutionConfig.builder()
            .target(target)
            .plugin(plugin)
            .outputFormat(format)
            .verbose(verbose)
            .quiet(quiet)
            .build();

        ExecutionEngine engine = new ExecutionEngine();
        ExecutionResult result = engine.execute(config);

        if (outputDir != null) {
            File outputFile = new File(outputDir, target.getName() + "." + format.getExtension());
            OutputHandler handler = OutputHandler.forFormat(format, outputFile);
            handler.writeResult(result);
        }

        return result;
    }
}
