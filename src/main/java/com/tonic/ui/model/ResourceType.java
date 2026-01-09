package com.tonic.ui.model;

import com.tonic.ui.theme.Icons;
import lombok.Getter;

import javax.swing.Icon;
import java.util.Set;

@Getter
public enum ResourceType {
    IMAGE("image"),
    TEXT("source"),
    BINARY("hex");

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".ico", ".svg", ".webp"
    );

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".txt", ".properties", ".xml", ".json", ".yml", ".yaml",
            ".md", ".html", ".htm", ".css", ".js", ".mf", ".sf", ".rsa",
            ".csv", ".ini", ".cfg", ".conf", ".log", ".bat", ".sh"
    );

    private final String iconName;

    ResourceType(String iconName) {
        this.iconName = iconName;
    }

    public Icon getIcon() {
        return Icons.getIcon(iconName);
    }

    public static ResourceType detect(String path, byte[] data) {
        String lowerPath = path.toLowerCase();

        for (String ext : IMAGE_EXTENSIONS) {
            if (lowerPath.endsWith(ext)) {
                return IMAGE;
            }
        }

        for (String ext : TEXT_EXTENSIONS) {
            if (lowerPath.endsWith(ext)) {
                return TEXT;
            }
        }

        if (isTextContent(data)) {
            return TEXT;
        }

        return BINARY;
    }

    private static boolean isTextContent(byte[] data) {
        if (data == null || data.length == 0) {
            return true;
        }

        int checkLength = Math.min(data.length, 8192);
        int nonTextCount = 0;

        for (int i = 0; i < checkLength; i++) {
            int b = data[i] & 0xFF;
            if (b == 0) {
                return false;
            }
            if (b < 32 && b != 9 && b != 10 && b != 13) {
                nonTextCount++;
            }
        }

        return nonTextCount < checkLength * 0.1;
    }
}
