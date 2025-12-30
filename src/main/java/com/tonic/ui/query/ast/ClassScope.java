package com.tonic.ui.query.ast;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Scope limited to classes matching a pattern or exact name.
 * Example: IN class "Config.*" or IN class "com/example/Config"
 */
public final class ClassScope implements Scope {

    private final String pattern;
    private final boolean isRegex;

    public ClassScope(String pattern, boolean isRegex) {
        this.pattern = pattern;
        this.isRegex = isRegex;
    }

    public String pattern() {
        return pattern;
    }

    public boolean isRegex() {
        return isRegex;
    }

    public static ClassScope exact(String className) {
        return new ClassScope(className, false);
    }

    public static ClassScope regex(String pattern) {
        return new ClassScope(pattern, true);
    }

    public boolean matches(String className) {
        if (isRegex) {
            return Pattern.matches(pattern, className);
        }
        return pattern.equals(className);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClassScope)) return false;
        ClassScope that = (ClassScope) o;
        return isRegex == that.isRegex && Objects.equals(pattern, that.pattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pattern, isRegex);
    }

    @Override
    public String toString() {
        return "ClassScope{pattern='" + pattern + "', isRegex=" + isRegex + "}";
    }
}
