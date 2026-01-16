package com.tonic.ui.editor.statistics.charts;

import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class StatCard extends JPanel implements ThemeChangeListener {

    private String value;
    private String label;
    private Color accentColor;
    private boolean hovered = false;

    private static final int ARC_SIZE = 12;

    public StatCard(String value, String label, Color accentColor) {
        this.value = value;
        this.label = label;
        this.accentColor = accentColor;

        setOpaque(false);
        setPreferredSize(new Dimension(140, 80));
        setMinimumSize(new Dimension(120, 70));

        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                hovered = false;
                repaint();
            }
        });

        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    public void setValue(String value) {
        this.value = value;
        repaint();
    }

    public void setLabel(String label) {
        this.label = label;
        repaint();
    }

    public void setAccentColor(Color accentColor) {
        this.accentColor = accentColor;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int w = getWidth();
        int h = getHeight();

        RoundRectangle2D.Float bg = new RoundRectangle2D.Float(1, 1, w - 2, h - 2, ARC_SIZE, ARC_SIZE);

        Color bgColor = hovered ? brighten(JStudioTheme.getBgSecondary(), 1.1f) : JStudioTheme.getBgSecondary();
        g2.setColor(bgColor);
        g2.fill(bg);

        g2.setColor(JStudioTheme.getBorder());
        g2.setStroke(new BasicStroke(1f));
        g2.draw(bg);

        Color valueColor = accentColor != null ? accentColor : JStudioTheme.getAccent();
        g2.setColor(valueColor);
        Font valueFont = JStudioTheme.getCodeFont(24).deriveFont(Font.BOLD);
        g2.setFont(valueFont);

        FontMetrics valueFm = g2.getFontMetrics();
        int valueWidth = valueFm.stringWidth(value);
        int valueX = (w - valueWidth) / 2;
        int valueY = h / 2;
        g2.drawString(value, valueX, valueY);

        g2.setColor(JStudioTheme.getTextSecondary());
        Font labelFont = JStudioTheme.getCodeFont(11);
        g2.setFont(labelFont);

        FontMetrics labelFm = g2.getFontMetrics();
        int labelWidth = labelFm.stringWidth(label);
        int labelX = (w - labelWidth) / 2;
        int labelY = valueY + valueFm.getDescent() + labelFm.getAscent() + 4;
        g2.drawString(label, labelX, labelY);

        g2.dispose();
    }

    private Color brighten(Color color, float factor) {
        int r = Math.min(255, (int) (color.getRed() * factor));
        int g = Math.min(255, (int) (color.getGreen() * factor));
        int b = Math.min(255, (int) (color.getBlue() * factor));
        return new Color(r, g, b);
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        repaint();
    }
}
