package com.tonic.ui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonic.ui.layout.Arrangement.Divided;
import com.tonic.ui.layout.Arrangement.Node;
import com.tonic.ui.layout.Arrangement.Stack;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArrangementTest
{

    private static String pathTo(Node node, StackId stack)
    {
        final Divided divided = node instanceof Divided ? (Divided) node : null;
        if (divided == null)
        {
            return node.stacks().contains(stack) ? "" : null;
        }
        final String first = pathTo(divided.first(), stack);
        if (first != null)
        {
            return (divided.horizontal() ? "L" : "T") + first;
        }
        final String second = pathTo(divided.second(), stack);
        return second == null ? null : (divided.horizontal() ? "R" : "B") + second;
    }

    private static String pathTo(Arrangement arrangement, StackId stack)
    {
        return pathTo(arrangement.working().orElseThrow(), stack);
    }

    private static String firstTurn(Arrangement arrangement, StackId moved)
    {
        final String path = pathTo(arrangement, moved);
        return path.isEmpty() ? "" : path.substring(path.length() - 1);
    }

    private static Node leafOf(Arrangement arrangement, StackId stack)
    {
        return arrangement.leaves().stream()
                .filter(node -> node.stacks().contains(stack))
                .findFirst()
                .orElse(null);
    }

    private static int dividers(Node node)
    {
        final Divided divided = node instanceof Divided ? (Divided) node : null;
        if (divided == null)
        {
            return 0;
        }
        return 1 + dividers(divided.first()) + dividers(divided.second());
    }

    @Test
    @DisplayName("the arrangement that ships holds every stack once")
    void theShippedOne()
    {
        Arrangement shipped = Presets.shipped();
        assertTrue(shipped.isSound());
        assertEquals(List.of(Stacks.NAVIGATOR, Stacks.DOCUMENTS, Stacks.BOTTOM, Stacks.TOOLS), shipped.shown(), "every stack should be somewhere");
        assertTrue(shipped.hidden().isEmpty());
        assertEquals(List.of(), shipped.viewsIn(Stacks.TOOLS), "the tools are whatever registered one, which no preset can name -- but the " + "side they live on is there regardless");
        assertEquals(List.of(Views.WELCOME), shipped.viewsIn(Stacks.DOCUMENTS));
    }

    @Test
    @DisplayName("every preset is something that can be drawn")
    void everyPreset()
    {
        final Arrangement shipped = Presets.shipped();
        assertTrue(shipped.isSound(), "the shipped arrangement repeats a stack");
        assertFalse(shipped.shown().isEmpty(), "the shipped arrangement draws nothing");
    }

    @Test
    @DisplayName("a stack dropped on an edge lands on that side")
    void dropsLandWhereTheGestureSaid()
    {
        Arrangement shipped = Presets.shipped();

        assertEquals("R", firstTurn(shipped.withDropped(Stacks.NAVIGATOR, Stacks.DOCUMENTS, Edge.RIGHT), Stacks.NAVIGATOR), "dropped right of the documents");
        assertEquals("L", firstTurn(shipped.withDropped(Stacks.BOTTOM, Stacks.DOCUMENTS, Edge.LEFT), Stacks.BOTTOM), "dropped left of the documents");
        assertEquals("T", firstTurn(shipped.withDropped(Stacks.BOTTOM, Stacks.DOCUMENTS, Edge.TOP), Stacks.BOTTOM), "dropped above the documents");
        assertEquals("B", firstTurn(shipped.withDropped(Stacks.NAVIGATOR, Stacks.DOCUMENTS, Edge.BOTTOM), Stacks.NAVIGATOR), "dropped below the documents");
    }

    @Test
    @DisplayName("dropping takes the stack out of where it was")
    void aStackIsInOnePlaceOnly()
    {
        Arrangement moved = Presets.shipped().withDropped(Stacks.NAVIGATOR, Stacks.BOTTOM, Edge.RIGHT);
        assertTrue(moved.isSound());
        assertEquals(1, moved.shown().stream().filter(Stacks.NAVIGATOR::equals).count());
        assertEquals(4, moved.shown().size(), "nothing was lost either");
    }

    @Test
    @DisplayName("dropping a stack on the middle of another puts its views in it")
    void middleDropMerges()
    {
        Arrangement merged = Presets.shipped().withStacked(Stacks.NAVIGATOR, Stacks.DOCUMENTS);
        assertTrue(merged.isSound());
        assertFalse(merged.shows(Stacks.NAVIGATOR));
        assertEquals(List.of(Views.WELCOME, Views.NAVIGATOR), merged.viewsIn(Stacks.DOCUMENTS));
        assertEquals(Optional.of(Stacks.DOCUMENTS), merged.stackOf(Views.NAVIGATOR));

        assertEquals(3, merged.shown().size());
    }

    @Test
    @DisplayName("a stack emptied of its last view stops being drawn")
    void emptyStacksGo()
    {
        Arrangement moved = Presets.shipped()
                .withViewIn(Views.WELCOME, Stacks.BOTTOM, 0);
        assertFalse(moved.shows(Stacks.DOCUMENTS), "the pane it left has nothing in it");
        assertTrue(moved.isSound());
    }

    @Test
    @DisplayName("a side emptied of its last view keeps its place")
    void emptySidesStay()
    {
        Arrangement moved = Presets.shipped()
                .withViewIn(Views.NAVIGATOR, Stacks.DOCUMENTS, 0);
        assertTrue(moved.shows(Stacks.NAVIGATOR), "the side it left went with it");
        assertEquals(List.of(), moved.viewsIn(Stacks.NAVIGATOR));
        assertEquals(List.of(Views.NAVIGATOR, Views.WELCOME), moved.viewsIn(Stacks.DOCUMENTS));
        assertTrue(moved.isSound());
    }

    @Test
    @DisplayName("a view dropped on an edge makes a stack of its own there")
    void aViewSplitsOffIntoItsOwnStack()
    {
        final StackId made = StackId.generated(3);
        Arrangement split = Presets.shipped()
                .withStacked(Stacks.NAVIGATOR, Stacks.DOCUMENTS)
                .withViewSplit(Views.NAVIGATOR, Stacks.DOCUMENTS, Edge.RIGHT, made);

        assertTrue(split.shows(made), "the stack the drop made is on the window");
        assertEquals(List.of(Views.NAVIGATOR), split.viewsIn(made));
        assertEquals(List.of(Views.WELCOME), split.viewsIn(Stacks.DOCUMENTS));
        assertEquals("R", firstTurn(split, made));
        assertTrue(split.isSound());
    }

    @Test
    @DisplayName("moving a view inside its own stack reorders it")
    void reorderingWithinAStack()
    {
        Arrangement three = Presets.shipped()
                .withStacked(Stacks.NAVIGATOR, Stacks.DOCUMENTS)
                .withStacked(Stacks.BOTTOM, Stacks.DOCUMENTS);
        assertEquals(List.of(Views.WELCOME, Views.NAVIGATOR, Views.CONSOLE, Views.BOOKMARKS, Views.COMMENTS, Views.LOCAL_HISTORY), three.viewsIn(Stacks.DOCUMENTS));

        Arrangement moved = three.withViewIn(Views.CONSOLE, Stacks.DOCUMENTS, 0);
        assertEquals(List.of(Views.CONSOLE, Views.WELCOME, Views.NAVIGATOR, Views.BOOKMARKS, Views.COMMENTS, Views.LOCAL_HISTORY), moved.viewsIn(Stacks.DOCUMENTS), "the tab moved to the front");
        assertEquals(0, ((Stack) leafOf(moved, Stacks.DOCUMENTS)).selected(), "and what moved is what is showing");
        assertTrue(moved.isSound());
    }

    @Test
    @DisplayName("a view put past the end of a stack goes last")
    void placingPastTheEnd()
    {
        Arrangement moved = Presets.shipped()
                .withViewIn(Views.NAVIGATOR, Stacks.DOCUMENTS, Integer.MAX_VALUE);
        assertEquals(List.of(Views.WELCOME, Views.NAVIGATOR), moved.viewsIn(Stacks.DOCUMENTS));
    }

    @Test
    @DisplayName("taking a stack out leaves no divider dividing nothing")
    void removingTidiesUp()
    {
        Arrangement without = Presets.shipped().without(Stacks.BOTTOM);
        assertEquals(3, without.shown().size());
        assertEquals(Set.of(Stacks.BOTTOM), without.hidden());
        assertEquals(2, dividers(without.working().orElseThrow()), "four stacks need three dividers; three need two");
    }

    @Test
    @DisplayName("a stack shown again is the size it ships at")
    void showingAgainAtItsOwnSize()
    {
        for (StackId stack : List.of(Stacks.NAVIGATOR, Stacks.BOTTOM))
        {
            Arrangement back = Presets.shipped().without(stack)
                    .withShown(stack, Presets.contentsOf(stack));
            assertEquals(Stacks.homeWeight(stack), shareOf(back, stack), 0.001, stack.key() + " came back a size other than the one it ships at");
            assertEquals(shareOf(Presets.shipped(), stack), shareOf(back, stack), 0.001, stack.key() + " came back a size other than the one it had");
        }
    }

    @Test
    @DisplayName("the tools land at their edge and their share, the same as the rest")
    void whereTheToolsLand()
    {
        final ViewId tool = new ViewId("tool:Matches");
        Arrangement with = Presets.shipped().withShown(Stacks.TOOLS, List.of(tool));

        assertTrue(with.shows(Stacks.TOOLS));
        assertEquals("R", firstTurn(with, Stacks.TOOLS), "they belong on the right");
        assertEquals(Stacks.homeWeight(Stacks.TOOLS), shareOf(with, Stacks.TOOLS), 0.001);
        assertTrue(with.isSound());
    }

    private static double shareOf(Arrangement arrangement, StackId stack)
    {
        for (Divided divided : dividersIn(arrangement.working().orElseThrow()))
        {
            if (isTheLeaf(divided.first(), stack))
            {
                return divided.weight();
            }
            if (isTheLeaf(divided.second(), stack))
            {
                return 1 - divided.weight();
            }
        }
        return Double.NaN;
    }

    private static List<Divided> dividersIn(Node node)
    {
        final Divided divided = node instanceof Divided ? (Divided) node : null;
        if (divided == null)
        {
            return List.of();
        }
        List<Divided> found = new ArrayList<>();
        found.add(divided);
        found.addAll(dividersIn(divided.first()));
        found.addAll(dividersIn(divided.second()));
        return found;
    }

    private static boolean isTheLeaf(Node node, StackId stack)
    {
        return !(node instanceof Divided) && node.stacks().contains(stack);
    }

    @Test
    @DisplayName("a stack shown again arrives at the edge it ships on")
    void showingAgain()
    {
        Arrangement without = Presets.shipped().without(Stacks.NAVIGATOR);
        Arrangement back = without.withShown(Stacks.NAVIGATOR, Presets.contentsOf(Stacks.NAVIGATOR));
        assertTrue(back.shows(Stacks.NAVIGATOR));
        assertEquals("L", firstTurn(back, Stacks.NAVIGATOR));
        assertEquals(List.of(Views.NAVIGATOR), back.viewsIn(Stacks.NAVIGATOR));
        assertTrue(back.isSound());
    }

    @Test
    @DisplayName("a stack shown holding nothing is not shown at all")
    void showingNothing()
    {
        Arrangement back = Presets.shipped().without(Stacks.TOOLS)
                .withShown(Stacks.TOOLS, List.of());
        assertFalse(back.shows(Stacks.TOOLS));
        assertEquals(Presets.shipped().without(Stacks.TOOLS).views(), back.views());
    }

    @Test
    @DisplayName("hiding the last stack leaves an arrangement rather than a hole")
    void everythingHidden()
    {
        Arrangement nothing = Presets.shipped();
        for (StackId stack : Stacks.shipped())
        {
            nothing = nothing.without(stack);
        }
        assertTrue(nothing.working().isEmpty());
        assertTrue(nothing.shown().isEmpty());
        assertEquals(Set.copyOf(Stacks.shipped()), nothing.hidden());

        Arrangement one = nothing.withShown(Stacks.DOCUMENTS, Presets.contentsOf(Stacks.DOCUMENTS));
        assertEquals(List.of(Stacks.DOCUMENTS), one.shown());
        assertInstanceOf(Stack.class, one.working().orElseThrow());
    }

    @Test
    @DisplayName("dropping a stack on itself changes nothing")
    void ontoItself()
    {
        Arrangement shipped = Presets.shipped();
        assertSame(shipped, shipped.withDropped(Stacks.DOCUMENTS, Stacks.DOCUMENTS, Edge.LEFT));
        assertSame(shipped, shipped.withStacked(Stacks.DOCUMENTS, Stacks.DOCUMENTS));
    }

    @Test
    @DisplayName("collapsing follows a stack and is forgotten when it goes")
    void collapsedTravelsWithTheStack()
    {
        Arrangement pinned = Presets.shipped().withCollapsed(Stacks.NAVIGATOR, true);
        assertTrue(pinned.isCollapsed(Stacks.NAVIGATOR));

        Arrangement moved = pinned.withDropped(Stacks.NAVIGATOR, Stacks.DOCUMENTS, Edge.RIGHT);
        assertTrue(moved.isCollapsed(Stacks.NAVIGATOR), "it is still put away, on its new edge");

        Arrangement gone = moved.without(Stacks.NAVIGATOR);
        assertFalse(gone.isCollapsed(Stacks.NAVIGATOR), "a stack that is not drawn cannot be collapsed");
    }

    @Test
    @DisplayName("a collapsed stack that is not drawn is not collapsed")
    void collapsedMustBeShown()
    {
        Arrangement fromAFile = new Arrangement(Optional.of(Stack.of(Stacks.DOCUMENTS, Views.WELCOME)), List.of(), List.of(), Set.of(Stacks.NAVIGATOR, Stacks.DOCUMENTS));

        Arrangement tidy = fromAFile.normalised();
        assertTrue(tidy.isCollapsed(Stacks.DOCUMENTS), "the one that is there stays put away");
        assertFalse(tidy.isCollapsed(Stacks.NAVIGATOR), "the one that is not there cannot be put away");
    }

    @Test
    @DisplayName("no sequence of stack drags loses one or makes two of one")
    void draggingStacksAtRandomStaysSound()
    {
        final Random random = new Random(20260827L);
        final List<StackId> stacks = Stacks.shipped();
        final Edge[] edges = Edge.values();
        Arrangement arrangement = Presets.shipped();

        for (int step = 0; step < 4000; step++)
        {
            final StackId moved = stacks.get(random.nextInt(stacks.size()));
            final StackId onto = stacks.get(random.nextInt(stacks.size()));
            final int what = random.nextInt(10);
            if (what == 0)
            {
                arrangement = arrangement.without(moved);
            }
            else if (what == 1)
            {
                arrangement = arrangement.withShown(moved, Presets.contentsOf(moved));
            }
            else if (arrangement.shows(moved) && arrangement.shows(onto))
            {
                arrangement = arrangement.withDropped(moved, onto, edges[random.nextInt(edges.length)]);
            }
            assertTrue(arrangement.isSound(), "step " + step + " repeated a stack");
            assertTrue(arrangement.shown().size() <= stacks.size());
        }
    }

    @Test
    @DisplayName("no sequence of tab drags loses a view or makes two of one")
    void draggingViewsAtRandomStaysSound()
    {
        final Random random = new Random(20260831L);
        final List<ViewId> views = List.of(Views.WELCOME, Views.NAVIGATOR, Views.CONSOLE, Views.BOOKMARKS, Views.COMMENTS, Views.LOCAL_HISTORY);
        final Edge[] edges = Edge.values();
        Arrangement arrangement = Presets.shipped();
        int made = 0;

        for (int step = 0; step < 4000; step++)
        {
            final ViewId moved = views.get(random.nextInt(views.size()));
            final List<StackId> targets = arrangement.shown().stream()
                    .filter(stack -> !Stacks.TOOLS.equals(stack))
                    .collect(java.util.stream.Collectors.toList());
            if (targets.isEmpty())
            {
                break;
            }
            final StackId onto = targets.get(random.nextInt(targets.size()));
            final Edge edge = edges[random.nextInt(edges.length)];
            arrangement = edge == Edge.CENTRE
                    ? arrangement.withViewIn(moved, onto, random.nextInt(4))
                    : arrangement.withViewSplit(moved, onto, edge, StackId.generated(made++));

            assertTrue(arrangement.isSound(), "step " + step + " repeated a view or a stack");
            assertEquals(Set.copyOf(views), new LinkedHashSet<>(arrangement.views()), "step " + step + " lost a view");
        }
    }

    @Test
    @DisplayName("dragging back and forth does not pile up dividers")
    void noAccumulation()
    {
        Arrangement arrangement = Presets.shipped();
        for (int again = 0; again < 20; again++)
        {
            arrangement = arrangement.withDropped(Stacks.NAVIGATOR, Stacks.DOCUMENTS, Edge.RIGHT);
            arrangement = arrangement.withDropped(Stacks.NAVIGATOR, Stacks.DOCUMENTS, Edge.LEFT);
        }
        assertEquals(3, dividers(arrangement.working().orElseThrow()), "four stacks want three dividers however often they were moved");
    }

    @Test
    @DisplayName("what a stack shows stays inside the stack")
    void selectedStaysInRange()
    {
        Arrangement odd = new Arrangement(Optional.of(new Divided(true, 0.3, new Stack(Stacks.NAVIGATOR, List.of(Views.NAVIGATOR, Views.CONSOLE), 7), Stack.of(Stacks.DOCUMENTS, Views.WELCOME))), List.of(), List.of(), Set.of()).normalised();

        assertEquals(1, ((Stack) leafOf(odd, Stacks.NAVIGATOR)).selected(), "a selection past the end should come back inside it");
    }

    @Test
    @DisplayName("selecting a tab picks one of the tabs that are there")
    void selectingStaysInRange()
    {
        Arrangement two = Presets.shipped().withStacked(Stacks.NAVIGATOR, Stacks.DOCUMENTS);
        assertEquals(1, ((Stack) leafOf(two.withSelected(Stacks.DOCUMENTS, 9), Stacks.DOCUMENTS)).selected());
        assertEquals(0, ((Stack) leafOf(two.withSelected(Stacks.DOCUMENTS, -3), Stacks.DOCUMENTS)).selected());
    }

    @Test
    @DisplayName("the order stacks are laid out in is the order they are drawn")
    void layoutOrder()
    {
        List<StackId> order = new ArrayList<>(Presets.shipped().shown());
        assertEquals(List.of(Stacks.NAVIGATOR, Stacks.DOCUMENTS, Stacks.BOTTOM, Stacks.TOOLS), order);
    }
}
