package com.tonic.model;

import lombok.Getter;

import java.util.Set;

/** The kind of a resource, which picks its icon and viewer. */
@Getter
public enum ResourceType
{
    IMAGE("image"),
    TEXT("source"),
    BINARY("hex");

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".gif", ".bmp", ".ico", ".svg", ".webp");

    private static final Set<String> TEXT_EXTENSIONS = Set.of(".txt", ".properties", ".xml", ".json", ".yml", ".yaml", ".md", ".html", ".htm", ".css", ".js", ".mf", ".sf", ".rsa", ".csv", ".ini", ".cfg", ".conf", ".log", ".bat", ".sh");

    private final String iconName;

    ResourceType(String iconName)
    {
        this.iconName = iconName;
    }

    /**
     * Detects a resource's type from its extension, falling back to sniffing the first 8 KB for text.
     *
     * @param path the resource's path inside the project
     * @param data the file bytes, or null
     * @return IMAGE or TEXT for a known extension, TEXT for content that looks like text, otherwise BINARY
     */
    public static ResourceType detect(String path, byte[] data)
    {
        String lowerPath = path.toLowerCase();

        for (String ext : IMAGE_EXTENSIONS)
        {
            if (lowerPath.endsWith(ext))
            {
                return IMAGE;
            }
        }

        for (String ext : TEXT_EXTENSIONS)
        {
            if (lowerPath.endsWith(ext))
            {
                return TEXT;
            }
        }

        if (isTextContent(data))
        {
            return TEXT;
        }

        return BINARY;
    }

    private static boolean isTextContent(byte[] data)
    {
        if (data == null || data.length == 0)
        {
            return true;
        }

        int checkLength = Math.min(data.length, 8192);
        int nonTextCount = 0;

        for (int i = 0; i < checkLength; i++)
        {
            int b = data[i] & 0xFF;
            if (b == 0)
            {
                return false;
            }
            if (b < 32 && b != 9 && b != 10 && b != 13)
            {
                nonTextCount++;
            }
        }

        return nonTextCount < checkLength * 0.1;
    }
}
