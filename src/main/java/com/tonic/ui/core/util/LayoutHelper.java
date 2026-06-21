package com.tonic.ui.core.util;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.component.ThemedJScrollPane;
import com.tonic.ui.core.component.ThemedJTextArea;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.Border;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;

public final class LayoutHelper {

    private LayoutHelper() {
    }

    public static JPanel createToolbar() {
        ThemedJPanel toolbar = new ThemedJPanel(ThemedJPanel.BackgroundStyle.SECONDARY);
        toolbar.setLayout(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL));
        return toolbar;
    }

    public static JPanel createToolbarWithBorder() {
        JPanel toolbar = createToolbar();
        toolbar.setBorder(createBottomBorder());
        return toolbar;
    }

    public static JButton createButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(JStudioTheme.getBgSecondary());
        button.setForeground(JStudioTheme.getTextPrimary());
        button.setFocusPainted(false);
        return button;
    }

    public static JButton createButton(String text, ActionListener action) {
        JButton button = createButton(text);
        button.addActionListener(action);
        return button;
    }

    public static JScrollPane createScrollPane(Component view) {
        return new ThemedJScrollPane(view);
    }

    public static ThemedJTextArea createStatusArea(int rows) {
        ThemedJTextArea area = new ThemedJTextArea(rows, UIConstants.TEXT_FIELD_COLUMNS_LARGE);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    public static JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(JStudioTheme.getTextPrimary());
        label.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_NORMAL));
        return label;
    }

    public static Border createBottomBorder() {
        return BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder());
    }

    public static Border createTopBorder() {
        return BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder());
    }

    public static Border createEmptyBorder() {
        return BorderFactory.createEmptyBorder(
            UIConstants.SPACING_SMALL,
            UIConstants.SPACING_SMALL,
            UIConstants.SPACING_SMALL,
            UIConstants.SPACING_SMALL
        );
    }

    public static Border createEmptyBorder(int size) {
        return BorderFactory.createEmptyBorder(size, size, size, size);
    }

    public static Border createEmptyBorder(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    public static Component createHorizontalGlue() {
        return Box.createHorizontalGlue();
    }

    public static Component createVerticalGlue() {
        return Box.createVerticalGlue();
    }

    public static Component createHorizontalStrut(int width) {
        return Box.createRigidArea(new Dimension(width, 0));
    }

    public static Component createVerticalStrut(int height) {
        return Box.createRigidArea(new Dimension(0, height));
    }

    public static JPanel createHorizontalBox(Component... components) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        for (Component c : components) {
            panel.add(c);
        }
        return panel;
    }

    public static JPanel createVerticalBox(Component... components) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (Component c : components) {
            panel.add(c);
        }
        return panel;
    }
}
