package com.tonic.ui.core.component;

import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;

public class LoadingOverlay extends JPanel implements ThemeChangeListener {

    private static final int SPINNER_SIZE = 32;
    private static final int SPINNER_THICKNESS = 3;
    private static final int ARC_ANGLE = 270;
    private static final int ANIMATION_DELAY = 40;

    private String message = "Loading...";
    private int rotation = 0;
    private Timer animationTimer;
    private boolean loading = false;

    public LoadingOverlay() {
        setOpaque(false);
        setVisible(false);
        ThemeManager.getInstance().addThemeChangeListener(this);

        animationTimer = new Timer(ANIMATION_DELAY, e -> {
            rotation = (rotation + 10) % 360;
            repaint();
        });
    }

    public void showLoading(String message) {
        this.message = message != null ? message : "Loading...";
        this.loading = true;
        this.rotation = 0;
        setVisible(true);
        animationTimer.start();
        repaint();
    }

    public void hideLoading() {
        this.loading = false;
        animationTimer.stop();
        setVisible(false);
    }

    public boolean isLoading() {
        return loading;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (!loading) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        Color bgColor = JStudioTheme.getBgPrimary();
        Color overlayColor = new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), 200);
        g2.setColor(overlayColor);
        g2.fillRect(0, 0, width, height);

        int centerX = width / 2;
        int centerY = height / 2;

        Color trackColor = JStudioTheme.getBorder();
        g2.setColor(trackColor);
        g2.setStroke(new BasicStroke(SPINNER_THICKNESS, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawOval(
                centerX - SPINNER_SIZE / 2,
                centerY - SPINNER_SIZE / 2 - 15,
                SPINNER_SIZE,
                SPINNER_SIZE
        );

        Color spinnerColor = JStudioTheme.getAccent();
        g2.setColor(spinnerColor);
        g2.setStroke(new BasicStroke(SPINNER_THICKNESS, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        Arc2D arc = new Arc2D.Double(
                centerX - SPINNER_SIZE / 2.0,
                centerY - SPINNER_SIZE / 2.0 - 15,
                SPINNER_SIZE,
                SPINNER_SIZE,
                rotation,
                ARC_ANGLE,
                Arc2D.OPEN
        );
        g2.draw(arc);

        g2.setColor(JStudioTheme.getTextPrimary());
        g2.setFont(JStudioTheme.getUIFont(13));
        FontMetrics fm = g2.getFontMetrics();
        int textWidth = fm.stringWidth(message);
        int textX = centerX - textWidth / 2;
        int textY = centerY + SPINNER_SIZE / 2 + 10;
        g2.drawString(message, textX, textY);

        g2.dispose();
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        repaint();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        animationTimer.stop();
        ThemeManager.getInstance().removeThemeChangeListener(this);
    }
}
