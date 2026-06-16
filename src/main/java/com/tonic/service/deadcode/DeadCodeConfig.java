package com.tonic.service.deadcode;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Configuration for {@link DeadCodeAnalyzer}: whether {@code public} members are treated as entry points, a
 * keep-list of fully-qualified members that are forced live (and seed reachability), and a skip-list of
 * fully-qualified classes excluded from analysis and removal entirely.
 *
 * <p>Keep entries are {@code com.foo.Bar#member} (name only, keeps all overloads) or
 * {@code com.foo.Bar#method(descriptor)} for a specific overload. Skip entries are {@code com.foo.Bar}.
 */
public final class DeadCodeConfig {

    private final boolean publicAsEntryPoints;
    private final Set<String> keep;
    private final Set<String> skipClassesInternal;

    public DeadCodeConfig(boolean publicAsEntryPoints, Set<String> keepEntries, Set<String> skipClasses) {
        this.publicAsEntryPoints = publicAsEntryPoints;
        this.keep = new LinkedHashSet<>();
        for (String s : keepEntries) {
            String t = s.trim();
            if (!t.isEmpty()) {
                this.keep.add(t);
            }
        }
        this.skipClassesInternal = new LinkedHashSet<>();
        for (String s : skipClasses) {
            String t = s.trim();
            if (!t.isEmpty()) {
                this.skipClassesInternal.add(t.replace('.', '/'));
            }
        }
    }

    boolean isPublicAsEntryPoints() {
        return publicAsEntryPoints;
    }

    /** Internal-form class names excluded from analysis and removal. */
    Set<String> skipClasses() {
        return skipClassesInternal;
    }

    /** Whether the keep-list forces the given member live (an entry-point root that is never removed). */
    boolean keeps(String ownerInternal, String name, String desc) {
        String ownerDotted = ownerInternal.replace('/', '.');
        for (String entry : keep) {
            int hash = entry.indexOf('#');
            if (hash < 0) {
                continue;
            }
            if (!entry.substring(0, hash).trim().equals(ownerDotted)) {
                continue;
            }
            String member = entry.substring(hash + 1).trim();
            int paren = member.indexOf('(');
            if (paren >= 0) {
                if (member.substring(0, paren).equals(name) && member.substring(paren).equals(desc)) {
                    return true;
                }
            } else if (member.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
