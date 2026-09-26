package com.tonic.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** UTF-8 file access under ~/.jstudio that replaces a file only once its new contents are complete. */
public final class ConfigFile
{

    private ConfigFile()
    {
    }

    /**
     * Writes a file beside the target and moves it over the target, ignoring any failure.
     *
     * @param file the file to replace
     * @param contents the text to store
     */
    public static void write(Path file, String contents)
    {
        try
        {
            final Path parent = file.getParent();
            if (parent != null)
            {
                Files.createDirectories(parent);
            }
            final Path beside = file.resolveSibling(file.getFileName() + ".writing");
            Files.write(beside, contents.getBytes(StandardCharsets.UTF_8));
            try
            {
                Files.move(beside, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException notAtomic)
            {
                Files.move(beside, file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException ignored)
        {
        }
    }

    /**
     * Reads a whole file.
     *
     * @param file the file to read
     * @return its contents, or an empty string where there is no file or it cannot be read
     */
    public static String read(Path file)
    {
        try
        {
            return Files.isRegularFile(file)
                    ? new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                    : "";
        }
        catch (IOException unreadable)
        {
            return "";
        }
    }
}
