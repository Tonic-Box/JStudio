package com.tonic.ui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonic.ui.bottom.BottomToolbar;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WindowViewsTest
{

    private static final Rectangle MIDDLE = new Rectangle(300, 0, 700, 600);
    private static final Rectangle DOCK = new Rectangle(300, 600, 700, 200);
    private static final Rectangle SIDE = new Rectangle(0, 0, 300, 800);

    private LayoutController layout;
    private JFrame window;

    @BeforeEach
    void setUp() throws Exception
    {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "a display is needed");
        SwingUtilities.invokeAndWait(() ->
        {
            layout = new LayoutController(new JLabel("navigator"), new BottomToolbar());
            layout.show(Presets.asLaunched(Presets.shipped()));
            window = new JFrame("views under test");
            window.setContentPane((JPanel) wrap(layout.buildCenter()));
            window.setSize(1000, 800);
            window.pack();
            window.setSize(1000, 800);
            window.validate();
        });
    }

    private static javax.swing.JComponent wrap(javax.swing.JComponent centre)
    {
        JPanel holder = new JPanel(new java.awt.BorderLayout());
        holder.add(centre, java.awt.BorderLayout.CENTER);
        return holder;
    }

    @AfterEach
    void tearDown() throws Exception
    {
        if (layout != null)
        {
            SwingUtilities.invokeAndWait(() ->
            {
                layout.closeTornOut();
                window.dispose();
            });
        }
    }

    private ViewId openUtility(String key, StackId home) throws Exception
    {
        final ViewId id = new ViewId(key);
        SwingUtilities.invokeAndWait(() -> layout.open(ViewSpec.utility(id, TabLook.of(key), home, new JLabel(key))));
        return id;
    }

    private ViewId openDocument(String key) throws Exception
    {
        final ViewId id = new ViewId(key);
        SwingUtilities.invokeAndWait(() -> layout.open(ViewSpec.document(id, TabLook.of(key), new JLabel(key))));
        return id;
    }

    private List<LayoutController.PaneOnScreen> panes()
    {
        final List<LayoutController.PaneOnScreen> out = new ArrayList<>();
        out.add(new LayoutController.PaneOnScreen(Stacks.DOCUMENTS, layout.viewsIn(Stacks.DOCUMENTS), null, MIDDLE));
        out.add(new LayoutController.PaneOnScreen(Stacks.BOTTOM, layout.viewsIn(Stacks.BOTTOM), null, DOCK));
        out.add(new LayoutController.PaneOnScreen(Stacks.NAVIGATOR, layout.viewsIn(Stacks.NAVIGATOR), null, SIDE));
        return out;
    }

    private static Point middleOf(Rectangle bounds)
    {
        return new Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
    }

    @Test
    @DisplayName("the navigator comes up in the stack down the side")
    void theNavigatorIsASideTab()
    {
        assertTrue(layout.showsStack(Stacks.NAVIGATOR));
        assertEquals(List.of(Views.NAVIGATOR), layout.viewsIn(Stacks.NAVIGATOR));
        assertEquals(Stacks.NAVIGATOR, layout.stackOf(Views.NAVIGATOR).orElse(null));
    }

    @Test
    @DisplayName("a side stack stands its names up, and the middle lays them across")
    void sidesStandTheirNamesUp()
    {
        assertEquals(JTabbedPane.LEFT, layout.stripFor(Stacks.NAVIGATOR).tabsOn(), "the navigator's names should run up the left edge");
        assertEquals(JTabbedPane.RIGHT, layout.stripFor(Stacks.TOOLS).tabsOn(), "the tools' names should run up the right edge");
        assertEquals(JTabbedPane.TOP, layout.stripFor(Stacks.DOCUMENTS).tabsOn());
        assertEquals(JTabbedPane.TOP, layout.stripFor(Stacks.BOTTOM).tabsOn());
    }

    @Test
    @DisplayName("the dock and the tools come up put away, whatever is in them")
    void theSidesArriveClosed() throws Exception
    {
        openUtility("tool:Inspector", Stacks.TOOLS);
        openUtility("dock:Console", Stacks.BOTTOM);

        assertTrue(layout.isPutAway(Stacks.TOOLS), "the tools should not open across the code");
        assertTrue(layout.isPutAway(Stacks.BOTTOM), "nor should the dock");
        assertFalse(layout.isPutAway(Stacks.NAVIGATOR), "the navigator is where a reader starts");
    }

    @Test
    @DisplayName("a tool lands on the side and a dock tab underneath")
    void viewsLandWhereTheyBelong() throws Exception
    {
        final ViewId tool = openUtility("tool:Inspector", Stacks.TOOLS);
        final ViewId tab = openUtility("dock:Console", Stacks.BOTTOM);

        assertEquals(Stacks.TOOLS, layout.stackOf(tool).orElse(null));
        assertEquals(Stacks.BOTTOM, layout.stackOf(tab).orElse(null));
    }

    @Test
    @DisplayName("a document is refused by a pane that will not have it")
    void documentsOnlyDockInTheMiddle() throws Exception
    {
        final ViewId document = openDocument("class:com/foo/Bar");

        assertTrue(layout.willTake(document, Stacks.DOCUMENTS), "the middle takes it");
        assertFalse(layout.willTake(document, Stacks.NAVIGATOR), "the navigator does not");
        assertFalse(layout.willTake(document, Stacks.BOTTOM), "nor the dock");

        final ViewId utility = openUtility("dock:Console", Stacks.BOTTOM);
        assertTrue(layout.willTake(utility, Stacks.NAVIGATOR), "but a utility goes anywhere");
    }

    @Test
    @DisplayName("a dragged view ends up in one stack and only one")
    void draggingMovesRatherThanCopies() throws Exception
    {
        final ViewId tab = openUtility("dock:Console", Stacks.BOTTOM);
        assertEquals(Stacks.BOTTOM, layout.stackOf(tab).orElse(null));

        SwingUtilities.invokeAndWait(() ->
        {
            layout.beginCarrying(tab, panes());
            layout.drop(tab, middleOf(MIDDLE), false);
        });

        assertEquals(Stacks.DOCUMENTS, layout.stackOf(tab).orElse(null), "it moved");
        assertFalse(layout.viewsIn(Stacks.BOTTOM).contains(tab), "and is not still where it was");
        assertEquals(1, countOf(tab), "nor is there a second of it");
    }

    @Test
    @DisplayName("nothing moves until the reader lets go")
    void carryingChangesNothing() throws Exception
    {
        final ViewId tab = openUtility("dock:Console", Stacks.BOTTOM);

        SwingUtilities.invokeAndWait(() ->
        {
            layout.beginCarrying(tab, panes());
            layout.aimAt(tab, middleOf(MIDDLE), false);
        });
        assertNotNull(layout.aiming(), "it should be promising something");
        assertEquals(Stacks.BOTTOM, layout.stackOf(tab).orElse(null), "but the tab is still where it was");

        SwingUtilities.invokeAndWait(() -> layout.stopCarrying());
        assertEquals(Stacks.BOTTOM, layout.stackOf(tab).orElse(null), "and abandoning leaves it");
    }

    @Test
    @DisplayName("a torn-out view is in exactly one place, and comes home when its window shuts")
    void tearingOutAndComingBack() throws Exception
    {
        final ViewId tool = openUtility("tool:Inspector", Stacks.TOOLS);

        SwingUtilities.invokeAndWait(() -> layout.tearOut(tool, null));
        assertEquals(1, countOf(tool), "it is not in two places");
        assertFalse(layout.viewsIn(Stacks.TOOLS).contains(tool), "and not still on the side");
        assertTrue(layout.isOpen(tool), "it is open somewhere");

        SwingUtilities.invokeAndWait(() -> layout.closeTornOut());
        assertTrue(layout.isOpen(tool), "closing its window does not lose it");
        assertEquals(Stacks.TOOLS, layout.stackOf(tool).orElse(null), "it goes back to its side");
    }

    @Test
    @DisplayName("a dock tab goes when it is closed and comes back when it is asked for")
    void theDockComesAndGoes() throws Exception
    {
        final ViewId tab = openUtility("dock:Console", Stacks.BOTTOM);
        assertTrue(layout.viewsIn(Stacks.BOTTOM).contains(tab));

        SwingUtilities.invokeAndWait(() -> layout.close(tab));
        assertFalse(layout.isOpen(tab), "it is off the window");
        assertTrue(layout.showsStack(Stacks.BOTTOM));

        openUtility("dock:Console", Stacks.BOTTOM);
        assertEquals(Stacks.BOTTOM, layout.stackOf(tab).orElse(null), "and it is back in the dock");
    }

    @Test
    @DisplayName("an emptied side keeps its place, so there is somewhere to drag back to")
    void anEmptiedSideStays() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> layout.close(Views.NAVIGATOR));

        assertTrue(layout.showsStack(Stacks.NAVIGATOR), "the side a reader drags things back to cannot vanish with its last tab");
        assertTrue(layout.viewsIn(Stacks.NAVIGATOR).isEmpty());
    }

    private int countOf(ViewId view)
    {
        int found = 0;
        for (StackId stack : Stacks.shipped())
        {
            if (layout.viewsIn(stack).contains(view))
            {
                found++;
            }
        }
        final StackId where = layout.stackOf(view).orElse(null);
        if (where != null && !Stacks.isShipped(where))
        {
            found++;
        }
        return found;
    }
}
