package com.tonic.ui.query.ast;

import java.util.Objects;

/**
 * Scope limited to events during execution of specific methods.
 * Example: DURING clinit OF classes matching /Config/
 *          DURING method "Auth.login"
 */
public final class DuringScope implements Scope {

    private final String methodPattern;
    private final boolean isClinit;
    private final ClassScope classFilter;

    public DuringScope(String methodPattern, boolean isClinit, ClassScope classFilter) {
        this.methodPattern = methodPattern;
        this.isClinit = isClinit;
        this.classFilter = classFilter;
    }

    public String methodPattern() {
        return methodPattern;
    }

    public boolean isClinit() {
        return isClinit;
    }

    public ClassScope classFilter() {
        return classFilter;
    }

    public static DuringScope clinit() {
        return new DuringScope("<clinit>", true, null);
    }

    public static DuringScope clinitOf(ClassScope classFilter) {
        return new DuringScope("<clinit>", true, classFilter);
    }

    public static DuringScope method(String methodPattern) {
        return new DuringScope(methodPattern, false, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DuringScope)) return false;
        DuringScope that = (DuringScope) o;
        return isClinit == that.isClinit &&
               Objects.equals(methodPattern, that.methodPattern) &&
               Objects.equals(classFilter, that.classFilter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodPattern, isClinit, classFilter);
    }

    @Override
    public String toString() {
        return "DuringScope{methodPattern='" + methodPattern + "', isClinit=" + isClinit +
               ", classFilter=" + classFilter + "}";
    }
}
