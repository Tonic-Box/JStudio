package com.tonic.ui.live.recorder.jfr;

import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.JComponent;
import javax.swing.Scrollable;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongFunction;

/**
 * A flame graph (top-down icicle) for a {@link CallTreeNode} call tree: each node is a rounded bar whose width
 * is proportional to its total weight, stacked by call depth (root at the top, callees below). Single-click a
 * bar to zoom in (click the top bar to step back out); hover highlights a bar; double-click activates the frame
 * (source navigation). Weight is rendered via the supplied formatter (samples / bytes / time), so one widget
 * serves the CPU, allocation and lock views.
 */
public final class FlameGraphPanel extends JComponent implements ThemeChangeListener, Scrollable {

    private static final int ROW_HEIGHT = 20;
    private static final int GAP = 1;
    private static final int ARC = 6;
    /** Frames narrower than this are not drawn (unreadable/unclickable slivers); zoom in to reveal them. */
    private static final int MIN_WIDTH = 3;

    private final CallTreeNode trueRoot;
    private final LongFunction<String> weightFormat;
    private final List<Box> boxes = new ArrayList<>();
    private final List<CallTreeNode> path = new ArrayList<>();
    private final Timer clickTimer;

    private CallTreeNode hovered;
    private Box pendingZoom;
    private Runnable onZoomChanged;

    public FlameGraphPanel(CallTreeNode root, LongFunction<String> weightFormat, Consumer<FrameKey> onActivate) {
        this.trueRoot = root;
        this.weightFormat = weightFormat;
        this.path.add(root);
        setFont(JStudioTheme.getUIFont(11));
        ToolTipManager.sharedInstance().registerComponent(this);
        ThemeManager.getInstance().addThemeChangeListener(this);
        updatePreferredHeight();

        clickTimer = new Timer(doubleClickInterval(), e -> {
            if (pendingZoom != null) {
                zoom(pendingZoom.node);
                pendingZoom = null;
            }
        });
        clickTimer.setRepeats(false);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Box box = boxAt(e.getX(), e.getY());
                if (box == null) {
                    return;
                }
                if (e.getClickCount() == 2) {
                    clickTimer.stop();
                    pendingZoom = null;
                    if (box.node.getFrame() != null && onActivate != null) {
                        onActivate.accept(box.node.getFrame());
                    }
                } else {
                    pendingZoom = box;
                    clickTimer.restart();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                Box box = boxAt(e.getX(), e.getY());
                CallTreeNode node = box != null ? box.node : null;
                if (node != hovered) {
                    hovered = node;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hovered != null) {
                    hovered = null;
                    repaint();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updatePreferredHeight();
                revalidate();
            }
        });
    }

    /** Notified whenever the zoom (breadcrumb path) changes. */
    public void setOnZoomChanged(Runnable listener) {
        this.onZoomChanged = listener;
    }

    public boolean isZoomed() {
        return path.size() > 1;
    }

    /** Breadcrumb of the current zoom path, e.g. {@code All > Bar.run > Baz.work}. */
    public String pathLabel() {
        StringBuilder sb = new StringBuilder("All");
        for (int i = 1; i < path.size(); i++) {
            sb.append("  >  ").append(path.get(i).getFrame().displayLabel());
        }
        return sb.toString();
    }

    public void reset() {
        if (path.size() > 1) {
            path.subList(1, path.size()).clear();
            afterZoom();
        }
    }

    private void zoom(CallTreeNode node) {
        if (node == currentRoot()) {
            if (path.size() > 1) {
                path.remove(path.size() - 1);
            }
        } else {
            path.add(node);
        }
        afterZoom();
    }

    private void afterZoom() {
        updatePreferredHeight();
        revalidate();
        repaint();
        if (onZoomChanged != null) {
            onZoomChanged.run();
        }
    }

    private CallTreeNode currentRoot() {
        return path.get(path.size() - 1);
    }

    private void updatePreferredHeight() {
        double width = getWidth() > 0 ? getWidth() : 800;
        int rows = visibleDepth(currentRoot(), width) + 1;
        setPreferredSize(new Dimension(600, rows * ROW_HEIGHT));
    }

    /** Max depth of frames wide enough to be drawn at {@code width} (matches the culling in {@link #layout}). */
    private static int visibleDepth(CallTreeNode node, double width) {
        long total = node.getTotalWeight();
        if (total <= 0) {
            return 0;
        }
        int max = 0;
        for (CallTreeNode child : node.sortedChildren()) {
            double cw = width * child.getTotalWeight() / total;
            if (cw >= MIN_WIDTH) {
                max = Math.max(max, 1 + visibleDepth(child, cw));
            }
        }
        return max;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(JStudioTheme.getBgTertiary());
        g2.fillRect(0, 0, getWidth(), getHeight());

        boxes.clear();
        CallTreeNode root = currentRoot();
        if (root.getTotalWeight() <= 0) {
            g2.setColor(JStudioTheme.getTextSecondary());
            g2.drawString("No data.", 10, 20);
            g2.dispose();
            return;
        }
        layout(g2, root, 0, getWidth(), 0);
        g2.dispose();
    }

    private void layout(Graphics2D g2, CallTreeNode node, double x, double width, int row) {
        int ix = (int) Math.round(x);
        int iw = Math.max(1, (int) Math.round(width));
        int y = row * ROW_HEIGHT;
        boxes.add(new Box(ix, y, iw, node));
        paintBar(g2, node, ix, y, iw);

        long nodeTotal = node.getTotalWeight();
        if (nodeTotal <= 0) {
            return;
        }
        double childX = x;
        for (CallTreeNode child : node.sortedChildren()) {
            double cw = width * child.getTotalWeight() / nodeTotal;
            if (cw >= MIN_WIDTH) {
                layout(g2, child, childX, cw, row + 1);
            }
            childX += cw;
        }
    }

    private void paintBar(Graphics2D g2, CallTreeNode node, int x, int y, int w) {
        int bw = Math.max(1, w - GAP);
        int bh = ROW_HEIGHT - GAP;
        Color color = barColor(node);
        if (node == hovered) {
            color = mix(color, Color.WHITE, 0.28);
        }
        g2.setColor(color);
        g2.fillRoundRect(x, y, bw, bh, ARC, ARC);

        if (bw > 30) {
            g2.setColor(new Color(28, 28, 30));
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            String label = node.getFrame() != null ? node.getFrame().displayLabel()
                    : "All (" + weightFormat.apply(node.getTotalWeight()) + ")";
            label = ellipsize(label, fm, bw - 10);
            int baseline = y + (bh - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(label, x + 5, baseline);
        }
    }

    private static String ellipsize(String text, FontMetrics fm, int available) {
        if (fm.stringWidth(text) <= available) {
            return text;
        }
        int end = text.length();
        while (end > 1 && fm.stringWidth(text.substring(0, end) + "…") > available) {
            end--;
        }
        return text.substring(0, end) + "…";
    }

    /** A stable color per frame: hue by package (so a package reads as one family), brightness varied by method. */
    private static Color barColor(CallTreeNode node) {
        if (node.getFrame() == null) {
            return new Color(150, 150, 155);
        }
        String cls = node.getFrame().getClassInternal();
        int slash = cls.lastIndexOf('/');
        String pkg = slash >= 0 ? cls.substring(0, slash) : cls;
        float hue = (Math.abs(pkg.hashCode()) % 360) / 360f;
        float brightness = 0.88f + (Math.abs(node.getFrame().getMethod().hashCode()) % 7) / 100f;
        return Color.getHSBColor(hue, 0.42f, brightness);
    }

    private static Color mix(Color a, Color b, double t) {
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        Box box = boxAt(e.getX(), e.getY());
        if (box == null) {
            return null;
        }
        long total = trueRoot.getTotalWeight();
        double pct = total > 0 ? 100.0 * box.node.getTotalWeight() / total : 0;
        String label = box.node.getFrame() != null ? box.node.getFrame().displayLabel() : "All";
        return String.format("%s  -  %s (%.1f%%)", label, weightFormat.apply(box.node.getTotalWeight()), pct);
    }

    private Box boxAt(int x, int y) {
        for (Box box : boxes) {
            if (x >= box.x && x < box.x + box.w && y >= box.y && y < box.y + ROW_HEIGHT) {
                return box;
            }
        }
        return null;
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(600, Math.min(getPreferredSize().height, 18 * ROW_HEIGHT));
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return ROW_HEIGHT;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(ROW_HEIGHT, visibleRect.height - ROW_HEIGHT);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        repaint();
    }

    @Override
    public void removeNotify() {
        clickTimer.stop();
        ThemeManager.getInstance().removeThemeChangeListener(this);
        super.removeNotify();
    }

    private static int doubleClickInterval() {
        Object value = Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
        return value instanceof Integer ? Math.max(200, (Integer) value) : 250;
    }

    /** A laid-out bar; kept per paint for hit-testing. */
    private static final class Box {
        private final int x;
        private final int y;
        private final int w;
        private final CallTreeNode node;

        Box(int x, int y, int w, CallTreeNode node) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.node = node;
        }
    }
}
