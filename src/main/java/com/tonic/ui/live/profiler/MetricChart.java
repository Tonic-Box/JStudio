package com.tonic.ui.live.profiler;

import com.tonic.ui.theme.JStudioTheme;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/**
 * A compact live time-series chart card: a title, a caller-set value readout, and one or more colored series
 * backed by fixed-capacity ring buffers. The first series is drawn as a filled area, the rest as lines. The
 * Y axis auto-scales to the window's max unless a fixed maximum is given (e.g. 100 for a percentage).
 * Push samples each tick and {@link #repaint()}.
 */
final class MetricChart extends JComponent {

    /** Samples retained per series (~3 minutes at one sample/second). */
    static final int CAPACITY = 180;

    private final String title;
    private final Double fixedMax;
    private final List<Series> series = new ArrayList<>();
    private String readout = "";

    MetricChart(String title, Double fixedMax) {
        this.title = title;
        this.fixedMax = fixedMax;
        Dimension size = new Dimension(10, 92);
        setPreferredSize(size);
        setMinimumSize(new Dimension(10, 92));
    }

    int addSeries(Color color) {
        series.add(new Series(color));
        return series.size() - 1;
    }

    void push(int seriesIndex, double value) {
        series.get(seriesIndex).push(value);
    }

    void setReadout(String text) {
        this.readout = text == null ? "" : text;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g.setColor(JStudioTheme.getBgSecondary());
        g.fillRect(0, 0, w, h);

        int pad = 7;
        int top = 22;
        int bottom = h - pad;
        int right = w - pad;
        int plotW = right - pad;
        int plotH = bottom - top;
        if (plotW <= 4 || plotH <= 4) {
            g.dispose();
            return;
        }

        double max = fixedMax != null ? fixedMax : 1.0;
        if (fixedMax == null) {
            for (Series s : series) {
                max = Math.max(max, s.windowMax());
            }
            max *= 1.15;
        }

        g.setColor(grid());
        g.drawLine(pad, bottom, right, bottom);

        for (int si = 0; si < series.size(); si++) {
            Series s = series.get(si);
            int n = s.size();
            if (n < 2) {
                continue;
            }
            int[] xs = new int[n];
            int[] ys = new int[n];
            for (int i = 0; i < n; i++) {
                xs[i] = pad + (int) Math.round(plotW * (i / (double) (CAPACITY - 1)));
                ys[i] = bottom - (int) Math.round(plotH * clamp(s.get(i) / max));
            }
            if (si == 0) {
                Polygon area = new Polygon();
                area.addPoint(xs[0], bottom);
                for (int i = 0; i < n; i++) {
                    area.addPoint(xs[i], ys[i]);
                }
                area.addPoint(xs[n - 1], bottom);
                g.setColor(alpha(s.color, 45));
                g.fillPolygon(area);
            }
            g.setColor(s.color);
            g.setStroke(new BasicStroke(1.4f));
            g.drawPolyline(xs, ys, n);
        }

        g.setFont(JStudioTheme.getUIFont(11));
        FontMetrics fm = g.getFontMetrics();
        int swatchX = pad;
        if (series.size() > 1) {
            for (Series s : series) {
                g.setColor(s.color);
                g.fillRect(swatchX, 6, 8, 8);
                swatchX += 12;
            }
        }
        g.setColor(JStudioTheme.getTextPrimary());
        g.drawString(title, swatchX + (series.size() > 1 ? 2 : 0), 15);
        if (!readout.isEmpty()) {
            g.setColor(JStudioTheme.getTextSecondary());
            g.drawString(readout, right - fm.stringWidth(readout), 15);
        }
        g.dispose();
    }

    private static double clamp(double v) {
        if (v < 0) {
            return 0;
        }
        return Math.min(v, 1.0);
    }

    private static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    private static Color grid() {
        Color c = JStudioTheme.getTextSecondary();
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), 60);
    }

    /** One series: a fixed-capacity ring of samples in arrival order. */
    private static final class Series {
        final Color color;
        private final double[] data = new double[CAPACITY];
        private int count;
        private int head;

        Series(Color color) {
            this.color = color;
        }

        void push(double value) {
            if (count < CAPACITY) {
                data[count++] = value;
            } else {
                data[head] = value;
                head = (head + 1) % CAPACITY;
            }
        }

        int size() {
            return count;
        }

        double get(int i) {
            return data[(head + i) % CAPACITY];
        }

        double windowMax() {
            double m = 0;
            for (int i = 0; i < count; i++) {
                m = Math.max(m, get(i));
            }
            return m;
        }
    }
}
