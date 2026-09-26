package com.tonic.service.deadcode;

import java.util.LinkedHashSet;
import java.util.Set;

/** Dead-code analysis settings: whether public members are entry points, a keep-list of members forced live, and a skip-list of classes left alone. */
public final class DeadCodeConfig
{

    private final boolean publicAsEntryPoints;
    private final Set<String> keep;
    private final Set<String> skipClassesInternal;

    /**
     * Creates a configuration; blank entries are dropped.
     *
     * @param publicAsEntryPoints whether every public member counts as an entry point
     * @param keepEntries members forced live, as com.foo.Bar#name for all overloads or com.foo.Bar#name(descriptor) for one
     * @param skipClasses dotted class names excluded from analysis and removal
     */
    public DeadCodeConfig(boolean publicAsEntryPoints, Set<String> keepEntries, Set<String> skipClasses)
    {
        this.publicAsEntryPoints = publicAsEntryPoints;
        this.keep = new LinkedHashSet<>();
        for (String s : keepEntries)
        {
            String t = s.trim();
            if (!t.isEmpty())
            {
                this.keep.add(t);
            }
        }
        this.skipClassesInternal = new LinkedHashSet<>();
        for (String s : skipClasses)
        {
            String t = s.trim();
            if (!t.isEmpty())
            {
                this.skipClassesInternal.add(t.replace('.', '/'));
            }
        }
    }

    boolean isPublicAsEntryPoints()
    {
        return publicAsEntryPoints;
    }

    Set<String> skipClasses()
    {
        return skipClassesInternal;
    }

    boolean keeps(String ownerInternal, String name, String desc)
    {
        String ownerDotted = ownerInternal.replace('/', '.');
        for (String entry : keep)
        {
            int hash = entry.indexOf('#');
            if (hash < 0)
            {
                continue;
            }
            if (!entry.substring(0, hash).trim().equals(ownerDotted))
            {
                continue;
            }
            String member = entry.substring(hash + 1).trim();
            int paren = member.indexOf('(');
            if (paren >= 0)
            {
                if (member.substring(0, paren).equals(name) && member.substring(paren).equals(desc))
                {
                    return true;
                }
            }
            else if (member.equals(name))
            {
                return true;
            }
        }
        return false;
    }
}
