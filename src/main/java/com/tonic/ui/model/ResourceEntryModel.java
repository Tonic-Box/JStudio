package com.tonic.ui.model;

import lombok.Getter;

import javax.swing.Icon;

@Getter
public class ResourceEntryModel {

    private final String path;
    private final String name;
    private final String directory;
    private final byte[] data;
    private final ResourceType resourceType;
    private final long size;

    public ResourceEntryModel(String path, byte[] data) {
        this.path = path;
        this.name = extractName(path);
        this.directory = extractDirectory(path);
        this.data = data;
        this.size = data.length;
        this.resourceType = ResourceType.detect(path, data);
    }

    private static String extractName(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private static String extractDirectory(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(0, lastSlash) : "";
    }

    public Icon getIcon() {
        return resourceType.getIcon();
    }

    public String getFormattedSize() {
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024 * 1024) {
            return (size / 1024) + " KB";
        }
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }
}
