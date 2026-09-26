package com.tonic.ui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonic.ui.layout.LayoutController.PaneOnScreen;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DropTargetTest
{

    private static final Rectangle PANE = new Rectangle(100, 50, 400, 300);

    private static PaneOnScreen pane(StackId stack, ViewId view, Rectangle bounds)
    {
        return new PaneOnScreen(stack, List.of(view), view, bounds);
    }

    private static Edge edgeAt(double across, double down)
    {
        return DropTarget.edgeIn(PANE, new Point(PANE.x + (int) (PANE.width * across), PANE.y + (int) (PANE.height * down)));
    }

    @Test
    @DisplayName("the sides split and the middle stacks")
    void whereEachTargetIs()
    {
        assertEquals(Edge.LEFT, edgeAt(0.05, 0.5));
        assertEquals(Edge.RIGHT, edgeAt(0.95, 0.5));
        assertEquals(Edge.TOP, edgeAt(0.5, 0.05));
        assertEquals(Edge.BOTTOM, edgeAt(0.5, 0.95));
        assertEquals(Edge.CENTRE, edgeAt(0.5, 0.5));
    }

    @Test
    @DisplayName("the middle is the bigger target, because it is the harder to hit")
    void theMiddleIsGenerous()
    {
        assertEquals(Edge.CENTRE, edgeAt(0.3, 0.5));
        assertEquals(Edge.CENTRE, edgeAt(0.7, 0.5));
        assertEquals(Edge.CENTRE, edgeAt(0.5, 0.3));
        assertEquals(Edge.CENTRE, edgeAt(0.5, 0.7));
    }

    @Test
    @DisplayName("a corner belongs to the side it is nearest")
    void cornersAreNotArbitrary()
    {
        assertEquals(Edge.LEFT, DropTarget.edgeIn(PANE, new Point(PANE.x + 4, PANE.y + 40)), "nearer the left than the top");
        assertEquals(Edge.TOP, DropTarget.edgeIn(PANE, new Point(PANE.x + 40, PANE.y + 4)), "nearer the top than the left");
    }

    @Test
    @DisplayName("a pane too small to divide only stacks")
    void aPutAwayPaneOnlyStacks()
    {
        Rectangle strip = new Rectangle(0, 0, 26, 700);
        for (double down = 0.05; down < 1; down += 0.15)
        {
            assertEquals(Edge.CENTRE, DropTarget.edgeIn(strip, new Point(13, (int) (700 * down))), "a strip should offer nothing but stacking");
        }
    }

    @Test
    @DisplayName("the part of a pane a drop would take is the part that lights up")
    void theZoneOfEachEdge()
    {
        assertEquals(new Rectangle(100, 50, 200, 300), DropTarget.zoneIn(PANE, Edge.LEFT));
        assertEquals(new Rectangle(300, 50, 200, 300), DropTarget.zoneIn(PANE, Edge.RIGHT));
        assertEquals(new Rectangle(100, 50, 400, 150), DropTarget.zoneIn(PANE, Edge.TOP));
        assertEquals(new Rectangle(100, 200, 400, 150), DropTarget.zoneIn(PANE, Edge.BOTTOM));
    }

    @Test
    @DisplayName("the middle takes the whole pane, because that is what joining it is")
    void theZoneOfTheMiddle()
    {
        assertEquals(PANE, DropTarget.zoneIn(PANE, Edge.CENTRE));

        Rectangle odd = new Rectangle(0, 0, 401, 301);
        Rectangle left = DropTarget.zoneIn(odd, Edge.LEFT);
        Rectangle right = DropTarget.zoneIn(odd, Edge.RIGHT);
        assertFalse(left.intersects(right), "the halves overlap");
        assertEquals(odd.x, left.x, "the left half does not start at the edge");
        assertEquals(odd.x + odd.width, right.x + right.width, "the right half does not reach the edge");
    }

    @Test
    @DisplayName("a point over nothing is not a target")
    void outsideEveryPane()
    {
        List<PaneOnScreen> panes = List.of(pane(Stacks.DOCUMENTS, Views.WELCOME, PANE));
        assertTrue(DropTarget.at(new Point(5, 5), panes, Stacks.NAVIGATOR).isEmpty());
        assertTrue(DropTarget.forView(new Point(5, 5), panes, stack -> true).isEmpty());
    }

    @Test
    @DisplayName("a stack is not a target for itself")
    void notOntoItself()
    {
        List<PaneOnScreen> panes = List.of(pane(Stacks.DOCUMENTS, Views.WELCOME, PANE));
        assertTrue(DropTarget.at(new Point(300, 200), panes, Stacks.DOCUMENTS).isEmpty());
    }

    @Test
    @DisplayName("a tab may be dropped back on the pane it came from")
    void aViewTargetsItsOwnPane()
    {
        List<PaneOnScreen> panes = List.of(new PaneOnScreen(Stacks.DOCUMENTS, List.of(Views.WELCOME, Views.NAVIGATOR), Views.WELCOME, PANE));

        Optional<DropTarget> middle = DropTarget.forView(new Point(300, 200), panes, stack -> true);
        assertEquals(Optional.of(new DropTarget(Stacks.DOCUMENTS, Edge.CENTRE)), middle);

        Optional<DropTarget> side = DropTarget.forView(new Point(110, 200), panes, stack -> true);
        assertEquals(Optional.of(new DropTarget(Stacks.DOCUMENTS, Edge.LEFT)), side);
    }

    @Test
    @DisplayName("a pane that will not take what is being dragged offers nothing")
    void whatAPaneWillNotTake()
    {
        List<PaneOnScreen> panes = List.of(pane(Stacks.BOTTOM, Views.CONSOLE, new Rectangle(0, 0, 400, 200)), pane(Stacks.DOCUMENTS, Views.WELCOME, new Rectangle(0, 200, 400, 400)));

        assertTrue(DropTarget.forView(new Point(200, 100), panes, Stacks.DOCUMENTS::equals).isEmpty(), "the bottom will not take a document");
        assertEquals(Stacks.DOCUMENTS, DropTarget.forView(new Point(200, 400), panes, Stacks.DOCUMENTS::equals).orElseThrow().onto());
    }

    @Test
    @DisplayName("a target produces the arrangement it promised")
    void targetsApply()
    {
        Arrangement shipped = Presets.shipped();

        Arrangement split = new DropTarget(Stacks.DOCUMENTS, Edge.RIGHT)
                .appliedTo(shipped, Stacks.NAVIGATOR);
        assertTrue(split.isSound());
        assertEquals(shipped.withDropped(Stacks.NAVIGATOR, Stacks.DOCUMENTS, Edge.RIGHT), split);

        Arrangement merged = new DropTarget(Stacks.DOCUMENTS, Edge.CENTRE)
                .appliedTo(shipped, Stacks.NAVIGATOR);
        assertEquals(shipped.withStacked(Stacks.NAVIGATOR, Stacks.DOCUMENTS), merged);
    }

    @Test
    @DisplayName("a target for one view produces the arrangement it promised")
    void viewTargetsApply()
    {
        Arrangement shipped = Presets.shipped();
        final StackId made = StackId.generated(1);

        Arrangement into = new DropTarget(Stacks.DOCUMENTS, Edge.CENTRE)
                .appliedTo(shipped, Views.CONSOLE, made);
        assertEquals(List.of(Views.WELCOME, Views.CONSOLE), into.viewsIn(Stacks.DOCUMENTS));

        Arrangement beside = new DropTarget(Stacks.DOCUMENTS, Edge.RIGHT)
                .appliedTo(shipped, Views.CONSOLE, made);
        assertEquals(List.of(Views.CONSOLE), beside.viewsIn(made));
        assertTrue(beside.isSound());
    }

    @Test
    @DisplayName("where two panes meet, the seam belongs to exactly one")
    void theBoundaryBetweenTwoPanes()
    {
        Rectangle left = new Rectangle(0, 0, 200, 400);
        Rectangle right = new Rectangle(200, 0, 200, 400);
        List<PaneOnScreen> panes = List.of(pane(Stacks.NAVIGATOR, Views.NAVIGATOR, left), pane(Stacks.DOCUMENTS, Views.WELCOME, right));

        assertEquals(1, panes.stream().filter(p -> p.bounds().contains(new Point(200, 200))).count(), "the seam is claimed by exactly one pane");

        assertEquals(Stacks.DOCUMENTS, DropTarget.at(new Point(200, 200), panes, Stacks.BOTTOM).orElseThrow().onto());
        assertEquals(Stacks.NAVIGATOR, DropTarget.at(new Point(199, 200), panes, Stacks.BOTTOM).orElseThrow().onto());
        assertFalse(DropTarget.at(new Point(401, 200), panes, Stacks.BOTTOM).isPresent());
    }
}
