package com.tonic.ui.live.recorder.jfr;

import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A table cell renderer that formats numbers (counts / bytes / millis) right-aligned and draws a subtle
 * proportional bar behind the value (longest value in the column = full width), with alternating row tint -
 * turning a plain table into a profiler-style ranked view. Text columns ({@link Kind#TEXT}) are left-aligned
 * with the same tint and no bar.
 */
final class BarTableCellRenderer extends DefaultTableCellRenderer {

    enum Kind {
        TEXT, COUNT, BYTES, MILLIS
    }

    private final Kind kind;
    private final double max;
    private double value;
    private int rowIndex;
    private boolean selected;

    private BarTableCellRenderer(Kind kind, double max) {
        this.kind = kind;
        this.max = max;
        setOpaque(false);
        setHorizontalAlignment(kind == Kind.TEXT ? LEFT : RIGHT);
        setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
    }

    /** Installs this renderer on every column of {@code table}, computing each numeric column's max for bars. */
    static void install(JTable table, Kind[] kinds) {
        TableModel model = table.getModel();
        for (int col = 0; col < kinds.length && col < table.getColumnCount(); col++) {
            double max = kinds[col] == Kind.TEXT ? 0 : columnMax(model, col);
            table.getColumnModel().getColumn(col).setCellRenderer(new BarTableCellRenderer(kinds[col], max));
        }
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setRowHeight(24);
    }

    private static double columnMax(TableModel model, int col) {
        double max = 0;
        for (int r = 0; r < model.getRowCount(); r++) {
            Object v = model.getValueAt(r, col);
            if (v instanceof Number) {
                max = Math.max(max, ((Number) v).doubleValue());
            }
        }
        return max;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object cellValue, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, cellValue, isSelected, hasFocus, row, column);
        this.value = cellValue instanceof Number ? ((Number) cellValue).doubleValue() : 0;
        this.rowIndex = row;
        this.selected = isSelected;
        setText(format(cellValue));
        setForeground(JStudioTheme.getTextPrimary());
        return this;
    }

    private String format(Object cellValue) {
        if (!(cellValue instanceof Number)) {
            return cellValue == null ? "" : cellValue.toString();
        }
        double v = ((Number) cellValue).doubleValue();
        switch (kind) {
            case BYTES:
                return JfrFormat.bytes(v);
            case MILLIS:
                return JfrFormat.millis(v);
            case COUNT:
                return JfrFormat.count(v);
            default:
                return cellValue.toString();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(selected ? JStudioTheme.getSelection() : rowBackground());
        g2.fillRect(0, 0, getWidth(), getHeight());

        if (max > 0 && value > 0) {
            int barWidth = (int) Math.round(getWidth() * Math.min(1.0, value / max));
            Color accent = JStudioTheme.getAccent();
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), selected ? 90 : 55));
            g2.fillRect(0, 2, barWidth, getHeight() - 4);
        }
        g2.dispose();
        super.paintComponent(g);
    }

    private Color rowBackground() {
        Color base = JStudioTheme.getBgSecondary();
        if (rowIndex % 2 == 0) {
            return base;
        }
        Color text = JStudioTheme.getTextPrimary();
        return mix(base, text, 0.05);
    }

    private static Color mix(Color a, Color b, double t) {
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
