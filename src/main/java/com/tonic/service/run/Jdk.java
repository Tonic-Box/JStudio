package com.tonic.service.run;

import lombok.Getter;

import java.io.File;

/** An installed JDK/JRE: its home directory, a display label, and its Java feature version (0 = unknown). */
@Getter
public final class Jdk
{

    private final File home;
    private final String label;
    private final int feature;

    /**
     * Creates a JDK entry.
     *
     * @param home the JDK or JRE home directory
     * @param label the name shown in the Run dialog
     * @param feature the Java feature version, or 0 if unknown
     */
    public Jdk(File home, String label, int feature)
    {
        this.home = home;
        this.label = label;
        this.feature = feature;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
