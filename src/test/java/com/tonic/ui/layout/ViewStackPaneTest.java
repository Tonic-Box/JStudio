package com.tonic.ui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ViewStackPaneTest
{

    private static final ViewId ONE = new ViewId("one");
    private static final ViewId TWO = new ViewId("two");
    private static final ViewId THREE = new ViewId("three");

    private final ViewStackPane strip = new ViewStackPane(Stacks.BOTTOM);

    private JFrame frame;

    @org.junit.jupiter.api.AfterEach
    void takeTheFrameDown()
    {
        if (frame != null)
        {
            frame.dispose();
            frame = null;
        }
    }

    private JPanel put(ViewId view)
    {
        JPanel body = new JPanel();
        strip.add(view, TabLook.of(view.key()), body);
        return body;
    }

    @Test
    @DisplayName("a view added is in the strip, in front, and drawn by its own panel")
    void adding()
    {
        JPanel body = put(ONE);

        assertTrue(strip.holds(ONE));
        assertEquals(List.of(ONE), strip.views());
        assertEquals(ONE, strip.selected().orElseThrow());
        assertSame(body, strip.bodyOf(ONE).orElseThrow());
    }

    @Test
    @DisplayName("adding a view already here raises it rather than making a second one")
    void addingTwice()
    {
        JPanel first = put(ONE);
        put(TWO);
        strip.add(ONE, TabLook.of("one"), new JPanel());

        assertEquals(2, strip.count(), "the same view was added twice");
        assertEquals(ONE, strip.selected().orElseThrow(), "and it was not raised");
        assertSame(first, strip.bodyOf(ONE).orElseThrow(), "its panel was replaced under it");
    }

    @Test
    @DisplayName("a view removed is out of the strip")
    void removing()
    {
        put(ONE);
        put(TWO);
        strip.remove(ONE);

        assertFalse(strip.holds(ONE));
        assertEquals(List.of(TWO), strip.views());
        assertTrue(strip.bodyOf(ONE).isEmpty());

        strip.remove(ONE);
        assertEquals(1, strip.count(), "removing what is not here disturbed what is");
    }

    @Test
    @DisplayName("clearing empties it, tabs and bookkeeping alike")
    void clearing()
    {
        put(ONE);
        put(TWO);
        strip.clear();

        assertEquals(0, strip.count());
        assertEquals(List.of(), strip.views());
        assertFalse(strip.holds(ONE));
        assertTrue(strip.selected().isEmpty());
    }

    @Test
    @DisplayName("a close is a request, and the strip waits to be told")
    void closingIsAsked()
    {
        List<ViewId> asked = new ArrayList<>();
        strip.addListener(new ViewStackPane.Listener()
        {
            @Override
            public void closeRequested(ViewId view)
            {
                asked.add(view);
            }
        });
        put(ONE);
        closeButtonFor(ONE).doClick();

        assertEquals(List.of(ONE), asked);
        assertTrue(strip.holds(ONE), "the strip closed a tab it was only asked about");
    }

    @Test
    @DisplayName("a pinned tab carries no way to close it")
    void pinnedTabsHaveNoCloseButton()
    {
        strip.add(ONE, TabLook.of("one").pinned(), new JPanel());
        assertTrue(buttonsIn((JPanel) header(ONE)).isEmpty(), "a pinned tab offered a close button");
    }

    @Test
    @DisplayName("bringing a view forward is announced once, and only when it changes")
    void selectionIsAnnounced()
    {
        List<ViewId> heard = new ArrayList<>();
        strip.addListener(new ViewStackPane.Listener()
        {
            @Override
            public void viewSelected(ViewId view)
            {
                heard.add(view);
            }
        });
        put(ONE);
        put(TWO);
        strip.select(ONE);
        strip.select(ONE);

        assertEquals(List.of(ONE, TWO, ONE), heard);
    }

    @Test
    @DisplayName("a renamed view keeps its place and its panel")
    void renaming()
    {
        JPanel body = put(ONE);
        put(TWO);
        strip.relook(ONE, TabLook.of("renamed"));

        assertEquals(List.of(ONE, TWO), strip.views(), "renaming moved the tab");
        assertSame(body, strip.bodyOf(ONE).orElseThrow());
        assertEquals("renamed", ((JLabel) ((JPanel) header(ONE)).getComponent(0)).getText());
    }

    @Test
    @DisplayName("a strip put in an order is in that order")
    void ordering()
    {
        put(ONE);
        put(TWO);
        put(THREE);
        strip.select(THREE);

        strip.order(List.of(THREE, ONE, TWO));

        assertEquals(List.of(THREE, ONE, TWO), strip.views());
        assertEquals(THREE, strip.selected().orElseThrow(), "reordering moved the tab out from under the reader");
    }

    @Test
    @DisplayName("an order naming what is not here orders what is")
    void orderingWhatIsNotHere()
    {
        put(ONE);
        put(TWO);

        strip.order(List.of(THREE, TWO, ONE));
        assertEquals(List.of(TWO, ONE), strip.views());
    }

    @Test
    @DisplayName("a point among the tabs is the gap it is nearest")
    void whereADropLands()
    {
        laidOut();
        put(ONE);
        put(TWO);
        put(THREE);
        laidOut();

        assertEquals(0, strip.dropIndexAt(leftOf(ONE)));
        assertEquals(1, strip.dropIndexAt(rightOf(ONE)));
        assertEquals(1, strip.dropIndexAt(leftOf(TWO)));
        assertEquals(3, strip.dropIndexAt(rightOf(THREE)));
        assertEquals(3, strip.dropIndexAt(new Point(4000, 4)), "past the end goes last");
    }

    @Test
    @DisplayName("nothing lands to the left of a pinned tab")
    void pinnedTabsKeepTheirPlace()
    {
        strip.add(ONE, TabLook.of("one").pinned(), new JPanel());
        put(TWO);
        put(THREE);
        laidOut();

        assertEquals(1, strip.dropIndexAt(leftOf(ONE)), "a tab landed before the pinned one");
        assertEquals(1, strip.dropIndexAt(rightOf(ONE)));
        assertEquals(2, strip.dropIndexAt(rightOf(TWO)));
    }

    @Test
    @DisplayName("a strip turned on its side re-draws the tabs it already had")
    void turningTheStrip()
    {
        put(ONE);
        put(TWO);
        assertEquals("Header", header(ONE).getClass().getSimpleName());

        strip.setTabsOn(javax.swing.JTabbedPane.RIGHT);
        assertEquals("Upright", header(ONE).getClass().getSimpleName(), "the tabs kept the shape they had before the strip turned");

        strip.setTabsOn(javax.swing.JTabbedPane.TOP);
        assertEquals("Header", header(TWO).getClass().getSimpleName(), "and back again");
    }

    @Test
    @DisplayName("a point among the tabs is in the row, and one below them is not")
    void whatCountsAsTheRow()
    {
        put(ONE);
        put(TWO);
        laidOut();
        final Rectangle first = tabsOf().getBoundsAt(0);

        assertTrue(strip.inTheTabRow(new Point(first.x + 2, first.y + first.height / 2)));
        assertFalse(strip.inTheTabRow(new Point(first.x + 2, first.y + first.height + 40)), "the body below the tabs was counted as the row");
        assertFalse(strip.inTheTabRow(new Point(-5, first.y + 2)), "a point left of the strip was counted as the row");
    }

    @Test
    @DisplayName("the mark sits in the gap, and past the last tab at its end")
    void whereTheMarkGoes()
    {
        put(ONE);
        put(TWO);
        laidOut();

        final Rectangle first = strip.caretAt(0).orElseThrow();
        final Rectangle between = strip.caretAt(1).orElseThrow();
        final Rectangle past = strip.caretAt(2).orElseThrow();

        assertTrue(first.x < between.x, "the gaps are not in order");
        assertTrue(between.x < past.x, "the gap past the last tab is not past it");
        assertTrue(first.height > 0, "the mark has no height to draw");
        assertTrue(strip.caretAt(9).isPresent(), "a gap past the end is still the end");
    }

    private void laidOut()
    {
        if (frame == null)
        {
            frame = new JFrame();
            frame.setContentPane(strip);
        }
        frame.pack();
        frame.setSize(600, 300);
        frame.validate();
    }

    private Point leftOf(ViewId view)
    {
        final Rectangle bounds = tabsOf().getBoundsAt(strip.views().indexOf(view));
        return new Point(bounds.x + 2, bounds.y + bounds.height / 2);
    }

    private Point rightOf(ViewId view)
    {
        final Rectangle bounds = tabsOf().getBoundsAt(strip.views().indexOf(view));
        return new Point(bounds.x + bounds.width - 2, bounds.y + bounds.height / 2);
    }

    private JTabbedPane tabsOf()
    {
        return (JTabbedPane) strip.getComponent(0);
    }

    private java.awt.Component header(ViewId view)
    {
        for (int at = 0; at < strip.count(); at++)
        {
            if (strip.views().get(at).equals(view))
            {
                return ((JTabbedPane) strip.getComponent(0)).getTabComponentAt(at);
            }
        }
        throw new IllegalStateException("no tab for " + view);
    }

    private static List<JButton> buttonsIn(JPanel header)
    {
        List<JButton> found = new ArrayList<>();
        for (Component child : header.getComponents())
        {
            if (child instanceof JButton)
            {
                found.add((JButton) child);
            }
        }
        return found;
    }

    private JButton closeButtonFor(ViewId view)
    {
        return buttonsIn((JPanel) header(view)).get(0);
    }
}
