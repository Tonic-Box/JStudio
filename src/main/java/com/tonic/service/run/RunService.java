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

/** Launches a project's main method in a separate JVM from a temp jar of its current state, so the target cannot take down JStudio. */
public final class RunService
{

    /** Callbacks for a run; the output and exit callbacks fire off the EDT. */
    public interface RunOutput
    {
        /**
         * Called once the child process has started.
         *
         * @param commandLine the launch command, space-joined
         */
        void onStarted(String commandLine);

        /**
         * Called for each line the child writes to stdout.
         *
         * @param line the line, without its terminator
         */
        void onStdout(String line);

        /**
         * Called for each line the child writes to stderr.
         *
         * @param line the line, without its terminator
         */
        void onStderr(String line);

        /**
         * Called when the child process exits.
         *
         * @param exitCode the process exit code
         */
        void onFinished(int exitCode);

        /**
         * Called when staging or launching fails.
         *
         * @param message the failure, for display
         */
        void onError(String message);
    }

    private RunService()
    {
    }

    /**
     * Stages the project to a temp jar and launches a class's main method in a child JVM.
     *
     * @param project the project to run
     * @param mainClassInternal the main class's internal name, with slashes
     * @param programArgs arguments passed to main
     * @param vmOptions options passed to the JVM before the classpath
     * @param workingDir the process working directory, or null to inherit JStudio's
     * @param javaHome the JDK home to launch with, or null for the running JVM
     * @param out receives the process's output, exit and errors
     * @return the child process, or null if staging or launch failed (reported to out)
     */
    public static Process run(ProjectModel project, String mainClassInternal, List<String> programArgs, List<String> vmOptions, File workingDir, File javaHome, RunOutput out)
    {
        File tempJar;
        try
        {
            tempJar = Files.createTempFile("jstudio-run-", ".jar").toFile();
            ProjectJarExporter.export(project, tempJar);
        }
        catch (IOException e)
        {
            out.onError("Could not stage the project: " + e.getMessage());
            return null;
        }

        List<String> command = new ArrayList<>();
        command.add(javaBinary(javaHome));
        command.addAll(vmOptions);
        command.add("-cp");
        command.add(tempJar.getAbsolutePath());
        command.add(mainClassInternal.replace('/', '.'));
        command.addAll(programArgs);

        ProcessBuilder builder = new ProcessBuilder(command);
        if (workingDir != null && workingDir.isDirectory())
        {
            builder.directory(workingDir);
        }

        Process process;
        try
        {
            process = builder.start();
        }
        catch (IOException e)
        {
            out.onError("Failed to launch: " + e.getMessage());
            tempJar.delete();
            return null;
        }

        out.onStarted(String.join(" ", command));
        pump(process.getInputStream(), out::onStdout);
        pump(process.getErrorStream(), out::onStderr);
        process.onExit().thenAccept(p ->
        {
            out.onFinished(p.exitValue());
            tempJar.delete();
        });
        return process;
    }

    /**
     * Forcibly terminates a process and every process it spawned; does nothing for null.
     *
     * @param process the process to kill
     */
    public static void terminate(Process process)
    {
        if (process == null)
        {
            return;
        }
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private static void pump(InputStream stream, Consumer<String> sink)
    {
        Thread thread = new Thread(() ->
        {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    sink.accept(line);
                }
            }
            catch (IOException ignored)
            {
            }
        }, "jstudio-run-output");
        thread.setDaemon(true);
        thread.start();
    }

    private static String javaBinary(File javaHome)
    {
        File home = (javaHome != null && javaHome.isDirectory()) ? javaHome : new File(System.getProperty("java.home"));
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return new File(home, "bin" + File.separator + (windows ? "java.exe" : "java")).getAbsolutePath();
    }
}
