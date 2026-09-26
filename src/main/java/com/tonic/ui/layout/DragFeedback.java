package com.tonic.ui.layout;

import java.awt.Rectangle;
import java.util.Objects;

/** What releasing a dragged tab at the pointer would do, in screen coordinates. */
public final class DragFeedback
{

    private final StackId onto;
    private final Edge edge;
    private final Rectangle zone;
    private final int caret;
    private final Rectangle mark;

    /**
     * Describes one drop.
     *
     * @param onto the stack that would take the tab
     * @param edge which part of the stack, CENTRE meaning among its tabs
     * @param zone the rectangle to light up
     * @param caret the gap among the tabs the tab would land in, or -1 for a split
     * @param mark where to draw that gap, or null where there is none
     */
    public DragFeedback(StackId onto, Edge edge, Rectangle zone, int caret, Rectangle mark)
    {
        this.onto = onto;
        this.edge = edge;
        this.zone = zone;
        this.caret = caret;
        this.mark = mark;
    }

    /** @return the stack that would take the tab */
    public StackId onto()
    {
        return onto;
    }

    /** @return which part of the stack, CENTRE meaning among its tabs */
    public Edge edge()
    {
        return edge;
    }

    /** @return the rectangle to light up */
    public Rectangle zone()
    {
        return zone;
    }

    /** @return the gap among the tabs the tab would land in, or -1 for a split */
    public int caret()
    {
        return caret;
    }

    /** @return where to draw the gap, or null where there is none */
    public Rectangle mark()
    {
        return mark;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof DragFeedback))
        {
            return false;
        }
        final DragFeedback that = (DragFeedback) other;
        return caret == that.caret && Objects.equals(onto, that.onto) && edge == that.edge
                && Objects.equals(zone, that.zone) && Objects.equals(mark, that.mark);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(onto, edge, zone, caret, mark);
    }

    @Override
    public String toString()
    {
        return "DragFeedback[" + onto + " " + edge + " caret=" + caret + "]";
    }
}
