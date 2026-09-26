package com.tonic.model;

import lombok.Getter;

/** A non-class file in the project: its path, bytes and detected type. */
@Getter
public class ResourceEntryModel
{

    private final String path;
    private final String name;
    private final String directory;
    private final byte[] data;
    private final ResourceType resourceType;
    private final long size;

    /**
     * Wraps a resource and detects its type.
     *
     * @param path the path inside the project, with slashes
     * @param data the file bytes
     */
    public ResourceEntryModel(String path, byte[] data)
    {
        this.path = path;
        this.name = extractName(path);
        this.directory = extractDirectory(path);
        this.data = data;
        this.size = data.length;
        this.resourceType = ResourceType.detect(path, data);
    }

    private static String extractName(String path)
    {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private static String extractDirectory(String path)
    {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(0, lastSlash) : "";
    }

    /**
     * The icon to show for the resource.
     *
     * @return the icon name of its type
     */
    public String getIconKey()
    {
        return resourceType.getIconName();
    }

    /**
     * Formats the size for display.
     *
     * @return the size in B, KB or MB
     */
    public String getFormattedSize()
    {
        if (size < 1024)
        {
            return size + " B";
        }
        if (size < 1024 * 1024)
        {
            return (size / 1024) + " KB";
        }
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }
}
