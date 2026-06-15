package com.tonic.live.protocol;

import lombok.Getter;

/** A static method of a class in the target JVM: name + JVM descriptor ({@code (II)Ljava/lang/String;}). */
@Getter
public final class StaticMethod {
    private final String name;
    private final String desc;

    public StaticMethod(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }

}
