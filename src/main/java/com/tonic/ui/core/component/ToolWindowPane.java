package com.tonic.ui.core.component;

import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
    private final Map<String, JComponent> tools = new LinkedHashMap<>();
    private String selected;
    @Getter
    private boolean collapsed = true;
    private Consumer<Boolean> collapseListener;
    private BiConsumer<String, MoveTarget> moveListener;
    private boolean dragSuppressAction;
    private static final int DRAG_THRESHOLD = 5;

    /** Targets a stripe tool can be relocated to via its right-click menu. */
    public enum MoveTarget { TAB, WINDOW }

    public ToolWindowPane() {
        super(new BorderLayout());
        stripe.setLayout(new BoxLayout(stripe, BoxLayout.Y_AXIS));
        stripeWrapper.add(stripe, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        add(stripeWrapper, BorderLayout.EAST);
        content.setVisible(false);
        applyThemeColors();
        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    /** Notified (with the new collapsed state) whenever the content area collapses or expands. */
    public void setCollapseListener(Consumer<Boolean> listener) {
        this.collapseListener = listener;
    }

    /** Notified when the user right-click-requests relocating a tool out of the dock (to a center tab or a window). */
    public void setMoveListener(BiConsumer<String, MoveTarget> listener) {
        this.moveListener = listener;
    }

    private boolean maybeShowMoveMenu(MouseEvent e, String name) {
        if (!e.isPopupTrigger() || moveListener == null) {
            return false;
        }
        JPopupMenu menu = new JPopupMenu();
        JMenuItem toTab = new JMenuItem("Move to Tab");
        toTab.addActionListener(a -> moveListener.accept(name, MoveTarget.TAB));
        JMenuItem toWindow = new JMenuItem("Move to Window");
        toWindow.addActionListener(a -> moveListener.accept(name, MoveTarget.WINDOW));
        menu.add(toTab);
        menu.add(toWindow);
        menu.show(e.getComponent(), e.getX(), e.getY());
        return true;
    }

    /** The stripe slot a cursor at {@code y} (in stripe coords) falls into: # of buttons whose midpoint is above it. */
    private int slotForY(int y) {
        int slot = 0;
        for (StripeButton b : buttons) {
            if (y >= b.getY() + b.getHeight() / 2) {
                slot++;
            }
        }
        return Math.max(0, Math.min(slot, buttons.size() - 1));
    }

    /** Reorders the stripe button (and the tools map, to keep reselect/default-first consistent) from {@code from} to {@code to}. */
    private void moveButton(int from, int to) {
        int size = buttons.size();
        to = Math.max(0, Math.min(to, size - 1));
        if (from < 0 || from >= size || from == to) {
            return;
        }
        StripeButton moved = buttons.remove(from);
        buttons.add(to, moved);

        stripe.removeAll();
        Map<String, JComponent> reordered = new LinkedHashMap<>();
        for (StripeButton b : buttons) {
            stripe.add(b);
            stripe.add(Box.createVerticalStrut(3));
            reordered.put(b.toolName(), tools.get(b.toolName()));
        }
        tools.clear();
        tools.putAll(reordered);
        stripe.revalidate();
        stripe.repaint();
    }

    /** Width of the always-visible stripe column (so the container can leave room for it when collapsed). */
    public int getStripeWidth() {
        return stripeWrapper.getPreferredSize().width;
    }

    /** Collapses (hides) or expands the content area; the stripe stays visible either way. */
    public void setCollapsed(boolean value) {
        if (collapsed == value) {
            updateButtonStates();
            return;
        }
        collapsed = value;
        content.setVisible(!value);
        updateButtonStates();
        if (collapseListener != null) {
            collapseListener.accept(value);
        }
        revalidate();
        repaint();
    }

    private void onStripeClick(String name) {
        if (!collapsed && name.equals(selected)) {
            setCollapsed(true);
        } else {
            selected = name;
            cards.show(content, name);
            setCollapsed(false);
            updateButtonStates();
        }
    }

    private void updateButtonStates() {
        for (StripeButton button : buttons) {
            button.setSelected(!collapsed && button.toolName().equals(selected));
        }
    }

    /** Registers a tool under a stripe button; the first registered tool becomes the active one. No-op if the name already exists. */
    public void addTool(String name, JComponent component) {
        if (tools.containsKey(name)) {
            return;
        }
        tools.put(name, component);
        content.add(component, name);
        StripeButton button = new StripeButton(name);
        button.addActionListener(e -> {
            if (dragSuppressAction) {
                dragSuppressAction = false;   // this click ended a drag - don't toggle selection
                return;
            }
            onStripeClick(name);
        });
        MouseAdapter handler = new MouseAdapter() {
            private Point pressPoint;
            private boolean dragging;

            @Override
            public void mousePressed(MouseEvent e) {
                if (maybeShowMoveMenu(e, name) || !SwingUtilities.isLeftMouseButton(e)) {
                    pressPoint = null;
                    return;
                }
                pressPoint = e.getPoint();
                dragging = false;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (pressPoint == null) {
                    return;
                }
                if (!dragging) {
                    if (Math.abs(e.getX() - pressPoint.x) < DRAG_THRESHOLD
                            && Math.abs(e.getY() - pressPoint.y) < DRAG_THRESHOLD) {
                        return;
                    }
                    dragging = true;
                    dragSuppressAction = true;
                    button.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
                int from = buttons.indexOf(button);
                Point inStripe = SwingUtilities.convertPoint(button, e.getPoint(), stripe);
                int to = slotForY(inStripe.y);
                if (from >= 0 && to != from) {
                    moveButton(from, to);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowMoveMenu(e, name);
                pressPoint = null;
                if (dragging) {
                    dragging = false;
                    button.setCursor(Cursor.getDefaultCursor());
                }
            }
        };
        button.addMouseListener(handler);
        button.addMouseMotionListener(handler);
        buttons.add(button);
        stripe.add(button);
        stripe.add(Box.createVerticalStrut(3));
        if (selected == null) {
            selected = name;
            cards.show(content, name);
            updateButtonStates();
        }
    }

    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    /** Removes a registered tool (and its stripe button). If it was active, activates the first remaining tool. */
    public void removeTool(String name) {
        removeToolInternal(name);
    }

    /**
     * Removes a tool and returns its component so it can be re-hosted elsewhere (a center tab or a window) and later
     * restored via {@link #addTool}. Collapses the dock once it has no tools left. Returns null if no such tool.
     */
    public JComponent detachTool(String name) {
        JComponent component = removeToolInternal(name);
        if (component != null) {
            // CardLayout leaves non-active cards visible=false; ensure it shows once re-hosted outside the dock.
            component.setVisible(true);
            if (tools.isEmpty()) {
                setCollapsed(true);
            }
        }
        return component;
    }

    /** Shared removal: drops the tool's card + stripe button, reselects the first remaining tool, returns the card. */
    private JComponent removeToolInternal(String name) {
        JComponent component = tools.remove(name);
        if (component == null) {
            return null;
        }
        content.remove(component);
        for (int i = 0; i < buttons.size(); i++) {
            if (buttons.get(i).toolName().equals(name)) {
                int stripeIndex = stripe.getComponentZOrder(buttons.get(i));
                stripe.remove(buttons.get(i));
                if (stripeIndex >= 0 && stripeIndex < stripe.getComponentCount()) {
                    stripe.remove(stripeIndex); // the trailing strut
                }
                buttons.remove(i);
                break;
            }
        }
        if (name.equals(selected)) {
            selected = null;
            if (!tools.isEmpty()) {
                selected = tools.keySet().iterator().next();
                cards.show(content, selected);
            }
            updateButtonStates();
        }
        revalidate();
        repaint();
        return component;
    }

    /** Activates the named tool (expanding the content area if collapsed) and shows its card. */
    public void select(String name) {
        selected = name;
        cards.show(content, name);
        setCollapsed(false);
        updateButtonStates();
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
