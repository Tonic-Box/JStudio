package com.tonic.ui.theme;

import javax.swing.Icon;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Decorates a base icon with a small green "play" badge in the bottom-right corner, marking a class that has a
 * runnable {@code main} entry point (IntelliJ-style).
 */
public final class RunnableOverlayIcon implements Icon {

    private final Icon base;

    public RunnableOverlayIcon(Icon base) {
        this.base = base;
    }

    @Override
    public int getIconWidth() {
        return base.getIconWidth();
    }

    @Override
    public int getIconHeight() {
        return base.getIconHeight();
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        base.paintIcon(c, g, x, y);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getIconWidth();
        int h = getIconHeight();
        int badge = Math.max(7, w / 2);
        int bx = x + w - badge;
        int by = y + h - badge;

        // Background disc for contrast against the base icon, then the green triangle.
        g2.setColor(JStudioTheme.getBgPrimary());
        g2.fillOval(bx - 1, by - 1, badge + 2, badge + 2);
        g2.setColor(JStudioTheme.getSuccess());
        int pad = Math.max(1, badge / 5);
        int[] xs = {bx + pad, bx + pad, bx + badge - pad};
        int[] ys = {by + pad, by + badge - pad, by + badge / 2};
        g2.fillPolygon(xs, ys, 3);
        g2.dispose();
    }
}
