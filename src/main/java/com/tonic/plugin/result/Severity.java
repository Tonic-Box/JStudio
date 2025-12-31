package com.tonic.plugin.result;

import lombok.Getter;

@Getter
public enum Severity {

    INFO("Info", 0),
    LOW("Low", 1),
    MEDIUM("Medium", 2),
    HIGH("High", 3),
    CRITICAL("Critical", 4);

    private final String displayName;
    private final int level;

    Severity(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }

    public boolean isHigherThan(Severity other) {
        return this.level > other.level;
    }

    public boolean isAtLeast(Severity threshold) {
        return this.level >= threshold.level;
    }

    public static Severity fromString(String value) {
        if (value == null) return INFO;
        String upper = value.toUpperCase();
        switch (upper) {
            case "LOW":
                return LOW;
            case "MEDIUM":
            case "MED":
                return MEDIUM;
            case "HIGH":
                return HIGH;
            case "CRITICAL":
            case "CRIT":
                return CRITICAL;
            default:
                return INFO;
        }
    }
}
