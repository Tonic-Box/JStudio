package com.tonic.ui.layout;

import com.tonic.ui.layout.LayoutController.PaneOnScreen;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** Where a drag would land: a stack and which part of it, worked out from screen rectangles alone. */
public final class DropTarget
{

    private final StackId onto;
    private final Edge edge;

    /**
     * Describes a drop.
     *
     * @param onto the stack under the pointer
     * @param edge which part of it, CENTRE meaning stacking
     */
    public DropTarget(StackId onto, Edge edge)
    {
        this.onto = onto;
        this.edge = edge;
    }

    /** @return the stack under the pointer */
    public StackId onto()
    {
        return onto;
    }

    /** @return which part of the stack, CENTRE meaning stacking */
    public Edge edge()
    {
        return edge;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof DropTarget))
        {
            return false;
        }
        final DropTarget that = (DropTarget) other;
        return Objects.equals(onto, that.onto) && edge == that.edge;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(onto, edge);
    }

    @Override
    public String toString()
    {
        return "DropTarget[" + onto + " " + edge + "]";
    }

    private static final double EDGE_SHARE = 0.25;

    private static final int SMALLEST_TO_SPLIT = 80;

    /**
     * Finds where a dragged stack would land.
     *
     * @param point the pointer, in the panes' coordinates
     * @param panes the panes on screen
     * @param dragged the stack being dragged, which is never a target for itself
     * @return the target, or empty where the pointer is over no other pane
     */
    public static Optional<DropTarget> at(Point point, List<PaneOnScreen> panes, StackId dragged)
    {
        return at(point, panes, dragged, stack -> true);
    }

    /**
     * Finds where a dragged stack would land, among the stacks that will take it.
     *
     * @param point the pointer, in the panes' coordinates
     * @param panes the panes on screen
     * @param dragged the stack being dragged, which is never a target for itself
     * @param allowed whether a stack will take what is being dragged
     * @return the target, or empty where the pointer is over no other pane that will take it
     */
    public static Optional<DropTarget> at(Point point, List<PaneOnScreen> panes, StackId dragged, Predicate<StackId> allowed)
    {
        final PaneOnScreen found = under(point, panes, allowed);
        if (found == null)
        {
            return Optional.empty();
        }
        if (found.stack().equals(dragged))
        {
            return Optional.empty();
        }
        return Optional.of(new DropTarget(found.stack(), edgeIn(found.bounds(), point)));
    }

    /**
     * Finds where a dragged view would land; its own pane counts, as a reorder or a split.
     *
     * @param point the pointer, in the panes' coordinates
     * @param panes the panes on screen
     * @param allowed whether a stack will take the view being dragged
     * @return the target, or empty where the pointer is over no pane that will take it
     */
    public static Optional<DropTarget> forView(Point point, List<PaneOnScreen> panes, Predicate<StackId> allowed)
    {
        final PaneOnScreen found = under(point, panes, allowed);
        return found == null ? Optional.empty()
                : Optional.of(new DropTarget(found.stack(), edgeIn(found.bounds(), point)));
    }

    private static PaneOnScreen under(Point point, List<PaneOnScreen> panes, Predicate<StackId> allowed)
    {
        return panes.stream()
                .filter(pane -> pane.bounds().contains(point))
                .filter(pane -> allowed.test(pane.stack()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Finds which part of a pane a point is in: the outer quarter on each side is that edge, the rest is the centre.
     *
     * @param bounds the pane
     * @param point the pointer, inside the pane
     * @return the nearest edge, or CENTRE for the middle and for a pane too small to split
     */
    public static Edge edgeIn(Rectangle bounds, Point point)
    {
        if (bounds.width < SMALLEST_TO_SPLIT || bounds.height < SMALLEST_TO_SPLIT)
        {
            return Edge.CENTRE;
        }
        final double acrossFrom = (point.x - bounds.x) / (double) bounds.width;
        final double downFrom = (point.y - bounds.y) / (double) bounds.height;

        final double nearestSide = Math.min(Math.min(acrossFrom, 1 - acrossFrom), Math.min(downFrom, 1 - downFrom));
        if (nearestSide > EDGE_SHARE)
        {
            return Edge.CENTRE;
        }
        if (nearestSide == acrossFrom)
        {
            return Edge.LEFT;
        }
        if (nearestSide == 1 - acrossFrom)
        {
            return Edge.RIGHT;
        }
        return nearestSide == downFrom ? Edge.TOP : Edge.BOTTOM;
    }

    /**
     * Finds the part of a pane a drop would take: all of it for the centre, the half on that side for an edge.
     *
     * @param pane the pane
     * @param edge where the drop lands
     * @return the rectangle to outline
     */
    public static Rectangle zoneIn(Rectangle pane, Edge edge)
    {
        final int halfWide = pane.width / 2;
        final int halfHigh = pane.height / 2;
        switch (edge)
        {
            case LEFT:
                return new Rectangle(pane.x, pane.y, halfWide, pane.height);
            case RIGHT:
                return new Rectangle(pane.x + pane.width - halfWide, pane.y, halfWide, pane.height);
            case TOP:
                return new Rectangle(pane.x, pane.y, pane.width, halfHigh);
            case BOTTOM:
                return new Rectangle(pane.x, pane.y + pane.height - halfHigh, pane.width, halfHigh);
            default:
                return new Rectangle(pane);
        }
    }

    /**
     * Applies this drop to a dragged stack.
     *
     * @param arrangement the arrangement before the drop
     * @param dragged the stack being dragged
     * @return the arrangement after the drop
     */
    public Arrangement appliedTo(Arrangement arrangement, StackId dragged)
    {
        return edge == Edge.CENTRE
                ? arrangement.withStacked(dragged, onto)
                : arrangement.withDropped(dragged, onto, edge);
    }

    /**
     * Applies this drop to one dragged view.
     *
     * @param arrangement the arrangement before the drop
     * @param dragged the view being dragged
     * @param made the identity for the stack a split creates, minted by the caller so two drags never share one
     * @return the arrangement after the drop
     */
    public Arrangement appliedTo(Arrangement arrangement, ViewId dragged, StackId made)
    {
        return edge == Edge.CENTRE
                ? arrangement.withViewIn(dragged, onto, Integer.MAX_VALUE)
                : arrangement.withViewSplit(dragged, onto, edge, made);
    }
}
