package com.tonic.ui.update;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The running build's version and jar location, read from the release jar's manifest; both are null in a development run. */
public final class AppVersion
{

    private static final Pattern LEADING_INT = Pattern.compile("(\\d+)");

    private AppVersion()
    {
    }

    /**
     * Reads the Implementation-Version attribute of the running jar's manifest.
     *
     * @return the version, or null when not run from a jar or the manifest cannot be read
     */
    public static String current()
    {
        File jar = runningJar();
        if (jar == null)
        {
            return null;
        }
        try (JarFile jarFile = new JarFile(jar))
        {
            Manifest manifest = jarFile.getManifest();
            return manifest != null ? manifest.getMainAttributes().getValue("Implementation-Version") : null;
        }
        catch (IOException e)
        {
            return null;
        }
    }

    /**
     * Locates the jar this class was loaded from.
     *
     * @return the jar file, or null when running from a classes directory
     */
    public static File runningJar()
    {
        try
        {
            URL location = AppVersion.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null)
            {
                return null;
            }
            File file = new File(location.toURI());
            return file.isFile() && file.getName().endsWith(".jar") ? file : null;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * Tells whether this is a packaged release build.
     *
     * @return true when running from a jar whose manifest has a version
     */
    public static boolean isPackaged()
    {
        return current() != null;
    }

    /**
     * Extracts the first integer of a version or release tag.
     *
     * @param version the version or tag, such as v11 or 10.0-SNAPSHOT
     * @return the first integer, such as 11 or 10, or -1 if there is none or version is null
     */
    public static int parse(String version)
    {
        if (version == null)
        {
            return -1;
        }
        Matcher matcher = LEADING_INT.matcher(version);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }
}
