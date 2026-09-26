package com.tonic.ui.layout;

import com.tonic.ui.theme.JStudioTheme;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import javax.swing.JRootPane;

final class TabDragOverlay extends JComponent
{

    private static final long serialVersionUID = 1L;

    private static final float WASH = 0.30f;

    private final transient JRootPane root;

    private transient Component previous;
    private boolean previousVisible;

    private transient Rectangle zone;
    private transient Rectangle mark;

    TabDragOverlay(JRootPane root)
    {
        this.root = root;
        setOpaque(false);
    }

    void raise()
    {
        if (previous == null)
        {
            previous = root.getGlassPane();
            previousVisible = previous != null && previous.isVisible();
        }
        root.setGlassPane(this);
        setVisible(true);
        repaint();
    }

    void lower()
    {
        zone = null;
        mark = null;
        setVisible(false);
        if (previous != null)
        {
            root.setGlassPane(previous);
            previous.setVisible(previousVisible);
            previous = null;
        }
    }

    void show(DragFeedback where)
    {
        zone = here(where == null ? null : where.zone());
        mark = here(where == null ? null : where.mark());
        repaint();
    }

    private Rectangle here(Rectangle onScreen)
    {
        if (onScreen == null || !isShowing())
        {
            return null;
        }
        final Point origin = getLocationOnScreen();
        final Rectangle local = new Rectangle(onScreen);
        local.translate(-origin.x, -origin.y);
        return local.intersects(new Rectangle(getSize())) ? local : null;
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        if (zone == null && mark == null)
        {
            return;
        }
        final Graphics2D graphics = (Graphics2D) g.create();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (zone != null)
        {
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, WASH));
            graphics.setColor(JStudioTheme.getAccent());
            graphics.fillRect(zone.x, zone.y, zone.width, zone.height);
            graphics.setComposite(AlphaComposite.SrcOver);

            graphics.setColor(JStudioTheme.getAccent());
            graphics.setStroke(new BasicStroke(2f));
            graphics.drawRect(zone.x + 1, zone.y + 1, zone.width - 3, zone.height - 3);
        }
        if (mark != null)
        {
            graphics.setColor(JStudioTheme.getAccent());
            graphics.fillRect(mark.x, mark.y, Math.max(2, mark.width), mark.height);
        }
        graphics.dispose();
    }
}
