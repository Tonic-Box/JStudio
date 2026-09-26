package com.tonic.ui.debug;

import com.tonic.event.EventBus;
import com.tonic.event.events.BreakpointsChangedEvent;
import com.tonic.event.events.DebugPausedEvent;
import com.tonic.event.events.DebugResumedEvent;
import com.tonic.event.events.DebugSessionEvent;
import com.tonic.live.debug.DebugLocation;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.GutterIconInfo;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.Icon;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/** Draws breakpoint dots in one editor view's gutter and toggles them on left-click; the paused line shows a pause badge that resumes when clicked. */
public final class BreakpointGutterController
{

    private static final Icon BREAKPOINT_ICON = new Icon()
    {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0xE0, 0x4A, 0x40));
            g2.fillOval(x, y, getIconWidth(), getIconHeight());
            g2.dispose();
        }

        @Override
        public int getIconWidth()
        {
            return 11;
        }

        @Override
        public int getIconHeight()
        {
            return 11;
        }
    };

    private static final Icon PAUSE_ICON = new Icon()
    {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getIconWidth();
            int h = getIconHeight();
            g2.setColor(new Color(0xF2, 0xB8, 0x0C));
            g2.fillOval(x, y, w, h);
            g2.setColor(new Color(0x2A, 0x22, 0x00));
            int barH = h - 5;
            int barY = y + (h - barH) / 2;
            g2.fillRect(x + w / 2 - 3, barY, 2, barH);
            g2.fillRect(x + w / 2 + 1, barY, 2, barH);
            g2.dispose();
        }

        @Override
        public int getIconWidth()
        {
            return 11;
        }

        @Override
        public int getIconHeight()
        {
            return 11;
        }
    };

    private final RSyntaxTextArea textArea;
    private final RTextScrollPane scrollPane;
    private final BreakpointMapper mapper;

    private final List<GutterIconInfo> icons = new ArrayList<>();
    private final MouseAdapter mouse;
    private final EventBus.EventHandler<BreakpointsChangedEvent> bpHandler = e -> refresh();
    private final EventBus.EventHandler<DebugSessionEvent> sessionHandler = e -> refresh();
    private final EventBus.EventHandler<DebugPausedEvent> pausedHandler = e -> refresh();
    private final EventBus.EventHandler<DebugResumedEvent> resumedHandler = e -> refresh();

    /**
     * Creates the controller and hooks its click handler into the gutter's icon strip.
     *
     * @param textArea the editor whose lines the gutter follows
     * @param scrollPane the scroll pane that owns the gutter
     * @param mapper the view's line-to-breakpoint mapping
     */
    public BreakpointGutterController(RSyntaxTextArea textArea, RTextScrollPane scrollPane, BreakpointMapper mapper)
    {
        this.textArea = textArea;
        this.scrollPane = scrollPane;
        this.mapper = mapper;
        this.mouse = new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getButton() != MouseEvent.BUTTON1)
                {
                    return;
                }
                int line = lineAt(e);
                if (line <= 0)
                {
                    return;
                }
                if (line == pausedLineInView())
                {
                    DebugManager.getInstance().resume();
                    return;
                }
                Breakpoint bp = mapper.breakpointAtLine(line);
                if (bp != null)
                {
                    BreakpointService.getInstance().toggle(bp);
                }
            }
        };
        wireGutter();
    }

    /**
     * Finds the breakpoint a line would toggle.
     *
     * @param line the 1-based line in the view
     * @return the breakpoint, or null if the line is not executable
     */
    public Breakpoint breakpointAt(int line)
    {
        return mapper.breakpointAtLine(line);
    }

    /**
     * Tells whether a breakpoint is set.
     *
     * @param bp the breakpoint
     * @return true if it is in the shared registry
     */
    public boolean isSet(Breakpoint bp)
    {
        return BreakpointService.getInstance().contains(bp);
    }

    /**
     * Toggles a breakpoint in the shared registry.
     *
     * @param bp the breakpoint
     */
    public void toggle(Breakpoint bp)
    {
        BreakpointService.getInstance().toggle(bp);
    }

    /** Subscribes to breakpoint/session changes and renders existing dots; call from the host view's addNotify. */
    public void attach()
    {
        EventBus.getInstance().register(BreakpointsChangedEvent.class, bpHandler);
        EventBus.getInstance().register(DebugSessionEvent.class, sessionHandler);
        EventBus.getInstance().register(DebugPausedEvent.class, pausedHandler);
        EventBus.getInstance().register(DebugResumedEvent.class, resumedHandler);
        updateIcons();
    }

    /** Unsubscribes from breakpoint and debug events; call from the host view's removeNotify. */
    public void detach()
    {
        EventBus.getInstance().unregister(BreakpointsChangedEvent.class, bpHandler);
        EventBus.getInstance().unregister(DebugSessionEvent.class, sessionHandler);
        EventBus.getInstance().unregister(DebugPausedEvent.class, pausedHandler);
        EventBus.getInstance().unregister(DebugResumedEvent.class, resumedHandler);
    }

    /** Redraws the gutter icons for this view's class, placing the pause badge on the paused line in place of its dot. */
    public void updateIcons()
    {
        wireGutter();
        Gutter gutter = scrollPane.getGutter();
        for (GutterIconInfo info : icons)
        {
            gutter.removeTrackingIcon(info);
        }
        icons.clear();
        int pausedLine = pausedLineInView();
        for (Breakpoint bp : BreakpointService.getInstance().forClass(mapper.className()))
        {
            int line = mapper.lineForBreakpoint(bp);
            if (line <= 0 || line == pausedLine)
            {
                continue;
            }
            try
            {
                icons.add(gutter.addLineTrackingIcon(line - 1, BREAKPOINT_ICON, "Breakpoint"));
            }
            catch (BadLocationException ignored)
            {
            }
        }
        if (pausedLine > 0)
        {
            try
            {
                icons.add(gutter.addLineTrackingIcon(pausedLine - 1, PAUSE_ICON, "Paused here - click to resume"));
            }
            catch (BadLocationException ignored)
            {
            }
        }
    }

    private int pausedLineInView()
    {
        DebugLocation loc = DebugManager.getInstance().getPausedLocation();
        if (loc == null || !mapper.className().equals(loc.getClassName()))
        {
            return -1;
        }
        return mapper.lineForBreakpoint(new Breakpoint(loc.getClassName(), loc.getMethodName(), loc.getMethodDescriptor(), loc.getCodeIndex()));
    }

    private void refresh()
    {
        SwingUtilities.invokeLater(this::updateIcons);
    }

    private void wireGutter()
    {
        Gutter gutter = scrollPane.getGutter();
        for (Component child : gutter.getComponents())
        {
            if (child.getClass().getSimpleName().contains("IconRowHeader"))
            {
                child.removeMouseListener(mouse);
                child.addMouseListener(mouse);
            }
        }
    }

    private int lineAt(MouseEvent e)
    {
        Point inText = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), textArea);
        int offset = textArea.viewToModel2D(inText);
        if (offset < 0)
        {
            return -1;
        }
        try
        {
            return textArea.getLineOfOffset(offset) + 1;
        }
        catch (BadLocationException ex)
        {
            return -1;
        }
    }
}
