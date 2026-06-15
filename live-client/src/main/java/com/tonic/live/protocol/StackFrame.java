package com.tonic.live.protocol;

import lombok.Getter;

/** One frame of a thread's live stack: declaring class (internal name), method, source file, and line. */
@Getter
public final class StackFrame {
    private final String declaringClass;
    private final String method;
    private final String file;
    private final int line;

    public StackFrame(String declaringClass, String method, String file, int line) {
        this.declaringClass = declaringClass;
        this.method = method;
        this.file = file;
        this.line = line;
    }

}
