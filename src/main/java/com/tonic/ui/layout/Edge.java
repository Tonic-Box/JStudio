package com.tonic.ui.layout;

/** Which side of a pane a drop lands on, or its centre, for stacks and tabs alike. */
public enum Edge
{
    LEFT, RIGHT, TOP, BOTTOM,
    CENTRE;

    /**
     * Tells whether a split on this edge divides left from right.
     *
     * @return true for LEFT and RIGHT
     */
    public boolean horizontal()
    {
        return this == LEFT || this == RIGHT;
    }

    /**
     * Tells whether what is dropped becomes the first of the two sides of a split.
     *
     * @return true for LEFT and TOP
     */
    public boolean first()
    {
        return this == LEFT || this == TOP;
    }
}
