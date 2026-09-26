package com.tonic.ui.vm;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;

import java.io.ByteArrayInputStream;
import java.util.Map;

/** A class pool for an isolated VM that serves project classes from a frozen byte snapshot, parsed lazily, and delegates library classes to the live project pool. */
public final class SnapshotClassPool extends ClassPool
{

    private final Map<String, byte[]> frozenUserClasses;
    private final ClassPool delegate;

    /**
     * Creates a pool over a snapshot of the project's classes.
     *
     * @param frozenUserClasses class bytes keyed by internal name, captured before the VM runs
     * @param delegate the live project pool, read for every class not in the snapshot
     */
    public SnapshotClassPool(Map<String, byte[]> frozenUserClasses, ClassPool delegate)
    {
        super(true);
        this.frozenUserClasses = frozenUserClasses;
        this.delegate = delegate;
    }

    @Override
    public ClassFile get(String internalName)
    {
        ClassFile materialized = super.get(internalName);
        if (materialized != null)
        {
            return materialized;
        }
        byte[] frozen = frozenUserClasses.get(internalName);
        if (frozen != null)
        {
            try
            {
                ClassFile parsed = new ClassFile(new ByteArrayInputStream(frozen));
                put(parsed);
                return parsed;
            }
            catch (Exception e)
            {
                return null;
            }
        }
        return delegate != null ? delegate.get(internalName) : null;
    }
}
