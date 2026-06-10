package com.tonic.ui.core.component;

import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * IntelliJ-style tool-window container: a vertical stripe of rotated toggle buttons along the right
 * edge selects which registered tool fills the content area (a {@link CardLayout}). The first tool
 * added is selected by default. Reusable and theme-aware.
 */
public class ToolWindowPane extends JPanel implements ThemeChangeListener {

    private final CardLayout cards = new CardLayout();
    /**
     * Sizes to the currently visible card rather than the {@link CardLayout} default (the max over
     * all cards), so a wide hidden tool never inflates the column or blocks the split from shrinking.
     */
    private final JPanel content = new JPanel(cards) {
        private Component visibleCard() {
            for (Component c : getComponents()) {
                if (c.isVisible()) {
                    return c;
                }
            }
            return null;
        }

        @Override
        public Dimension getPreferredSize() {
            Component card = visibleCard();
            return card != null ? card.getPreferredSize() : super.getPreferredSize();
        }

        @Override
        public Dimension getMinimumSize() {
            Component card = visibleCard();
            return card != null ? card.getMinimumSize() : super.getMinimumSize();
        }
    };
    private final JPanel stripe = new JPanel();
    private final JPanel stripeWrapper = new JPanel(new BorderLayout());
    private final List<StripeButton> buttons = new ArrayList<>();
    private String selected;

    public ToolWindowPane() {
        super(new BorderLayout());
        stripe.setLayout(new BoxLayout(stripe, BoxLayout.Y_AXIS));
        stripeWrapper.add(stripe, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        add(stripeWrapper, BorderLayout.EAST);
        applyThemeColors();
        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    /** Registers a tool under a stripe button; the first registered tool becomes the active one. */
    public void addTool(String name, JComponent component) {
        content.add(component, name);
        StripeButton button = new StripeButton(name);
        button.addActionListener(e -> select(name));
        buttons.add(button);
        stripe.add(button);
        stripe.add(Box.createVerticalStrut(3));
        if (selected == null) {
            select(name);
        }
    }

    /** Activates the named tool, updating the card view and the stripe selection state. */
    public void select(String name) {
        selected = name;
        cards.show(content, name);
        for (StripeButton button : buttons) {
            button.setSelected(button.toolName().equals(name));
        }
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        SwingUtilities.invokeLater(this::applyThemeColors);
    }

    private void applyThemeColors() {
        setBackground(JStudioTheme.getBgPrimary());
        content.setBackground(JStudioTheme.getBgSurface());
        stripe.setBackground(JStudioTheme.getBgSecondary());
        stripeWrapper.setBackground(JStudioTheme.getBgSecondary());
        repaint();
    }

    /** A vertically-rendered, themed toggle button for the stripe. */
    private static class StripeButton extends JToggleButton {
        private final String toolName;

        StripeButton(String toolName) {
            this.toolName = toolName;
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setRolloverEnabled(true);
            setAlignmentX(Component.CENTER_ALIGNMENT);
            setFont(JStudioTheme.getUIFont(12));
            setToolTipText(toolName);
        }

        String toolName() {
            return toolName;
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.getHeight() + 12, fm.stringWidth(toolName) + 20);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            if (isSelected()) {
                g2.setColor(JStudioTheme.getSelection());
                g2.fillRect(0, 0, w, h);
                g2.setColor(JStudioTheme.getAccent());
                g2.fillRect(w - 2, 0, 2, h);
            } else if (getModel().isRollover()) {
                g2.setColor(JStudioTheme.getHover());
                g2.fillRect(0, 0, w, h);
            }

            g2.setFont(getFont());
            g2.setColor(isSelected() ? JStudioTheme.getTextPrimary() : JStudioTheme.getTextSecondary());
            g2.translate(0, h);
            g2.rotate(-Math.PI / 2);
            FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth(toolName);
            int x = (h - textWidth) / 2;
            int y = (w - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(toolName, x, y);
            g2.dispose();
        }
    }
}
