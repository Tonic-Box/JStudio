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

/**
 * Renders breakpoint dots in an editor view's gutter and toggles a breakpoint when the gutter's icon strip is
 * left-clicked. The breakpoint the target is currently paused at shows a yellow pause badge instead, and
 * clicking it resumes (right-click still removes it). The view-specific line/offset mapping is supplied by a
 * {@link BreakpointMapper}, so the same controller drives both the source and bytecode views; dots are rendered
 * from the shared {@link BreakpointService}.
 */
public final class BreakpointGutterController {

    private static final Icon BREAKPOINT_ICON = new Icon() {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0xE0, 0x4A, 0x40));
            g2.fillOval(x, y, getIconWidth(), getIconHeight());
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 11;
        }

        @Override
        public int getIconHeight() {
            return 11;
        }
    };

    /** Shown on the breakpoint that is currently hit (paused): a yellow pause badge - click it to resume. */
    private static final Icon PAUSE_ICON = new Icon() {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
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
        public int getIconWidth() {
            return 11;
        }

        @Override
        public int getIconHeight() {
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

    public BreakpointGutterController(RSyntaxTextArea textArea, RTextScrollPane scrollPane, BreakpointMapper mapper) {
        this.textArea = textArea;
        this.scrollPane = scrollPane;
        this.mapper = mapper;
        this.mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                int line = lineAt(e);
                if (line <= 0) {
                    return;
                }
                // Clicking the paused line resumes - even if its breakpoint was removed mid-pause, so the
                // resume affordance never disappears while suspended. Any other line toggles a breakpoint.
                if (line == pausedLineInView()) {
                    DebugManager.getInstance().resume();
                    return;
                }
                Breakpoint bp = mapper.breakpointAtLine(line);
                if (bp != null) {
                    BreakpointService.getInstance().toggle(bp);
                }
            }
        };
        wireGutter();
    }

    /**
     * The breakpoint a right-click on {@code line} (1-based) would toggle, or null when the line isn't an
     * executable location. Breakpoints are settable any time (even before attach) and arm on connect.
     */
    public Breakpoint breakpointAt(int line) {
        return mapper.breakpointAtLine(line);
    }

    public boolean isSet(Breakpoint bp) {
        return BreakpointService.getInstance().contains(bp);
    }

    public void toggle(Breakpoint bp) {
        BreakpointService.getInstance().toggle(bp);
    }

    /** Subscribes to breakpoint/session changes and renders existing dots; call from the host view's addNotify. */
    public void attach() {
        EventBus.getInstance().register(BreakpointsChangedEvent.class, bpHandler);
        EventBus.getInstance().register(DebugSessionEvent.class, sessionHandler);
        EventBus.getInstance().register(DebugPausedEvent.class, pausedHandler);
        EventBus.getInstance().register(DebugResumedEvent.class, resumedHandler);
        updateIcons();
    }

    public void detach() {
        EventBus.getInstance().unregister(BreakpointsChangedEvent.class, bpHandler);
        EventBus.getInstance().unregister(DebugSessionEvent.class, sessionHandler);
        EventBus.getInstance().unregister(DebugPausedEvent.class, pausedHandler);
        EventBus.getInstance().unregister(DebugResumedEvent.class, resumedHandler);
    }

    public void updateIcons() {
        wireGutter();
        Gutter gutter = scrollPane.getGutter();
        for (GutterIconInfo info : icons) {
            gutter.removeTrackingIcon(info);
        }
        icons.clear();
        int pausedLine = pausedLineInView();
        for (Breakpoint bp : BreakpointService.getInstance().forClass(mapper.className())) {
            int line = mapper.lineForBreakpoint(bp);
            if (line <= 0 || line == pausedLine) {
                continue;
            }
            try {
                icons.add(gutter.addLineTrackingIcon(line - 1, BREAKPOINT_ICON, "Breakpoint"));
            } catch (BadLocationException ignored) {
            }
        }
        // The paused line always carries the pause/resume badge - on top of (or without) a breakpoint - so
        // removing the breakpoint mid-pause leaves the resume affordance intact.
        if (pausedLine > 0) {
            try {
                icons.add(gutter.addLineTrackingIcon(pausedLine - 1, PAUSE_ICON, "Paused here - click to resume"));
            } catch (BadLocationException ignored) {
            }
        }
    }

    /** The 1-based line in THIS view where the target is currently paused, or -1 if it isn't paused here. */
    private int pausedLineInView() {
        DebugLocation loc = DebugManager.getInstance().getPausedLocation();
        if (loc == null || !mapper.className().equals(loc.getClassName())) {
            return -1;
        }
        return mapper.lineForBreakpoint(new Breakpoint(
                loc.getClassName(), loc.getMethodName(), loc.getMethodDescriptor(), loc.getCodeIndex()));
    }

    private void refresh() {
        SwingUtilities.invokeLater(this::updateIcons);
    }

    /**
     * Wires the click handler onto the gutter's icon strip only (not the line-number or fold areas). Removes
     * before adding so it stays EXACTLY one listener even after a re-decompile re-creates/re-adds the gutter
     * components - otherwise a click would toggle twice (set then unset) and the breakpoint would never take.
     */
    private void wireGutter() {
        Gutter gutter = scrollPane.getGutter();
        for (Component child : gutter.getComponents()) {
            if (child.getClass().getSimpleName().contains("IconRowHeader")) {
                child.removeMouseListener(mouse);
                child.addMouseListener(mouse);
            }
        }
    }

    private int lineAt(MouseEvent e) {
        Point inText = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), textArea);
        int offset = textArea.viewToModel2D(inText);
        if (offset < 0) {
            return -1;
        }
        try {
            return textArea.getLineOfOffset(offset) + 1;
        } catch (BadLocationException ex) {
            return -1;
        }
    }
}
