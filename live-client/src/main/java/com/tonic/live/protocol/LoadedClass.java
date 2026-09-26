package com.tonic.live.protocol;

import lombok.Getter;

/** A class loaded in the target JVM: its internal name and JVM access flags. */
@Getter
public final class LoadedClass
{
    private final String internalName;
    private final int accessFlags;

    /**
     * Creates a class entry.
     *
     * @param internalName the class's internal name, with slashes
     * @param accessFlags the JVM access flags
     */
    public LoadedClass(String internalName, int accessFlags)
    {
        this.internalName = internalName;
        this.accessFlags = accessFlags;
    }

    /**
     * Converts the internal name to a binary name.
     *
     * @return the class name with dots
     */
    public String getBinaryName()
    {
        return internalName.replace('/', '.');
    }
}
