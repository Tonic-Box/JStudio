package com.tonic.ui.editor;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.JTabbedPane;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Owns live drag-to-reorder for tab headers: installs a press/drag/release adapter on a header (and its child
 * labels) and moves the tab as the cursor passes other tabs. The pinned Welcome tab (index 0) is never moved.
 */
final class TabDragController {

    private static final int DRAG_THRESHOLD = 5;

    private final JTabbedPane tabbedPane;

    TabDragController(JTabbedPane tabbedPane) {
        this.tabbedPane = tabbedPane;
    }

    /** Installs the drag handler on a tab header (and its child labels). The pinned Welcome tab (index 0) gets no handler. */
    void install(JPanel header, JComponent... extraTargets) {
        MouseAdapter drag = new MouseAdapter() {
            private Point pressPoint;
            private boolean dragging;

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger() || !SwingUtilities.isLeftMouseButton(e)) {
                    pressPoint = null;
                    return;
                }
                pressPoint = e.getPoint();
                dragging = false;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (pressPoint == null) {
                    return;
                }
                if (!dragging) {
                    if (Math.abs(e.getX() - pressPoint.x) < DRAG_THRESHOLD
                            && Math.abs(e.getY() - pressPoint.y) < DRAG_THRESHOLD) {
                        return;
                    }
                    dragging = true;
                    header.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
                Point inPane = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), tabbedPane);
                int to = tabbedPane.indexAtLocation(inPane.x, inPane.y);
                int from = tabbedPane.indexOfTabComponent(header);
                if (to >= 1 && from >= 1 && to != from) {
                    moveTab(from, to);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                pressPoint = null;
                if (dragging) {
                    dragging = false;
                    header.setCursor(Cursor.getDefaultCursor());
                }
            }
        };
        header.addMouseListener(drag);
        header.addMouseMotionListener(drag);
        for (JComponent target : extraTargets) {
            target.addMouseListener(drag);
            target.addMouseMotionListener(drag);
        }
    }

    /** Moves the tab at {@code from} to {@code to}, preserving header/content/icon/tooltip. Tab 0 (Welcome) is pinned. */
    private void moveTab(int from, int to) {
        int count = tabbedPane.getTabCount();
        if (from < 1 || to < 1 || from == to || from >= count || to >= count) {
            return;
        }
        String title = tabbedPane.getTitleAt(from);
        Icon icon = tabbedPane.getIconAt(from);
        String tip = tabbedPane.getToolTipTextAt(from);
        Component comp = tabbedPane.getComponentAt(from);
        Component header = tabbedPane.getTabComponentAt(from);
        tabbedPane.removeTabAt(from);
        tabbedPane.insertTab(title, icon, comp, tip, to);
        tabbedPane.setTabComponentAt(to, header);
        tabbedPane.setSelectedIndex(to);
    }
}
