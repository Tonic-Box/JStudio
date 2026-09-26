package com.tonic.ui.live.recorder.jfr;

import lombok.Getter;

import java.util.Objects;

/** One stack frame for aggregation and source navigation: the declaring class's internal name, the method name, and the source line or -1. */
@Getter
public final class FrameKey
{

    private final String classInternal;
    private final String method;
    private final int line;

    /**
     * Creates a frame key.
     *
     * @param classInternal the declaring class's internal name, with slashes; JFR's dotted names must be converted first
     * @param method the method name
     * @param line the source line, or -1 when unknown
     */
    public FrameKey(String classInternal, String method, int line)
    {
        this.classInternal = classInternal;
        this.method = method;
        this.line = line;
    }

    /**
     * Returns the display label, such as Bar.doWork.
     *
     * @return the simple class name and method name joined by a dot
     */
    public String displayLabel()
    {
        int slash = classInternal.lastIndexOf('/');
        String simple = slash >= 0 ? classInternal.substring(slash + 1) : classInternal;
        return simple + "." + method;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof FrameKey))
        {
            return false;
        }
        FrameKey other = (FrameKey) o;
        return line == other.line && classInternal.equals(other.classInternal) && method.equals(other.method);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(classInternal, method, line);
    }
}
