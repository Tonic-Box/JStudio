package com.tonic.live.protocol;

import lombok.Getter;

/** A class loaded in the target JVM: internal name ({@code com/foo/Bar}) + JVM access flags. */
@Getter
public final class LoadedClass {
    private final String internalName;
    private final int accessFlags;

    public LoadedClass(String internalName, int accessFlags) {
        this.internalName = internalName;
        this.accessFlags = accessFlags;
    }

    public String getBinaryName() {
        return internalName.replace('/', '.');
    }
}
