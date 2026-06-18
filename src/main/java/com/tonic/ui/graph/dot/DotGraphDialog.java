package com.tonic.ui.graph.dot;

import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.util.mxRectangle;
import com.mxgraph.view.mxGraph;
import com.tonic.graph.dot.DotParser;
import com.tonic.ui.theme.JStudioTheme;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * The expanded, interactive view of a DOT diagram: a pan/zoom {@link mxGraphComponent} with a toolbar (zoom, fit,
 * view-DOT toggle, copy DOT, save as PNG). Opened from {@link DotGraphView}. Read-only; rebuilds its own graph from
 * the DOT source so it is independent of the inline thumbnail.
 */
public final class DotGraphDialog extends JDialog {

    private final String dotSource;
    private final mxGraph graph;
    private final mxGraphComponent graphComponent;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel();

    private Point panStartScreen;
    private Point panStartViewport;

    public DotGraphDialog(Window owner, String dotSource) {
        super(owner, "Diagram", ModalityType.MODELESS);
        this.dotSource = dotSource;
        this.graph = buildGraph(dotSource);
        this.graphComponent = createGraphComponent();

        content.setLayout(cards);
        content.add(wrap(graphComponent), "graph");
        content.add(dotPanel(dotSource), "dot");

        setLayout(new BorderLayout());
        add(buildToolbar(), BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        getContentPane().setBackground(JStudioTheme.getBgPrimary());

        setSize(sizeFor(owner));
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(this::fit);
    }

    private static mxGraph buildGraph(String dot) {
        try {
            return DotGraphBuilder.build(DotParser.parse(dot));
        } catch (RuntimeException e) {
            return new mxGraph();
        }
    }

    private mxGraphComponent createGraphComponent() {
        mxGraphComponent component = new mxGraphComponent(graph);
        component.setConnectable(false);
        component.setDragEnabled(false);
        component.setBorder(null);
        component.getViewport().setBackground(JStudioTheme.getBgPrimary());
        component.setBackground(JStudioTheme.getBgPrimary());
        component.getGraphControl().addMouseWheelListener(e -> {
            if (e.getWheelRotation() < 0) {
                component.zoomIn();
            } else {
                component.zoomOut();
            }
        });
        component.getGraphControl().addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                panStartScreen = e.getLocationOnScreen();
                panStartViewport = component.getViewport().getViewPosition();
                component.getGraphControl().setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                panStartScreen = null;
                panStartViewport = null;
                component.getGraphControl().setCursor(Cursor.getDefaultCursor());
            }
        });
        component.getGraphControl().addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (panStartScreen == null || panStartViewport == null) {
                    return;
                }
                Point now = e.getLocationOnScreen();
                int x = panStartViewport.x + (panStartScreen.x - now.x);
                int y = panStartViewport.y + (panStartScreen.y - now.y);
                Dimension view = component.getViewport().getViewSize();
                Dimension port = component.getViewport().getExtentSize();
                x = Math.max(0, Math.min(x, Math.max(0, view.width - port.width)));
                y = Math.max(0, Math.min(y, Math.max(0, view.height - port.height)));
                component.getViewport().setViewPosition(new Point(x, y));
            }
        });
        return component;
    }

    private JComponent wrap(JComponent c) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(JStudioTheme.getBgPrimary());
        panel.add(c, BorderLayout.CENTER);
        return panel;
    }

    private JComponent dotPanel(String dot) {
        JTextArea area = new JTextArea(dot == null ? "" : dot.trim());
        area.setEditable(false);
        area.setBackground(JStudioTheme.getBgPrimary());
        area.setForeground(JStudioTheme.getTextPrimary());
        area.setFont(JStudioTheme.getCodeFont(13));
        area.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(null);
        return scroll;
    }

    private JToolBar buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBackground(JStudioTheme.getBgSecondary());
        bar.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        bar.add(button("Zoom +", graphComponent::zoomIn));
        bar.add(button("Zoom -", graphComponent::zoomOut));
        bar.add(button("Fit", this::fit));
        bar.addSeparator();

        JToggleButton viewDot = new JToggleButton("View DOT");
        style(viewDot);
        viewDot.addActionListener(e -> {
            cards.show(content, viewDot.isSelected() ? "dot" : "graph");
            viewDot.setText(viewDot.isSelected() ? "View graph" : "View DOT");
        });
        bar.add(viewDot);

        bar.add(button("Copy DOT", this::copyDot));
        bar.add(button("Save as PNG", this::savePng));
        return bar;
    }

    private JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        style(b);
        b.addActionListener(e -> action.run());
        return b;
    }

    private void style(javax.swing.AbstractButton b) {
        b.setFont(JStudioTheme.getUIFont(12));
        b.setBackground(JStudioTheme.getBgSurface());
        b.setForeground(JStudioTheme.getTextPrimary());
        b.setFocusPainted(false);
    }

    private void fit() {
        mxRectangle bounds = graph.getGraphBounds();
        if (bounds == null || bounds.getWidth() <= 0 || bounds.getHeight() <= 0) {
            return;
        }
        Dimension port = graphComponent.getViewport().getExtentSize();
        if (port.width <= 0 || port.height <= 0) {
            return;
        }
        double scale = Math.min(
                port.width / (bounds.getWidth() + 60),
                port.height / (bounds.getHeight() + 60));
        scale = Math.max(0.1, Math.min(scale, 2.0));
        graphComponent.zoomTo(scale, false);
    }

    private void copyDot() {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(dotSource == null ? "" : dotSource), null);
    }

    private void savePng() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save diagram as PNG");
        chooser.setFileFilter(new FileNameExtensionFilter("PNG Images", "png"));
        chooser.setSelectedFile(new File("diagram.png"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".png")) {
            file = new File(file.getAbsolutePath() + ".png");
        }
        try {
            BufferedImage image = mxCellRenderer.createBufferedImage(
                    graph, null, 2, JStudioTheme.getBgPrimary(), true, null);
            if (image == null) {
                JOptionPane.showMessageDialog(this, "Nothing to export (empty graph).");
                return;
            }
            ImageIO.write(image, "PNG", file);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save: " + ex.getMessage());
        }
    }

    private static Dimension sizeFor(Window owner) {
        if (owner != null) {
            return new Dimension(Math.max(640, owner.getWidth() * 3 / 4),
                    Math.max(480, owner.getHeight() * 3 / 4));
        }
        return new Dimension(820, 620);
    }
}
