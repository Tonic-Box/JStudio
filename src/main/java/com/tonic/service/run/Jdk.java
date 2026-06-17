package com.tonic.service.run;

import lombok.Getter;

import java.io.File;

/** An installed JDK/JRE: its home directory, a display label, and its Java feature version (0 = unknown). */
@Getter
public final class Jdk {

    private final File home;
    private final String label;
    private final int feature;

    public Jdk(File home, String label, int feature) {
        this.home = home;
        this.label = label;
        this.feature = feature;
    }

    @Override
    public String toString() {
        return label;
    }
}
