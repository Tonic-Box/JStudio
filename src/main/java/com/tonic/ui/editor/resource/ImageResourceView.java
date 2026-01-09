package com.tonic.ui.editor.resource;

import com.tonic.ui.model.ResourceEntryModel;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

public class ImageResourceView extends JPanel implements ThemeChangeListener {

    private final ResourceEntryModel resource;
    private final JLabel imageLabel;
    private final JScrollPane scrollPane;
    private final JToolBar toolbar;
    private final JLabel infoLabel;
    private BufferedImage originalImage;
    private double zoomFactor = 1.0;

    private static final double ZOOM_INCREMENT = 0.25;
    private static final double MIN_ZOOM = 0.1;
    private static final double MAX_ZOOM = 10.0;

    public ImageResourceView(ResourceEntryModel resource) {
        this.resource = resource;
        setLayout(new BorderLayout());

        toolbar = createToolbar();
        add(toolbar, BorderLayout.NORTH);

        imageLabel = new JLabel();
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setVerticalAlignment(SwingConstants.CENTER);

        JPanel imagePanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintCheckerboard(g);
            }
        };
        imagePanel.add(imageLabel, BorderLayout.CENTER);

        scrollPane = new JScrollPane(imagePanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());
        add(scrollPane, BorderLayout.CENTER);

        infoLabel = new JLabel();
        infoLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(infoLabel, BorderLayout.SOUTH);

        loadImage();
        applyTheme();

        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    private JToolBar createToolbar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);

        JButton zoomInBtn = new JButton(Icons.getIcon("zoom_in", 16));
        zoomInBtn.setToolTipText("Zoom In");
        zoomInBtn.setFocusable(false);
        zoomInBtn.setBorderPainted(false);
        zoomInBtn.setContentAreaFilled(false);
        zoomInBtn.addActionListener(e -> zoomIn());
        tb.add(zoomInBtn);

        JButton zoomOutBtn = new JButton(Icons.getIcon("zoom_out", 16));
        zoomOutBtn.setToolTipText("Zoom Out");
        zoomOutBtn.setFocusable(false);
        zoomOutBtn.setBorderPainted(false);
        zoomOutBtn.setContentAreaFilled(false);
        zoomOutBtn.addActionListener(e -> zoomOut());
        tb.add(zoomOutBtn);

        JButton fitBtn = new JButton(Icons.getIcon("fit", 16));
        fitBtn.setToolTipText("Fit to Window");
        fitBtn.setFocusable(false);
        fitBtn.setBorderPainted(false);
        fitBtn.setContentAreaFilled(false);
        fitBtn.addActionListener(e -> fitToWindow());
        tb.add(fitBtn);

        JButton actualSizeBtn = new JButton(Icons.getIcon("actual_size", 16));
        actualSizeBtn.setToolTipText("Actual Size (100%)");
        actualSizeBtn.setFocusable(false);
        actualSizeBtn.setBorderPainted(false);
        actualSizeBtn.setContentAreaFilled(false);
        actualSizeBtn.addActionListener(e -> actualSize());
        tb.add(actualSizeBtn);

        return tb;
    }

    private void loadImage() {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(resource.getData());
            originalImage = ImageIO.read(bais);
            if (originalImage != null) {
                updateImageDisplay();
                updateInfoLabel();
            } else {
                imageLabel.setText("Unable to load image");
            }
        } catch (Exception e) {
            imageLabel.setText("Error loading image: " + e.getMessage());
        }
    }

    private void updateImageDisplay() {
        if (originalImage == null) return;

        int newWidth = (int) (originalImage.getWidth() * zoomFactor);
        int newHeight = (int) (originalImage.getHeight() * zoomFactor);

        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                zoomFactor < 1 ? RenderingHints.VALUE_INTERPOLATION_BILINEAR
                        : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        imageLabel.setIcon(new ImageIcon(scaled));
        imageLabel.setPreferredSize(new Dimension(newWidth, newHeight));
        scrollPane.revalidate();
    }

    private void updateInfoLabel() {
        if (originalImage != null) {
            String info = String.format("%s | %dx%d | %s | Zoom: %.0f%%",
                    resource.getName(),
                    originalImage.getWidth(),
                    originalImage.getHeight(),
                    resource.getFormattedSize(),
                    zoomFactor * 100);
            infoLabel.setText(info);
        }
    }

    private void paintCheckerboard(Graphics g) {
        int tileSize = 10;
        Color light = new Color(200, 200, 200);
        Color dark = new Color(150, 150, 150);

        for (int y = 0; y < getHeight(); y += tileSize) {
            for (int x = 0; x < getWidth(); x += tileSize) {
                g.setColor(((x + y) / tileSize) % 2 == 0 ? light : dark);
                g.fillRect(x, y, tileSize, tileSize);
            }
        }
    }

    public void zoomIn() {
        if (zoomFactor < MAX_ZOOM) {
            zoomFactor = Math.min(MAX_ZOOM, zoomFactor + ZOOM_INCREMENT);
            updateImageDisplay();
            updateInfoLabel();
        }
    }

    public void zoomOut() {
        if (zoomFactor > MIN_ZOOM) {
            zoomFactor = Math.max(MIN_ZOOM, zoomFactor - ZOOM_INCREMENT);
            updateImageDisplay();
            updateInfoLabel();
        }
    }

    public void fitToWindow() {
        if (originalImage == null) return;

        Dimension viewSize = scrollPane.getViewport().getSize();
        double widthRatio = (double) viewSize.width / originalImage.getWidth();
        double heightRatio = (double) viewSize.height / originalImage.getHeight();
        zoomFactor = Math.min(widthRatio, heightRatio) * 0.95;
        zoomFactor = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoomFactor));
        updateImageDisplay();
        updateInfoLabel();
    }

    public void actualSize() {
        zoomFactor = 1.0;
        updateImageDisplay();
        updateInfoLabel();
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        SwingUtilities.invokeLater(this::applyTheme);
    }

    private void applyTheme() {
        setBackground(JStudioTheme.getBgTertiary());
        toolbar.setBackground(JStudioTheme.getBgSecondary());
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));
        scrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());
        infoLabel.setBackground(JStudioTheme.getBgSecondary());
        infoLabel.setForeground(JStudioTheme.getTextSecondary());
    }
}
