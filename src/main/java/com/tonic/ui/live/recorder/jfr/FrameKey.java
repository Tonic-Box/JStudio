package com.tonic.ui.live.recorder.jfr;

import lombok.Getter;

import java.util.Objects;

/**
 * Identifies one stack frame for aggregation and source navigation: the declaring class in internal/slash form
 * ({@code com/foo/Bar}, ready for {@code MainFrame.openLiveFrame}), the method name, and the source line
 * ({@code -1} when unknown). JFR reports class names dotted, so callers convert before constructing.
 */
@Getter
public final class FrameKey {

    private final String classInternal;
    private final String method;
    private final int line;

    public FrameKey(String classInternal, String method, int line) {
        this.classInternal = classInternal;
        this.method = method;
        this.line = line;
    }

    /** Simple class name + method, for display (e.g. {@code Bar.doWork}). */
    public String displayLabel() {
        int slash = classInternal.lastIndexOf('/');
        String simple = slash >= 0 ? classInternal.substring(slash + 1) : classInternal;
        return simple + "." + method;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FrameKey)) {
            return false;
        }
        FrameKey other = (FrameKey) o;
        return line == other.line && classInternal.equals(other.classInternal) && method.equals(other.method);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classInternal, method, line);
    }
}
