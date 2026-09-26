package com.tonic.ui.layout;

import java.util.Objects;

/** The identity of one view, wherever its tab is; two views with the same key are the same view. */
public final class ViewId
{

    private final String key;

    /**
     * Creates the identity of a view.
     *
     * @param key the name written to the layout file
     * @throws IllegalArgumentException if key is null or blank
     */
    public ViewId(String key)
    {
        if (key == null || key.trim().isEmpty())
        {
            throw new IllegalArgumentException("a view needs a key");
        }
        this.key = key;
    }

    /** @return the name written to the layout file */
    public String key()
    {
        return key;
    }

    /**
     * Names the view of one class.
     *
     * @param className the class's internal name
     * @return the view's identity
     */
    public static ViewId forClass(String className)
    {
        return new ViewId("class:" + className);
    }

    /**
     * Names the view of one resource in the archive.
     *
     * @param path the resource's path in the archive
     * @return the view's identity
     */
    public static ViewId forResource(String path)
    {
        return new ViewId("res:" + path);
    }

    /**
     * Names a view contributed under an id of its own.
     *
     * @param id the contributor's id for the view
     * @return the view's identity
     */
    public static ViewId custom(String id)
    {
        return new ViewId("custom:" + id);
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof ViewId))
        {
            return false;
        }
        return key.equals(((ViewId) other).key);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(key);
    }

    @Override
    public String toString()
    {
        return key;
    }
}
