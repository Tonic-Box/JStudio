package com.tonic.ui.query.ast;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Scope limited to methods matching a pattern or signature.
 * Example: IN method "get.*" or IN method "com/foo/Bar.process(I)V"
 */
public final class MethodScope implements Scope {

    private final String pattern;
    private final boolean isRegex;

    public MethodScope(String pattern, boolean isRegex) {
        this.pattern = pattern;
        this.isRegex = isRegex;
    }

    public String pattern() {
        return pattern;
    }

    public boolean isRegex() {
        return isRegex;
    }

    public static MethodScope exact(String signature) {
        return new MethodScope(signature, false);
    }

    public static MethodScope regex(String pattern) {
        return new MethodScope(pattern, true);
    }

    public boolean matches(String methodSignature) {
        if (isRegex) {
            return Pattern.matches(pattern, methodSignature);
        }
        return pattern.equals(methodSignature);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodScope)) return false;
        MethodScope that = (MethodScope) o;
        return isRegex == that.isRegex && Objects.equals(pattern, that.pattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pattern, isRegex);
    }

    @Override
    public String toString() {
        return "MethodScope{pattern='" + pattern + "', isRegex=" + isRegex + "}";
    }
}
