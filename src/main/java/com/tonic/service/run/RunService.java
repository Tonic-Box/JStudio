package com.tonic.service.run;

import com.tonic.model.ProjectModel;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Launches a project's {@code main} entry point in a <b>separate JVM process</b>, so the target's
 * {@code System.exit}, an uncaught exception, or a native crash cannot take down JStudio. The current (edited)
 * project state is staged to a temp jar; stdout/stderr stream back through {@link RunOutput}.
 */
public final class RunService {

    /** Callbacks for a run; the stream/exit callbacks fire off the EDT, so implementations must marshal. */
    public interface RunOutput {
        void onStarted(String commandLine);

        void onStdout(String line);

        void onStderr(String line);

        void onFinished(int exitCode);

        void onError(String message);
    }

    private RunService() {
    }

    /**
     * Stages the project and launches {@code mainClassInternal}'s {@code main} in a child JVM. Returns the
     * {@link Process} (for {@link #terminate}), or null if staging/launch failed (reported via {@code out}).
     */
    public static Process run(ProjectModel project, String mainClassInternal, List<String> programArgs,
                              List<String> vmOptions, File workingDir, RunOutput out) {
        File tempJar;
        try {
            tempJar = Files.createTempFile("jstudio-run-", ".jar").toFile();
            ProjectJarExporter.export(project, tempJar);
        } catch (IOException e) {
            out.onError("Could not stage the project: " + e.getMessage());
            return null;
        }

        List<String> command = new ArrayList<>();
        command.add(javaBinary());
        command.addAll(vmOptions);
        command.add("-cp");
        command.add(tempJar.getAbsolutePath());
        command.add(mainClassInternal.replace('/', '.'));
        command.addAll(programArgs);

        ProcessBuilder builder = new ProcessBuilder(command);
        if (workingDir != null && workingDir.isDirectory()) {
            builder.directory(workingDir);
        }

        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            out.onError("Failed to launch: " + e.getMessage());
            tempJar.delete();
            return null;
        }

        out.onStarted(String.join(" ", command));
        pump(process.getInputStream(), out::onStdout);
        pump(process.getErrorStream(), out::onStderr);
        process.onExit().thenAccept(p -> {
            out.onFinished(p.exitValue());
            tempJar.delete();
        });
        return process;
    }

    /** Forcibly terminates a process and any child processes it spawned. */
    public static void terminate(Process process) {
        if (process == null) {
            return;
        }
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private static void pump(InputStream stream, Consumer<String> sink) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sink.accept(line);
                }
            } catch (IOException ignored) {
            }
        }, "jstudio-run-output");
        thread.setDaemon(true);
        thread.start();
    }

    private static String javaBinary() {
        String home = System.getProperty("java.home");
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return home + File.separator + "bin" + File.separator + (windows ? "java.exe" : "java");
    }
}
