package com.tonic.ui;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.component.ToolWindowPane;
import com.tonic.ui.core.component.ToolWindowPane.MoveTarget;
import com.tonic.ui.editor.EditorPanel;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Relocates a right-dock tool window (a {@link ToolWindowPane} stripe tool) into a center editor tab or a standalone
 * window, and returns it to the dock when that tab/window is closed (or its "Dock back" control is used). Session-only;
 * a tool lives in exactly one place at a time. EDT-only - the {@code active} map is both the registry and the guard:
 * a move ends either by <b>redock</b> (the tab/window closed - put it back) or <b>discard</b> (the dock no longer wants
 * it, e.g. a live tool whose session detached - just dispose it), and {@code active.remove} makes whichever runs first win.
 */
public final class ToolWindowMover {

    private static final String TAB_ID_PREFIX = "toolwindow:";

    private final ToolWindowPane dock;
    private final EditorPanel editor;
    private final JFrame owner;
    private final Map<String, MovedTool> active = new HashMap<>();

    public ToolWindowMover(ToolWindowPane dock, EditorPanel editor, JFrame owner) {
        this.dock = dock;
        this.editor = editor;
        this.owner = owner;
    }

    /** Moves the named dock tool to {@code target}. No-op if it isn't currently in the dock or is already moved. */
    public void move(String name, MoveTarget target) {
        if (active.containsKey(name)) {
            return;
        }
        JComponent component = dock.detachTool(name);
        if (component == null) {
            return;
        }
        if (target == MoveTarget.WINDOW) {
            moveToWindow(name, component);
        } else {
            moveToTab(name, component);
        }
    }

    private void moveToTab(String name, JComponent component) {
        String id = TAB_ID_PREFIX + name;
        JComponent wrapper = wrap(name, component, () -> editor.closeCustomView(id), null);
        active.put(name, new MovedTool(component, MoveTarget.TAB, id, null));
        editor.openCustomView(id, name, null, wrapper, () -> redock(name));
    }

    private void moveToWindow(String name, JComponent component) {
        JFrame frame = new JFrame(name);
        frame.setIconImages(owner.getIconImages());
        frame.setAlwaysOnTop(true);
        frame.setContentPane(wrap(name, component, frame::dispose, frame));
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(new Dimension(520, 640));
        frame.setLocationRelativeTo(owner);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                redock(name);
            }
        });
        active.put(name, new MovedTool(component, MoveTarget.WINDOW, null, frame));
        frame.setVisible(true);
        frame.toFront();
    }

    /** Returns a moved tool to the dock. Fired by the tab/window close path; no-op if already redocked or discarded. */
    private void redock(String name) {
        MovedTool moved = active.remove(name);
        if (moved == null) {
            return;
        }
        dock.addTool(name, moved.component);
        dock.select(name);
        moved.component.requestFocusInWindow();
    }

    /**
     * Force-closes a moved tool WITHOUT re-docking it (the dock no longer wants it - e.g. a live tool whose session
     * detached). The close path's redock then no-ops because the entry is already gone. No-op if not currently moved.
     */
    public void closeFloat(String name) {
        MovedTool moved = active.remove(name);
        if (moved == null) {
            return;
        }
        if (moved.target == MoveTarget.TAB) {
            editor.closeCustomView(moved.tabId);
        } else {
            moved.frame.dispose();
        }
    }

    /** Discards every active float (for application shutdown). */
    public void disposeAll() {
        for (String name : new ArrayList<>(active.keySet())) {
            closeFloat(name);
        }
    }

    /**
     * A thin themed bar (tool name on the left; an optional always-on-top toggle + a "Dock back" link on the right)
     * above the moved component. {@code floatWindow} is the standalone window when moved to one, else null (a tab).
     */
    private JComponent wrap(String name, JComponent component, Runnable onDockBack, JFrame floatWindow) {
        ThemedJPanel wrapper = new ThemedJPanel(ThemedJPanel.BackgroundStyle.PRIMARY, new BorderLayout());

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(JStudioTheme.getBgSecondary());
        bar.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));

        JLabel title = new JLabel(name);
        title.setForeground(JStudioTheme.getTextSecondary());
        title.setFont(JStudioTheme.getUIFont(11));

        JLabel dockBack = new JLabel("Dock back");
        dockBack.setForeground(JStudioTheme.getAccent());
        dockBack.setFont(JStudioTheme.getUIFont(11));
        dockBack.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        dockBack.setToolTipText("Return this tool to the side dock");
        dockBack.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onDockBack.run();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                dockBack.setText("<html><u>Dock back</u></html>");
            }

            @Override
            public void mouseExited(MouseEvent e) {
                dockBack.setText("Dock back");
            }
        });

        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        east.setOpaque(false);
        if (floatWindow != null) {
            east.add(createAlwaysOnTopToggle(floatWindow));
        }
        east.add(dockBack);

        bar.add(title, BorderLayout.WEST);
        bar.add(east, BorderLayout.EAST);
        wrapper.add(bar, BorderLayout.NORTH);
        wrapper.add(component, BorderLayout.CENTER);
        return wrapper;
    }

    /** A small "Always on top" text toggle that fills with a dark rounded box when selected (accent text), dim when off. */
    private JToggleButton createAlwaysOnTopToggle(JFrame window) {
        final Color selectedBg = JStudioTheme.getBgPrimary().darker();
        JToggleButton toggle = new JToggleButton("Always on top") {
            @Override
            protected void paintComponent(Graphics g) {
                if (isSelected()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(selectedBg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        toggle.setSelected(window.isAlwaysOnTop());
        toggle.setFont(JStudioTheme.getUIFont(11));
        toggle.setFocusable(false);
        toggle.setBorderPainted(false);
        toggle.setContentAreaFilled(false);
        toggle.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggle.setToolTipText("Keep this window above other windows");
        Runnable applyState = () -> {
            boolean on = toggle.isSelected();
            window.setAlwaysOnTop(on);
            toggle.setForeground(on ? JStudioTheme.getAccent() : JStudioTheme.getTextSecondary());
            toggle.repaint();
        };
        applyState.run();
        toggle.addActionListener(e -> applyState.run());
        return toggle;
    }

    private static final class MovedTool {
        final JComponent component;
        final MoveTarget target;
        final String tabId;
        final JFrame frame;

        MovedTool(JComponent component, MoveTarget target, String tabId, JFrame frame) {
            this.component = component;
            this.target = target;
            this.tabId = tabId;
            this.frame = frame;
        }
    }
}
