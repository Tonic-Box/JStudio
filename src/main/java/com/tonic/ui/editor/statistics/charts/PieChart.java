package com.tonic.ui.editor.statistics.charts;

import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;
import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;

public class PieChart extends JPanel implements ThemeChangeListener {

    private final String title;
    private String centerLabel;
    private List<PieSlice> slices = new ArrayList<>();

    private static final int PADDING = 12;
    private static final int TITLE_HEIGHT = 28;
    private static final int LEGEND_ITEM_HEIGHT = 18;
    private static final float DONUT_THICKNESS = 0.35f;

    public PieChart(String title) {
        this.title = title;
        setOpaque(false);
        setPreferredSize(new Dimension(250, 220));
        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    public void setData(List<PieSlice> slices) {
        this.slices = slices != null ? new ArrayList<>(slices) : new ArrayList<>();
        repaint();
    }

    public void setCenterLabel(String centerLabel) {
        this.centerLabel = centerLabel;
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

        g2.setColor(JStudioTheme.getBgSecondary());
        g2.fillRoundRect(0, 0, w, h, 8, 8);

        g2.setColor(JStudioTheme.getBorder());
        g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);

        g2.setColor(JStudioTheme.getTextPrimary());
        g2.setFont(JStudioTheme.getCodeFont(12).deriveFont(Font.BOLD));
        g2.drawString(title, PADDING, PADDING + 14);

        if (slices.isEmpty()) {
            g2.setColor(JStudioTheme.getTextSecondary());
            g2.setFont(JStudioTheme.getCodeFont(11));
            g2.drawString("No data available", PADDING, TITLE_HEIGHT + PADDING + 20);
            g2.dispose();
            return;
        }

        double total = slices.stream().mapToDouble(PieSlice::getValue).sum();
        if (total == 0) {
            g2.setColor(JStudioTheme.getTextSecondary());
            g2.setFont(JStudioTheme.getCodeFont(11));
            g2.drawString("No data available", PADDING, TITLE_HEIGHT + PADDING + 20);
            g2.dispose();
            return;
        }

        int legendHeight = slices.size() * LEGEND_ITEM_HEIGHT + PADDING;
        int availableHeight = h - TITLE_HEIGHT - legendHeight - PADDING * 2;
        int availableWidth = w - PADDING * 2;
        int diameter = Math.min(availableWidth, availableHeight);
        diameter = Math.max(diameter, 80);

        int pieX = (w - diameter) / 2;
        int pieY = TITLE_HEIGHT + PADDING;

        int innerDiameter = (int) (diameter * (1 - DONUT_THICKNESS * 2));
        int innerX = pieX + (diameter - innerDiameter) / 2;
        int innerY = pieY + (diameter - innerDiameter) / 2;

        double startAngle = 90;
        for (PieSlice slice : slices) {
            double sweepAngle = (slice.getValue() / total) * 360;

            g2.setColor(slice.getColor());
            Arc2D.Double arc = new Arc2D.Double(pieX, pieY, diameter, diameter, startAngle, -sweepAngle, Arc2D.PIE);
            g2.fill(arc);

            startAngle -= sweepAngle;
        }

        g2.setColor(JStudioTheme.getBgSecondary());
        g2.fill(new Ellipse2D.Double(innerX, innerY, innerDiameter, innerDiameter));

        if (centerLabel != null && !centerLabel.isEmpty()) {
            g2.setColor(JStudioTheme.getTextPrimary());
            g2.setFont(JStudioTheme.getCodeFont(14).deriveFont(Font.BOLD));
            FontMetrics fm = g2.getFontMetrics();
            int labelWidth = fm.stringWidth(centerLabel);
            int labelX = pieX + (diameter - labelWidth) / 2;
            int labelY = pieY + diameter / 2 + fm.getAscent() / 3;
            g2.drawString(centerLabel, labelX, labelY);
        }

        int legendY = pieY + diameter + PADDING;
        int itemsPerRow = 3;
        int itemWidth = (w - PADDING * 2) / itemsPerRow;

        g2.setFont(JStudioTheme.getCodeFont(10));
        for (int i = 0; i < slices.size(); i++) {
            PieSlice slice = slices.get(i);
            int row = i / itemsPerRow;
            int col = i % itemsPerRow;

            int x = PADDING + col * itemWidth;
            int y = legendY + row * LEGEND_ITEM_HEIGHT;

            g2.setColor(slice.getColor());
            g2.fillRoundRect(x, y, 10, 10, 2, 2);

            g2.setColor(JStudioTheme.getTextPrimary());
            double percentage = (slice.getValue() / total) * 100;
            String legendText = String.format("%s (%.0f%%)", slice.getLabel(), percentage);
            g2.drawString(truncateLabel(legendText, g2.getFontMetrics(), itemWidth - 18), x + 14, y + 9);
        }

        g2.dispose();
    }

    private String truncateLabel(String label, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(label) <= maxWidth) {
            return label;
        }
        String ellipsis = "...";
        int ellipsisWidth = fm.stringWidth(ellipsis);
        int availableWidth = maxWidth - ellipsisWidth;

        StringBuilder sb = new StringBuilder();
        for (char c : label.toCharArray()) {
            if (fm.stringWidth(sb.toString() + c) > availableWidth) {
                break;
            }
            sb.append(c);
        }
        return sb + ellipsis;
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        repaint();
    }

    @Getter
    @AllArgsConstructor
    public static class PieSlice {
        private final String label;
        private final double value;
        private final Color color;
    }
}
