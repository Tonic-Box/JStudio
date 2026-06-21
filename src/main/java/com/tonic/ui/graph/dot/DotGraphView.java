package com.tonic.ui.graph.dot;

import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.view.mxGraph;
import com.tonic.graph.dot.DotGraph;
import com.tonic.graph.dot.DotParser;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * Renders a Graphviz DOT string as an inline, themed graph thumbnail scaled to the host's width; clicking opens an
 * interactive {@link DotGraphDialog}. The single entry point {@link #render(String)} returns a ready-to-embed
 * component (this view on success, or a raw-DOT fallback panel when the string can't be parsed), keeping
 * {@code com.mxgraph.*} off the caller's API. Self-themes via {@link ThemeManager}.
 */
public final class DotGraphView extends JPanel implements ThemeChangeListener {

    private static final int MAX_INLINE_HEIGHT = 360;
    private static final int PAD = 6;
    private static final double EXPORT_SCALE = 2.0;

    /** Renders {@code dot} as an inline interactive thumbnail (click opens a popup window), or a raw-source fallback. */
    public static JComponent render(String dot) {
        return render(dot, null);
    }

    /**
     * Renders {@code dot} as an inline thumbnail whose click is handled by {@code onOpen} (given the DOT source) - e.g.
     * to open the diagram as an editor tab instead of a popup. When {@code onOpen} is null the click opens the default
     * {@link DotGraphDialog} window. Returns a raw-source fallback panel if the DOT can't be parsed.
     */
    public static JComponent render(String dot, Consumer<String> onOpen) {
        try {
            DotGraph model = DotParser.parse(dot);
            if (model.isEmpty()) {
                return fallback(dot);
            }
            return new DotGraphView(dot, model, onOpen);
        } catch (RuntimeException e) {
            return fallback(dot);
        }
    }

    /** The full interactive (pan/zoom + toolbar) view of {@code dot}, for embedding as a tab/panel instead of a popup. */
    public static JComponent interactiveComponent(String dot) {
        return new DotGraphPanel(dot);
    }

    private final String dotSource;
    private final DotGraph model;
    private final Consumer<String> onOpen;
    private BufferedImage image;
    private int lastLayoutWidth = -1;

    private DotGraphView(String dotSource, DotGraph model, Consumer<String> onOpen) {
        this.dotSource = dotSource;
        this.model = model;
        this.onOpen = onOpen;
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("Click to open the diagram");
        setBorder(BorderFactory.createEmptyBorder(PAD, 0, PAD, 0));
        renderImage();
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openDiagram();
            }
        });
    }

    private void renderImage() {
        try {
            mxGraph graph = DotGraphBuilder.build(model);
            image = mxCellRenderer.createBufferedImage(graph, null, EXPORT_SCALE, null, true, null);
        } catch (RuntimeException e) {
            image = null;
        }
    }

    private void openDiagram() {
        if (onOpen != null) {
            onOpen.accept(dotSource);
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        new DotGraphDialog(owner, dotSource).setVisible(true);
    }

    @Override
    public Dimension getPreferredSize() {
        int width = currentWidth();
        if (image == null || width <= 0) {
            return new Dimension(Math.max(1, width), 40);
        }
        return new Dimension(width, scaledHeight(width) + 2 * PAD);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    private int currentWidth() {
        if (getWidth() > 0) {
            return getWidth();
        }
        return getParent() != null ? getParent().getWidth() : 0;
    }

    /** Height the scaled image occupies at {@code width} (downscale only, capped). */
    private int scaledHeight(int width) {
        double scale = Math.min(1.0, (double) width / image.getWidth());
        int height = (int) Math.round(image.getHeight() * scale);
        return Math.min(height, MAX_INLINE_HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image == null) {
            return;
        }
        int width = getWidth();
        double scale = Math.min(1.0, (double) width / image.getWidth());
        int drawW = (int) Math.round(image.getWidth() * scale);
        int drawH = (int) Math.round(image.getHeight() * scale);
        if (drawH > MAX_INLINE_HEIGHT) {
            double cap = (double) MAX_INLINE_HEIGHT / drawH;
            drawW = (int) Math.round(drawW * cap);
            drawH = MAX_INLINE_HEIGHT;
        }
        int x = (width - drawW) / 2;
        int y = PAD;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(JStudioTheme.getBgTertiary());
        g2.fillRoundRect(x - 4, y - 4, drawW + 8, drawH + 8, 10, 10);
        g2.drawImage(image, x, y, drawW, drawH, null);
        g2.setColor(JStudioTheme.getTextSecondary());
        g2.drawRoundRect(x - 4, y - 4, drawW + 8, drawH + 8, 10, 10);
        g2.dispose();
    }

    @Override
    public void doLayout() {
        super.doLayout();
        if (getWidth() != lastLayoutWidth) {
            lastLayoutWidth = getWidth();
            revalidate();
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    @Override
    public void removeNotify() {
        ThemeManager.getInstance().removeThemeChangeListener(this);
        super.removeNotify();
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        renderImage();
        revalidate();
        repaint();
    }

    /** A code-block-styled panel showing the raw DOT when it can't be rendered, so nothing is lost. */
    private static JComponent fallback(String dot) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(JStudioTheme.getBgTertiary());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JStudioTheme.getError(), 1),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        JTextArea area = new JTextArea(dot == null ? "" : dot.trim());
        area.setEditable(false);
        area.setOpaque(false);
        area.setFont(JStudioTheme.getCodeFont(12));
        area.setForeground(JStudioTheme.getTextPrimary());
        JScrollPane scroll = new JScrollPane(area,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        panel.add(label("dot diagram (couldn't be rendered)"), BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private static JComponent label(String text) {
        JTextArea l = new JTextArea(text);
        l.setEditable(false);
        l.setOpaque(false);
        l.setFont(JStudioTheme.getUIFont(11));
        l.setForeground(JStudioTheme.getTextSecondary());
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        return l;
    }
}
