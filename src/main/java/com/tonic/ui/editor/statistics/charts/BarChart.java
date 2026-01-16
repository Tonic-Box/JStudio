package com.tonic.ui.editor.statistics.charts;

import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;
import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

public class BarChart extends JPanel implements ThemeChangeListener {

    private final String title;
    private List<BarEntry> entries = new ArrayList<>();
    private boolean percentageMode = false;
    private boolean showAllEntries = false;
    private int maxVisibleBars = 8;

    private static final int BAR_HEIGHT = 22;
    private static final int BAR_SPACING = 6;
    private static final int LABEL_WIDTH = 100;
    private static final int VALUE_WIDTH = 60;
    private static final int PADDING = 12;
    private static final int TITLE_HEIGHT = 28;
    private static final int BAR_RADIUS = 4;

    public BarChart(String title) {
        this.title = title;
        setOpaque(false);
        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    public void setData(List<BarEntry> entries) {
        this.entries = entries != null ? new ArrayList<>(entries) : new ArrayList<>();
        updatePreferredSize();
        repaint();
    }

    public void setPercentageMode(boolean percentageMode) {
        this.percentageMode = percentageMode;
        repaint();
    }

    public void setShowAllEntries(boolean showAll) {
        this.showAllEntries = showAll;
        updatePreferredSize();
        repaint();
    }

    private void updatePreferredSize() {
        int visibleCount = showAllEntries ? entries.size() : Math.min(entries.size(), maxVisibleBars);
        int height = TITLE_HEIGHT + PADDING * 2 + visibleCount * (BAR_HEIGHT + BAR_SPACING);
        if (!showAllEntries && entries.size() > maxVisibleBars) {
            height += 20;
        }
        setPreferredSize(new Dimension(300, Math.max(height, 100)));
        revalidate();
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

        if (entries.isEmpty()) {
            g2.setColor(JStudioTheme.getTextSecondary());
            g2.setFont(JStudioTheme.getCodeFont(11));
            g2.drawString("No data available", PADDING, TITLE_HEIGHT + PADDING + 20);
            g2.dispose();
            return;
        }

        double maxValue = entries.stream().mapToDouble(BarEntry::getValue).max().orElse(1);
        double total = entries.stream().mapToDouble(BarEntry::getValue).sum();

        int y = TITLE_HEIGHT + PADDING;
        int barAreaWidth = w - LABEL_WIDTH - VALUE_WIDTH - PADDING * 3;

        int visibleCount = showAllEntries ? entries.size() : Math.min(entries.size(), maxVisibleBars);
        for (int i = 0; i < visibleCount; i++) {
            BarEntry entry = entries.get(i);
            double value = entry.getValue();
            double percentage = total > 0 ? (value / total) * 100 : 0;
            double barWidth = (value / maxValue) * barAreaWidth;

            g2.setColor(JStudioTheme.getTextPrimary());
            g2.setFont(JStudioTheme.getCodeFont(10));
            String label = truncateLabel(entry.getLabel(), g2.getFontMetrics(), LABEL_WIDTH - 8);
            g2.drawString(label, PADDING, y + BAR_HEIGHT / 2 + 4);

            int barX = PADDING + LABEL_WIDTH;
            int barY = y + 2;

            g2.setColor(darken(JStudioTheme.getBgTertiary(), 0.95f));
            g2.fillRoundRect(barX, barY, barAreaWidth, BAR_HEIGHT - 4, BAR_RADIUS, BAR_RADIUS);

            if (barWidth > 0) {
                Color barColor = entry.getColor() != null ? entry.getColor() : JStudioTheme.getAccent();
                GradientPaint gradient = new GradientPaint(
                        barX, barY, barColor,
                        barX + (float) barWidth, barY, brighten(barColor, 1.2f)
                );
                g2.setPaint(gradient);
                g2.fill(new RoundRectangle2D.Double(barX, barY, Math.max(barWidth, BAR_RADIUS * 2), BAR_HEIGHT - 4, BAR_RADIUS, BAR_RADIUS));
            }

            g2.setColor(JStudioTheme.getTextSecondary());
            g2.setFont(JStudioTheme.getCodeFont(10));
            String valueStr;
            if (percentageMode) {
                valueStr = String.format("%.1f%%", percentage);
            } else {
                valueStr = formatValue((int) value);
            }
            int valueX = w - PADDING - VALUE_WIDTH + 8;
            g2.drawString(valueStr, valueX, y + BAR_HEIGHT / 2 + 4);

            y += BAR_HEIGHT + BAR_SPACING;
        }

        if (!showAllEntries && entries.size() > maxVisibleBars) {
            g2.setColor(JStudioTheme.getTextSecondary());
            g2.setFont(JStudioTheme.getCodeFont(10).deriveFont(Font.ITALIC));
            g2.drawString("+" + (entries.size() - maxVisibleBars) + " more...", PADDING, y + 12);
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

    private String formatValue(int value) {
        if (value >= 1000000) {
            return String.format("%.1fM", value / 1000000.0);
        } else if (value >= 1000) {
            return String.format("%.1fK", value / 1000.0);
        }
        return String.valueOf(value);
    }

    private Color brighten(Color color, float factor) {
        int r = Math.min(255, (int) (color.getRed() * factor));
        int g = Math.min(255, (int) (color.getGreen() * factor));
        int b = Math.min(255, (int) (color.getBlue() * factor));
        return new Color(r, g, b);
    }

    private Color darken(Color color, float factor) {
        int r = (int) (color.getRed() * factor);
        int g = (int) (color.getGreen() * factor);
        int b = (int) (color.getBlue() * factor);
        return new Color(r, g, b);
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        repaint();
    }

    @Getter
    @AllArgsConstructor
    public static class BarEntry {
        private final String label;
        private final double value;
        private final Color color;

        public BarEntry(String label, double value) {
            this(label, value, null);
        }
    }
}
