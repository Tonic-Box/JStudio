package com.tonic.ui.layout;

import com.tonic.ui.layout.LayoutController.PaneOnScreen;
import com.tonic.ui.theme.JStudioTheme;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;

/** The rearrange mode drawn over the window: grab bars to drag whole panes, with the window reflowing live to the result. */
public final class RearrangeOverlay extends JComponent
{

    private static final long serialVersionUID = 1L;

    private static final int HEADER = 22;

    private static final int CLOSE = 16;

    private final JRootPane root;
    private final transient LayoutController layout;

    private transient Arrangement before;
    private transient StackId dragging;
    private transient DropTarget target;
    private transient Point pointer;

    private transient List<PaneOnScreen> aimingAt = List.of();

    /**
     * Creates the overlay for one window.
     *
     * @param root the window's root pane, whose glass pane the overlay becomes
     * @param layout the working area being rearranged
     */
    public RearrangeOverlay(JRootPane root, LayoutController layout)
    {
        this.root = root;
        this.layout = layout;
        setOpaque(false);
        setFocusable(true);

        addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent event)
            {
                pressed(event.getPoint());
            }

            @Override
            public void mouseReleased(MouseEvent event)
            {
                released();
            }

            @Override
            public void mouseExited(MouseEvent event)
            {
                pointer = null;
                repaint();
            }
        });
        addMouseMotionListener(new MouseMotionAdapter()
        {
            @Override
            public void mouseDragged(MouseEvent event)
            {
                pointer = event.getPoint();
                if (dragging != null)
                {
                    aimAt(event.getPoint());
                }
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent event)
            {
                pointer = event.getPoint();
                final int wanted = barUnder(event.getPoint()).isPresent()
                        ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR;
                if (getCursor().getType() != wanted)
                {
                    setCursor(Cursor.getPredefinedCursor(wanted));
                    repaint();
                }
            }
        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "abandon");
        getActionMap().put("abandon", new AbstractAction()
        {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event)
            {
                abandon();
            }
        });
    }

    /** Enters rearrange mode over the window. */
    public void begin()
    {
        before = layout.arrangement();
        dragging = null;
        target = null;
        root.setGlassPane(this);
        setVisible(true);
        requestFocusInWindow();
        repaint();
    }

    /** Leaves rearrange mode, keeping the arrangement made. */
    public void end()
    {
        if (!isVisible())
        {
            return;
        }
        dragging = null;
        target = null;
        setVisible(false);
    }

    /** Leaves rearrange mode and restores the arrangement from before it began. */
    public void abandon()
    {
        if (!isVisible())
        {
            return;
        }
        if (before != null)
        {
            layout.show(before);
        }
        dragging = null;
        target = null;
        setVisible(false);
    }

    /**
     * Tells whether rearrange mode is on.
     *
     * @return true while the overlay is showing
     */
    public boolean isRearranging()
    {
        return isVisible();
    }

    private void pressed(Point at)
    {
        final Optional<PaneOnScreen> bar = barUnder(at);
        if (bar.isEmpty())
        {
            return;
        }
        final PaneOnScreen pane = bar.get();
        if (closeBoxOf(pane.bounds()).contains(at))
        {
            layout.show(layout.arrangement().without(pane.stack()));
            repaint();
            return;
        }
        dragging = pane.stack();
        freeze();
        repaint();
    }

    private void freeze()
    {
        aimingAt = layout.panesIn(this);
        before = layout.arrangement();
    }

    private void aimAt(Point at)
    {
        final Optional<DropTarget> found = DropTarget.at(at, aimingAt, dragging);
        if (found.equals(Optional.ofNullable(target)))
        {
            return;
        }
        target = found.orElse(null);
        layout.show(target == null ? before : target.appliedTo(before, dragging));
    }

    private void released()
    {
        if (dragging == null)
        {
            return;
        }
        dragging = null;
        target = null;
        aimingAt = List.of();
        before = layout.arrangement();
        repaint();
    }

    private Optional<PaneOnScreen> barUnder(Point at)
    {
        for (PaneOnScreen pane : layout.panesIn(this))
        {
            if (headerOf(pane.bounds()).contains(at))
            {
                return Optional.of(pane);
            }
        }
        return Optional.empty();
    }

    private static Rectangle headerOf(Rectangle pane)
    {
        return new Rectangle(pane.x, pane.y, pane.width, Math.min(HEADER, pane.height));
    }

    private static Rectangle closeBoxOf(Rectangle pane)
    {
        final Rectangle header = headerOf(pane);
        return new Rectangle(header.x + header.width - CLOSE - 4, header.y + (header.height - CLOSE) / 2, CLOSE, CLOSE);
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        if (!isVisible())
        {
            return;
        }
        final Graphics2D graphics = (Graphics2D) g.create();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (dragging == null)
        {
            for (PaneOnScreen pane : layout.panesIn(this))
            {
                paintPane(graphics, pane);
            }
        }
        else
        {
            paintCarrying(graphics);
        }
        graphics.dispose();
    }

    private void paintCarrying(Graphics2D graphics)
    {
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
        graphics.setColor(JStudioTheme.getBgTertiary());
        graphics.fillRect(0, 0, getWidth(), getHeight());
        graphics.setComposite(AlphaComposite.SrcOver);

        if (pointer == null)
        {
            return;
        }
        final String name = Stacks.title(dragging);
        graphics.setFont(JStudioTheme.getUIFont(11).deriveFont(Font.BOLD));
        final int wide = graphics.getFontMetrics().stringWidth(name) + 16;
        final int high = 20;
        final int x = pointer.x + 14;
        final int y = pointer.y + 14;
        graphics.setColor(JStudioTheme.getAccent());
        graphics.fillRoundRect(x, y, wide, high, 6, 6);
        graphics.setColor(JStudioTheme.getBgPrimary());
        graphics.drawString(name, x + 8, y + high - 6);
    }

    private void paintPane(Graphics2D graphics, PaneOnScreen pane)
    {
        final Rectangle bounds = pane.bounds();
        final boolean pointedAt = pointer != null && headerOf(bounds).contains(pointer);

        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.28f));
        graphics.setColor(JStudioTheme.getBgTertiary());
        graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        graphics.setComposite(AlphaComposite.SrcOver);

        graphics.setColor(pointedAt ? JStudioTheme.getAccent() : JStudioTheme.getBorder());
        graphics.setStroke(new BasicStroke(pointedAt ? 2f : 1f));
        graphics.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);

        final Rectangle header = headerOf(bounds);
        graphics.setColor(pointedAt ? JStudioTheme.getAccent() : JStudioTheme.getBgSecondary());
        graphics.fillRect(header.x, header.y, header.width, header.height);

        graphics.setColor(pointedAt ? JStudioTheme.getBgPrimary() : JStudioTheme.getTextPrimary());
        graphics.setFont(JStudioTheme.getUIFont(11).deriveFont(Font.BOLD));
        final String name = Stacks.isShipped(pane.stack())
                ? Stacks.title(pane.stack())
                : String.join("  ·  ", pane.views().stream().map(Views::title).collect(Collectors.toList()));
        graphics.drawString(name, header.x + 8, header.y + header.height - 7);

        final Rectangle close = closeBoxOf(bounds);
        if (close.width > 0 && bounds.width > 80)
        {
            graphics.setColor(pointedAt ? JStudioTheme.getBgPrimary() : JStudioTheme.getTextSecondary());
            graphics.setStroke(new BasicStroke(1.4f));
            graphics.drawLine(close.x + 4, close.y + 4, close.x + close.width - 4, close.y + close.height - 4);
            graphics.drawLine(close.x + close.width - 4, close.y + 4, close.x + 4, close.y + close.height - 4);
        }
    }
}
