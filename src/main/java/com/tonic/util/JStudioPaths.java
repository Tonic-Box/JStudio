package com.tonic.util;

import java.nio.file.Path;
import java.nio.file.Paths;

/** The per-user directory JStudio writes to, ~/.jstudio, spelled in one place. */
public final class JStudioPaths
{

    private static final String DIRECTORY = ".jstudio";

    private JStudioPaths()
    {
    }

    /**
     * Locates the per-user directory, which may not exist yet.
     *
     * @return the path of ~/.jstudio
     */
    public static Path userDir()
    {
        return Paths.get(System.getProperty("user.home"), DIRECTORY);
    }

    /**
     * Locates one file in the per-user directory.
     *
     * @param name the file's name
     * @return the path of the file, which may not exist yet
     */
    public static Path userFile(String name)
    {
        return userDir().resolve(name);
    }
}
