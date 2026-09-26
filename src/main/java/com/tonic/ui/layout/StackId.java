package com.tonic.ui.layout;

import java.util.Objects;

/** The identity of one tab stack, written to layout.json, so a shipped stack's key never changes. */
public final class StackId
{

    private final String key;

    /**
     * Creates the identity of a stack.
     *
     * @param key the name written to the layout file
     * @throws IllegalArgumentException if key is null or blank
     */
    public StackId(String key)
    {
        if (key == null || key.trim().isEmpty())
        {
            throw new IllegalArgumentException("a stack needs a key");
        }
        this.key = key;
    }

    /** @return the name written to the layout file */
    public String key()
    {
        return key;
    }

    /**
     * Names a stack made by dragging a view out of another.
     *
     * @param number a number no other generated stack has
     * @return the new stack's identity
     */
    public static StackId generated(int number)
    {
        return new StackId("stack-" + number);
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof StackId))
        {
            return false;
        }
        return key.equals(((StackId) other).key);
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
