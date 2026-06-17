package com.tonic.plugin.gui;

import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JRadioButton;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;

/**
 * Recursively applies JStudio theme colors to a plugin-contributed component tree and keeps them in sync when the
 * user switches themes, so plugin authors get native-looking panels without doing anything. Standard Swing
 * components are colored by type; buttons keep their look-and-feel styling (only foreground is set). Installed by
 * {@link UiApiImpl} for every contributed tool window / view / bottom tab and uninstalled when the contribution is
 * removed.
 */
final class PluginThemer implements ThemeChangeListener {

    private final Component root;

    private PluginThemer(Component root) {
        this.root = root;
    }

    /** Themes {@code root} now and re-themes it on every theme change until {@link #uninstall()}. */
    static PluginThemer install(Component root) {
        PluginThemer themer = new PluginThemer(root);
        themer.apply();
        ThemeManager.getInstance().addThemeChangeListener(themer);
        return themer;
    }

    void uninstall() {
        ThemeManager.getInstance().removeThemeChangeListener(this);
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        // A theme switch runs updateComponentTreeUI (resetting colors to L&F defaults) before listeners fire,
        // so re-apply our explicit colors afterwards.
        SwingUtilities.invokeLater(this::apply);
    }

    void apply() {
        themeTree(root);
        root.repaint();
    }

    private static void themeTree(Component component) {
        themeOne(component);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                themeTree(child);
            }
        }
    }

    private static void themeOne(Component component) {
        Color text = JStudioTheme.getTextPrimary();
        if (component instanceof JTextComponent) {
            component.setBackground(JStudioTheme.getBgTertiary());
            component.setForeground(text);
            ((JTextComponent) component).setCaretColor(text);
        } else if (component instanceof JLabel) {
            component.setForeground(text);
        } else if (component instanceof JCheckBox || component instanceof JRadioButton) {
            // Toggles read better with no filled box, matching the parent background.
            component.setForeground(text);
        } else if (component instanceof AbstractButton) {
            // Fill push/toggle buttons with the raised "surface" color so they match the theme and stand out
            // from the panel (the L&F still draws the rounded shape and hover).
            component.setBackground(JStudioTheme.getBgSurface());
            component.setForeground(text);
        } else if (component instanceof JList || component instanceof JTree || component instanceof JTable) {
            component.setBackground(JStudioTheme.getBgSecondary());
            component.setForeground(text);
        } else if (component instanceof JComboBox || component instanceof JSpinner) {
            component.setBackground(JStudioTheme.getBgTertiary());
            component.setForeground(text);
        } else if (component instanceof JTabbedPane) {
            component.setBackground(JStudioTheme.getBgSecondary());
            component.setForeground(text);
        } else if (component instanceof JScrollPane) {
            JScrollPane scrollPane = (JScrollPane) component;
            scrollPane.setBackground(JStudioTheme.getBgSecondary());
            scrollPane.getViewport().setBackground(JStudioTheme.getBgSecondary());
            scrollPane.setForeground(text);
        } else if (component instanceof JPanel || component instanceof JViewport
                || component instanceof Box || component instanceof JScrollBar) {
            component.setBackground(JStudioTheme.getBgSecondary());
            component.setForeground(text);
        } else if (component instanceof JComponent) {
            component.setForeground(text);
        }
    }
}
