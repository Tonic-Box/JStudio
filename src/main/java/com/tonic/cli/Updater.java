package com.tonic.cli;

import java.io.File;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * External update applier, launched as a separate JVM by {@link com.tonic.ui.update.UpdateInstaller}
 * just before JStudio exits.
 *
 * <p>It runs from the downloaded jar (not the locked, running one), waits for the target jar's file
 * lock to release once the old JVM exits, copies the new jar over it (keeping a {@code .bak} for
 * rollback), and relaunches JStudio. Copy-with-retry is the lock probe: on Windows the running JVM
 * holds the jar without write-sharing, so the copy fails until exit; on other platforms it succeeds
 * immediately. On failure the target is left untouched and the existing version is relaunched.
 *
 * <p>Usage: {@code java -cp <downloaded.jar> com.tonic.cli.Updater <targetJar> <downloadedJar>}
 */
public final class Updater {

    private static final int MAX_ATTEMPTS = 120;
    private static final long RETRY_DELAY_MS = 500;

    private Updater() {
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            return;
        }
        Path target = Path.of(args[0]);
        Path source = Path.of(args[1]);
        Path backup = target.resolveSibling(target.getFileName() + ".bak");

        try {
            Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
            // best-effort backup; the target is not modified until the copy below succeeds
        }

        boolean swapped = false;
        for (int attempt = 0; attempt < MAX_ATTEMPTS && !swapped; attempt++) {
            try {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                swapped = true;
            } catch (Exception e) {
                sleep(RETRY_DELAY_MS);
            }
        }

        if (swapped) {
            deleteQuietly(backup);
            deleteQuietly(source);
        }
        relaunch(target);
    }

    private static void relaunch(Path jar) {
        try {
            new ProcessBuilder(javaBinary(), "-jar", jar.toString())
                    .directory(jar.toAbsolutePath().getParent().toFile())
                    .redirectOutput(Redirect.DISCARD)
                    .redirectError(Redirect.DISCARD)
                    .start();
        } catch (Exception ignored) {
            // nothing more we can do from the updater process
        }
    }

    private static String javaBinary() {
        String home = System.getProperty("java.home");
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return home + File.separator + "bin" + File.separator + (windows ? "java.exe" : "java");
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // leftover temp/backup is harmless
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
