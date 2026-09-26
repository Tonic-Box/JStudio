package com.tonic.cli;

import java.io.File;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** The external update applier, run as its own JVM from the downloaded jar: it swaps the new jar over the target once the old JVM releases it, then relaunches JStudio. */
public final class Updater
{

    private static final int MAX_ATTEMPTS = 120;
    private static final long RETRY_DELAY_MS = 500;

    private Updater()
    {
    }

    /**
     * Backs up the target jar, copies the downloaded jar over it with retries until the lock releases, and relaunches the target; on failure the target is left as it was and relaunched.
     *
     * @param args the target jar path, then the downloaded jar path; with fewer than two it does nothing
     */
    public static void main(String[] args)
    {
        if (args.length < 2)
        {
            return;
        }
        Path target = Path.of(args[0]);
        Path source = Path.of(args[1]);
        Path backup = target.resolveSibling(target.getFileName() + ".bak");

        try
        {
            Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (Exception ignored)
        {
        }

        boolean swapped = false;
        for (int attempt = 0; attempt < MAX_ATTEMPTS && !swapped; attempt++)
        {
            try
            {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                swapped = true;
            }
            catch (Exception e)
            {
                sleep(RETRY_DELAY_MS);
            }
        }

        if (swapped)
        {
            deleteQuietly(backup);
            deleteQuietly(source);
        }
        relaunch(target);
    }

    private static void relaunch(Path jar)
    {
        try
        {
            new ProcessBuilder(javaBinary(), "-jar", jar.toString())
                    .directory(jar.toAbsolutePath().getParent().toFile())
                    .redirectOutput(Redirect.DISCARD)
                    .redirectError(Redirect.DISCARD)
                    .start();
        }
        catch (Exception ignored)
        {
        }
    }

    private static String javaBinary()
    {
        String home = System.getProperty("java.home");
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return home + File.separator + "bin" + File.separator + (windows ? "java.exe" : "java");
    }

    private static void deleteQuietly(Path path)
    {
        try
        {
            Files.deleteIfExists(path);
        }
        catch (Exception ignored)
        {
        }
    }

    private static void sleep(long ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }
}
