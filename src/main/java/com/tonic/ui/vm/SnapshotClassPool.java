package com.tonic.ui.vm;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;

import java.io.ByteArrayInputStream;
import java.util.Map;

/**
 * A defensive, lazily-materialized {@link ClassPool} for an isolated VM instance. User classes are served from a
 * frozen byte snapshot - parsed on first access and cached in this pool - so later in-place edits to the live
 * project cannot perturb a running VM. JDK / library classes (never edited) are delegated read-only to the live
 * project pool, so only the project's own classes are copied.
 */
public final class SnapshotClassPool extends ClassPool {

    private final Map<String, byte[]> frozenUserClasses;
    private final ClassPool delegate;

    public SnapshotClassPool(Map<String, byte[]> frozenUserClasses, ClassPool delegate) {
        super(true);
        this.frozenUserClasses = frozenUserClasses;
        this.delegate = delegate;
    }

    @Override
    public ClassFile get(String internalName) {
        ClassFile materialized = super.get(internalName);
        if (materialized != null) {
            return materialized;
        }
        byte[] frozen = frozenUserClasses.get(internalName);
        if (frozen != null) {
            try {
                ClassFile parsed = new ClassFile(new ByteArrayInputStream(frozen));
                put(parsed);
                return parsed;
            } catch (Exception e) {
                return null;
            }
        }
        return delegate != null ? delegate.get(internalName) : null;
    }
}
