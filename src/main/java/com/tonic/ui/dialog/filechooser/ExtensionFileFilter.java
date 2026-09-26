package com.tonic.ui.dialog.filechooser;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/** A file chooser filter that accepts directories and files whose extension is in a set, ignoring case; the extension * accepts every file. */
public class ExtensionFileFilter
{

    private final String description;
    private final Set<String> extensions;

    /**
     * Creates a filter.
     *
     * @param description the name shown to the user, such as Java Files
     * @param extensions the accepted extensions without dots, such as jar; stored lower-cased
     */
    public ExtensionFileFilter(String description, String... extensions)
    {
        this.description = description;
        this.extensions = new HashSet<>();
        for (String ext : extensions)
        {
            this.extensions.add(ext.toLowerCase());
        }
    }

    /**
     * Creates the All Files filter.
     *
     * @return a filter that accepts every file
     */
    public static ExtensionFileFilter allFiles()
    {
        return new ExtensionFileFilter("All Files", "*");
    }

    /**
     * Checks whether the filter lets a file through.
     *
     * @param file the file
     * @return true for a directory, for any file under the All Files filter, or for a file with an accepted extension; false for null
     */
    public boolean accept(File file)
    {
        if (file == null)
        {
            return false;
        }

        if (file.isDirectory())
        {
            return true;
        }

        if (extensions.contains("*"))
        {
            return true;
        }

        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < name.length() - 1)
        {
            String ext = name.substring(dotIndex + 1).toLowerCase();
            return extensions.contains(ext);
        }

        return false;
    }

    /**
     * The name followed by the accepted patterns, such as Java Files (*.jar, *.class).
     *
     * @return the display text
     */
    public String getDescription()
    {
        if (extensions.contains("*"))
        {
            return description + " (*.*)";
        }

        StringBuilder sb = new StringBuilder(description);
        sb.append(" (");
        boolean first = true;
        for (String ext : extensions)
        {
            if (!first)
            {
                sb.append(", ");
            }
            sb.append("*.").append(ext);
            first = false;
        }
        sb.append(")");
        return sb.toString();
    }

    /** @return the name without the extension patterns */
    public String getRawDescription()
    {
        return description;
    }

    /**
     * The accepted extensions.
     *
     * @return an unmodifiable view of the lower-cased extensions
     */
    public Set<String> getExtensions()
    {
        return Collections.unmodifiableSet(extensions);
    }

    /**
     * Checks whether this is an All Files filter.
     *
     * @return true if the extensions include *
     */
    public boolean isAllFiles()
    {
        return extensions.contains("*");
    }

    @Override
    public String toString()
    {
        return getDescription();
    }

    /**
     * Creates a filter for jar and class files.
     *
     * @return a new filter
     */
    public static ExtensionFileFilter javaFiles()
    {
        return new ExtensionFileFilter("Java Files", "jar", "class");
    }

    /**
     * Creates a filter for jar files.
     *
     * @return a new filter
     */
    public static ExtensionFileFilter jarFiles()
    {
        return new ExtensionFileFilter("JAR Archives", "jar");
    }

    /**
     * Creates a filter for class files.
     *
     * @return a new filter
     */
    public static ExtensionFileFilter classFiles()
    {
        return new ExtensionFileFilter("Class Files", "class");
    }

    private static class Collections
    {
        static <T> Set<T> unmodifiableSet(Set<T> set)
        {
            return java.util.Collections.unmodifiableSet(set);
        }
    }
}
