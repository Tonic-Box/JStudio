package com.tonic.ui.vm.model;

import java.util.Objects;

/** A VM object that has no host form, identified by its class and heap id. */
public final class HeapReference
{

    private final String className;
    private final int id;

    /**
     * Creates a reference.
     *
     * @param className the object's internal class name, with slashes
     * @param id the object's heap id
     */
    public HeapReference(String className, int id)
    {
        this.className = className;
        this.id = id;
    }

    /** @return the object's internal class name, with slashes */
    public String getClassName()
    {
        return className;
    }

    /** @return the object's heap id */
    public int getId()
    {
        return id;
    }

    @Override
    public boolean equals(Object other)
    {
        if (!(other instanceof HeapReference))
        {
            return false;
        }
        HeapReference that = (HeapReference) other;
        return id == that.id && className.equals(that.className);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(className, id);
    }

    @Override
    public String toString()
    {
        return className.substring(className.lastIndexOf('/') + 1) + "@" + id;
    }
}
