package com.tonic.ui.layout;

import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import javax.swing.JWindow;

final class DragGhost
{

    private static final int OFFSET = 14;

    private static final float OPACITY = 0.82f;

    private static final int PADDING = 6;

    private final JWindow window = new JWindow();
    private final Face face = new Face();

    DragGhost()
    {
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(false);
        window.setContentPane(face);
        try
        {
            window.setOpacity(OPACITY);
        }
        catch (RuntimeException translucencyUnsupported)
        {
            window.setOpacity(1f);
        }
    }

    void carry(BufferedImage tab, boolean toItsOwnWindow)
    {
        face.tab = tab;
        face.toItsOwnWindow = toItsOwnWindow;
        window.setSize(face.wanted());
        window.repaint();
    }

    void moveTo(Point onScreen)
    {
        window.setLocation(onScreen.x + OFFSET, onScreen.y + OFFSET);
        if (!window.isVisible())
        {
            window.setVisible(true);
        }
    }

    void hide()
    {
        window.setVisible(false);
    }

    boolean isShowing()
    {
        return window.isVisible();
    }

    void dispose()
    {
        window.dispose();
    }

    private static final class Face extends JPanel
    {

        private static final long serialVersionUID = 1L;

        private static final String NEW_WINDOW = "New window";

        private transient BufferedImage tab;
        private boolean toItsOwnWindow;

        Face()
        {
            setOpaque(true);
        }

        Dimension wanted()
        {
            final int wide = Math.max(tab == null ? 0 : tab.getWidth(), toItsOwnWindow ? captionWidth() : 0);
            final int high = (tab == null ? 0 : tab.getHeight())
                    + (toItsOwnWindow ? UIConstants.FONT_SIZE_CODE + PADDING : 0);
            return new Dimension(wide + PADDING * 2, high + PADDING * 2);
        }

        private int captionWidth()
        {
            return getFontMetrics(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_SMALL).deriveFont(Font.BOLD)).stringWidth(NEW_WINDOW);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            final Graphics2D graphics = (Graphics2D) g.create();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            final Color edge = toItsOwnWindow ? JStudioTheme.getAccent() : JStudioTheme.getBorder();
            graphics.setColor(JStudioTheme.getBgSecondary());
            graphics.fillRect(0, 0, getWidth(), getHeight());
            graphics.setColor(edge);
            graphics.drawRect(0, 0, getWidth() - 1, getHeight() - 1);

            int y = PADDING;
            if (tab != null)
            {
                graphics.drawImage(tab, PADDING, y, null);
                y += tab.getHeight();
            }
            if (toItsOwnWindow)
            {
                graphics.setColor(JStudioTheme.getAccent());
                graphics.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_SMALL).deriveFont(Font.BOLD));
                graphics.drawString(NEW_WINDOW, PADDING, y + PADDING + graphics.getFontMetrics().getAscent() - 2);
            }
            graphics.dispose();
        }
    }
}
