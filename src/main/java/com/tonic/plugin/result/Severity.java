package com.tonic.plugin.result;

import lombok.Getter;

/** How serious a finding is, from INFO at level 0 to CRITICAL at level 4, each with a display name. */
@Getter
public enum Severity
{

    INFO("Info", 0),
    LOW("Low", 1),
    MEDIUM("Medium", 2),
    HIGH("High", 3),
    CRITICAL("Critical", 4);

    private final String displayName;
    private final int level;

    Severity(String displayName, int level)
    {
        this.displayName = displayName;
        this.level = level;
    }

    /**
     * Reports whether this severity is strictly above another.
     *
     * @param other the severity to compare with
     * @return true when this level is greater
     */
    public boolean isHigherThan(Severity other)
    {
        return this.level > other.level;
    }

    /**
     * Reports whether this severity is at or above a threshold.
     *
     * @param threshold the severity to compare with
     * @return true when this level is greater or equal
     */
    public boolean isAtLeast(Severity threshold)
    {
        return this.level >= threshold.level;
    }

    /**
     * Parses a severity name, ignoring case; MED and CRIT are accepted as short forms.
     *
     * @param value the name
     * @return the severity, or INFO when the value is null or not recognised
     */
    public static Severity fromString(String value)
    {
        if (value == null) return INFO;
        String upper = value.toUpperCase();
        switch (upper)
        {
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
