package com.tonic.live.protocol;

import lombok.Getter;

/** A static method of a class in the target JVM: its name and JVM descriptor. */
@Getter
public final class StaticMethod
{
    private final String name;
    private final String desc;

    /**
     * Creates a method entry.
     *
     * @param name the method name
     * @param desc the method's JVM descriptor
     */
    public StaticMethod(String name, String desc)
    {
        this.name = name;
        this.desc = desc;
    }

}
