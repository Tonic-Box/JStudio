package com.tonic.ui.dialog.filechooser;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * File filter based on file extensions.
 */
public class ExtensionFileFilter {

    private final String description;
    private final Set<String> extensions;

    /**
     * Create a filter for the specified extensions.
     *
     * @param description Human-readable description (e.g., "Java Files")
     * @param extensions  File extensions without dots (e.g., "jar", "class")
     */
    public ExtensionFileFilter(String description, String... extensions) {
        this.description = description;
        this.extensions = new HashSet<>();
        for (String ext : extensions) {
            this.extensions.add(ext.toLowerCase());
        }
    }

    /**
     * Create an "All Files" filter that accepts everything.
     */
    public static ExtensionFileFilter allFiles() {
        return new ExtensionFileFilter("All Files", "*");
    }

    /**
     * Check if the filter accepts the given file.
     */
    public boolean accept(File file) {
        if (file == null) {
            return false;
        }

        // Always accept directories
        if (file.isDirectory()) {
            return true;
        }

        // Accept all files if wildcard
        if (extensions.contains("*")) {
            return true;
        }

        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < name.length() - 1) {
            String ext = name.substring(dotIndex + 1).toLowerCase();
            return extensions.contains(ext);
        }

        return false;
    }

    /**
     * Get the description for display (includes extensions).
     */
    public String getDescription() {
        if (extensions.contains("*")) {
            return description + " (*.*)";
        }

        StringBuilder sb = new StringBuilder(description);
        sb.append(" (");
        boolean first = true;
        for (String ext : extensions) {
            if (!first) {
                sb.append(", ");
            }
            sb.append("*.").append(ext);
            first = false;
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Get the raw description without extensions.
     */
    public String getRawDescription() {
        return description;
    }

    /**
     * Get the extensions this filter accepts.
     */
    public Set<String> getExtensions() {
        return Collections.unmodifiableSet(extensions);
    }

    /**
     * Check if this filter accepts all files.
     */
    public boolean isAllFiles() {
        return extensions.contains("*");
    }

    @Override
    public String toString() {
        return getDescription();
    }

    // Pre-defined filters for common use cases

    /**
     * Filter for Java archive and class files.
     */
    public static ExtensionFileFilter javaFiles() {
        return new ExtensionFileFilter("Java Files", "jar", "class");
    }

    /**
     * Filter for JAR files only.
     */
    public static ExtensionFileFilter jarFiles() {
        return new ExtensionFileFilter("JAR Archives", "jar");
    }

    /**
     * Filter for class files only.
     */
    public static ExtensionFileFilter classFiles() {
        return new ExtensionFileFilter("Class Files", "class");
    }

    private static class Collections {
        static <T> Set<T> unmodifiableSet(Set<T> set) {
            return java.util.Collections.unmodifiableSet(set);
        }
    }
}
