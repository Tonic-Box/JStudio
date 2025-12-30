package com.tonic.ui.query.ast;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Matches if execution creates/uses a string matching a pattern.
 * Example: containsString(/password|secret/i)
 */
public final class ContainsStringPredicate implements Predicate {

    private final String pattern;
    private final boolean isRegex;
    private final boolean caseInsensitive;

    public ContainsStringPredicate(String pattern, boolean isRegex, boolean caseInsensitive) {
        this.pattern = pattern;
        this.isRegex = isRegex;
        this.caseInsensitive = caseInsensitive;
    }

    public String pattern() {
        return pattern;
    }

    public boolean isRegex() {
        return isRegex;
    }

    public boolean caseInsensitive() {
        return caseInsensitive;
    }

    public static ContainsStringPredicate literal(String text) {
        return new ContainsStringPredicate(text, false, false);
    }

    public static ContainsStringPredicate regex(String pattern) {
        return new ContainsStringPredicate(pattern, true, false);
    }

    public static ContainsStringPredicate regexIgnoreCase(String pattern) {
        return new ContainsStringPredicate(pattern, true, true);
    }

    public boolean matches(String value) {
        if (value == null) {
            return false;
        }
        if (isRegex) {
            int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
            return Pattern.compile(pattern, flags).matcher(value).find();
        }
        if (caseInsensitive) {
            return value.toLowerCase().contains(pattern.toLowerCase());
        }
        return value.contains(pattern);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContainsStringPredicate)) return false;
        ContainsStringPredicate that = (ContainsStringPredicate) o;
        return isRegex == that.isRegex &&
               caseInsensitive == that.caseInsensitive &&
               Objects.equals(pattern, that.pattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pattern, isRegex, caseInsensitive);
    }

    @Override
    public String toString() {
        return "ContainsStringPredicate{pattern='" + pattern + "', isRegex=" + isRegex +
               ", caseInsensitive=" + caseInsensitive + "}";
    }
}
