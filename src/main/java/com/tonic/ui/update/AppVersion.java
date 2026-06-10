package com.tonic.ui.update;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the running build's version and jar location from the packaged manifest.
 *
 * <p>The {@code Implementation-Version} manifest attribute is written by the Gradle build, so it is
 * present only when JStudio runs from a release jar; in a dev/IDE run {@link #current()} and
 * {@link #runningJar()} return {@code null}, which the update machinery treats as "not packaged" and
 * skips all checks.
 */
public final class AppVersion {

    private static final Pattern LEADING_INT = Pattern.compile("(\\d+)");

    private AppVersion() {
    }

    /**
     * @return the running jar's {@code Implementation-Version}, or {@code null} when not run from a jar.
     */
    public static String current() {
        File jar = runningJar();
        if (jar == null) {
            return null;
        }
        try (JarFile jarFile = new JarFile(jar)) {
            Manifest manifest = jarFile.getManifest();
            return manifest != null ? manifest.getMainAttributes().getValue("Implementation-Version") : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * @return the jar file JStudio is running from, or {@code null} when running from classes (dev/IDE).
     */
    public static File runningJar() {
        try {
            URL location = AppVersion.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) {
                return null;
            }
            File file = new File(location.toURI());
            return file.isFile() && file.getName().endsWith(".jar") ? file : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @return {@code true} when running from a packaged release jar with a version manifest.
     */
    public static boolean isPackaged() {
        return current() != null;
    }

    /**
     * Extracts the leading integer of a version or release tag.
     *
     * @param version e.g. {@code "v11"} or {@code "10.0-SNAPSHOT"}
     * @return the leading integer ({@code 11}, {@code 10}), or {@code -1} if none
     */
    public static int parse(String version) {
        if (version == null) {
            return -1;
        }
        Matcher matcher = LEADING_INT.matcher(version);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }
}
