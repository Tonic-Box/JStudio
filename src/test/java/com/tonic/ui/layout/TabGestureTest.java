package com.tonic.ui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TabGestureTest
{

    private static final ViewId VIEW = new ViewId("target.imports");
    private static final ViewId OTHER = new ViewId("target.query");

    private ViewStackPane strip;

    @BeforeAll
    static void needsAScreen()
    {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "a tab header cannot be built without a display");
    }

    private final AtomicInteger closed = new AtomicInteger();

    private final AtomicInteger clicked = new AtomicInteger();

    private final List<String> carried = new ArrayList<>();

    private JPanel header() throws Exception
    {
        List<JPanel> made = new ArrayList<>();
        SwingUtilities.invokeAndWait(() ->
        {
            final ViewStackPane strip = new ViewStackPane(Stacks.DOCUMENTS);
            strip.addListener(new ViewStackPane.Listener()
            {
                @Override
                public void closeRequested(ViewId view)
                {
                    closed.incrementAndGet();
                }

                @Override
                public void headerPressed(ViewId view, boolean inFront)
                {
                    clicked.incrementAndGet();
                }
            });
            strip.setDragOut(new ViewStackPane.DragOut()
            {
                @Override
                public void began(ViewId view)
                {
                    carried.add("began");
                }

                @Override
                public void movedTo(ViewId view, Point onScreen, boolean toItsOwnWindow)
                {
                    carried.add("moved");
                }

                @Override
                public void ended(ViewId view, Point onScreen, boolean toItsOwnWindow)
                {
                    carried.add("ended");
                }
            });
            strip.add(VIEW, TabLook.of("Imports"), new JPanel());
            strip.add(OTHER, TabLook.of("Query"), new JPanel());
            this.strip = strip;
            made.add((JPanel) ((JTabbedPane) strip.getComponent(0)).getTabComponentAt(0));
        });
        return made.get(0);
    }

    private static MouseEvent click(Component on, int button)
    {
        return new MouseEvent(on, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 4, 4, 1, false, button);
    }

    private static MouseEvent press(Component on, int button)
    {
        return at(on, MouseEvent.MOUSE_PRESSED, button, 4, 4);
    }

    private static MouseEvent release(Component on, int button)
    {
        return at(on, MouseEvent.MOUSE_RELEASED, button, 4, 4);
    }

    private static MouseEvent dragTo(Component on, int x, int y)
    {
        return at(on, MouseEvent.MOUSE_DRAGGED, MouseEvent.BUTTON1, x, y);
    }

    private static MouseEvent at(Component on, int what, int button, int x, int y)
    {
        return new MouseEvent(on, what, System.currentTimeMillis(), what == MouseEvent.MOUSE_DRAGGED ? MouseEvent.BUTTON1_DOWN_MASK : 0, x, y, 1, false, button);
    }

    @Test
    @DisplayName("the middle button closes the tab")
    void middleClickCloses() throws Exception
    {
        JPanel header = header();
        SwingUtilities.invokeAndWait(() -> header.dispatchEvent(click(header, MouseEvent.BUTTON2)));
        assertEquals(1, closed.get(), "a middle-click on the header did not close the tab");
    }

    @Test
    @DisplayName("the middle button does not also select it on the way")
    void middleClickDoesNotSelect() throws Exception
    {
        JPanel header = header();
        SwingUtilities.invokeAndWait(() ->
        {
            header.dispatchEvent(press(header, MouseEvent.BUTTON2));
            header.dispatchEvent(release(header, MouseEvent.BUTTON2));
        });
        assertEquals(0, clicked.get(), "a middle-press selected the tab, which is what it used to do instead of closing");
    }

    @Test
    @DisplayName("the left button still reports a click, and does not close")
    void leftClickStillSelects() throws Exception
    {
        JPanel header = header();
        SwingUtilities.invokeAndWait(() ->
        {
            header.dispatchEvent(press(header, MouseEvent.BUTTON1));
            header.dispatchEvent(release(header, MouseEvent.BUTTON1));
        });
        assertEquals(1, clicked.get(), "the left button stopped being a click");
        assertEquals(0, closed.get(), "the left button closed the tab");
    }

    @Test
    @DisplayName("pressing a tab brings it forward, before anything else is decided")
    void pressingBringsItForward() throws Exception
    {
        JPanel header = header();
        assertEquals(OTHER, strip.selected().orElseThrow());

        SwingUtilities.invokeAndWait(() -> header.dispatchEvent(press(header, MouseEvent.BUTTON1)));
        assertEquals(VIEW, strip.selected().orElseThrow(), "pressing a tab did not bring it forward");
    }

    @Test
    @DisplayName("a press that turns into a drag is not a click")
    void draggingIsNotClicking() throws Exception
    {
        JPanel header = header();
        SwingUtilities.invokeAndWait(() ->
        {
            header.dispatchEvent(press(header, MouseEvent.BUTTON1));
            header.dispatchEvent(dragTo(header, 60, 4));
            header.dispatchEvent(release(header, MouseEvent.BUTTON1));
        });

        assertEquals(0, clicked.get(), "a drag was reported as a click as well");
        assertEquals(List.of("began", "moved", "ended"), carried, "the drag did not reach the window");
    }

    @Test
    @DisplayName("a press that stays put is a click, however long it is held")
    void holdingStillIsAClick() throws Exception
    {
        JPanel header = header();
        SwingUtilities.invokeAndWait(() ->
        {
            header.dispatchEvent(press(header, MouseEvent.BUTTON1));
            header.dispatchEvent(dragTo(header, 6, 5));
            header.dispatchEvent(release(header, MouseEvent.BUTTON1));
        });

        assertEquals(1, clicked.get(), "a hand that barely moved stopped being a click");
        assertEquals(List.of(), carried, "a hand that barely moved started a drag");
    }

    @Test
    @DisplayName("the title reacts too, not only the panel behind it")
    void theTitleClosesAsWell() throws Exception
    {
        JPanel header = header();
        JLabel title = titleIn(header);
        assertTrue(title != null, "the header has no title label");
        SwingUtilities.invokeAndWait(() -> title.dispatchEvent(click(title, MouseEvent.BUTTON2)));
        assertEquals(1, closed.get(), "a middle-click on the tab's title did not close it");
    }

    private static JLabel titleIn(Component root)
    {
        if (root instanceof JLabel && ((JLabel) root).getIcon() == null)
        {
            return (JLabel) root;
        }
        if (root instanceof Container)
        {
            final Container container = (Container) root;
            for (Component child : container.getComponents())
            {
                JLabel found = titleIn(child);
                if (found != null)
                {
                    return found;
                }
            }
        }
        return null;
    }
}
