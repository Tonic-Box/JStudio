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

/** Factories for themed toolbars, buttons, labels, scroll panes, borders and box spacers. */
public final class LayoutHelper
{

    private LayoutHelper()
    {
    }

    /**
     * Creates a left-aligned flow toolbar on the secondary background.
     *
     * @return a new toolbar
     */
    public static JPanel createToolbar()
    {
        ThemedJPanel toolbar = new ThemedJPanel(ThemedJPanel.BackgroundStyle.SECONDARY);
        toolbar.setLayout(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL));
        return toolbar;
    }

    /**
     * Creates a toolbar with a line along its bottom edge.
     *
     * @return a new toolbar
     */
    public static JPanel createToolbarWithBorder()
    {
        JPanel toolbar = createToolbar();
        toolbar.setBorder(createBottomBorder());
        return toolbar;
    }

    /**
     * Creates a button with the current theme's secondary background and no focus paint.
     *
     * @param text the label
     * @return a new button
     */
    public static JButton createButton(String text)
    {
        JButton button = new JButton(text);
        button.setBackground(JStudioTheme.getBgSecondary());
        button.setForeground(JStudioTheme.getTextPrimary());
        button.setFocusPainted(false);
        return button;
    }

    /**
     * Creates a themed button with an action.
     *
     * @param text the label
     * @param action run when the button is pressed
     * @return a new button
     */
    public static JButton createButton(String text, ActionListener action)
    {
        JButton button = createButton(text);
        button.addActionListener(action);
        return button;
    }

    /**
     * Creates a themed scroll pane.
     *
     * @param view the component to scroll
     * @return a new ThemedJScrollPane
     */
    public static JScrollPane createScrollPane(Component view)
    {
        return new ThemedJScrollPane(view);
    }

    /**
     * Creates a read-only, word-wrapping text area for status messages.
     *
     * @param rows the visible row count
     * @return a new text area
     */
    public static ThemedJTextArea createStatusArea(int rows)
    {
        ThemedJTextArea area = new ThemedJTextArea(rows, UIConstants.TEXT_FIELD_COLUMNS_LARGE);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    /**
     * Creates a label in the primary text color and UI font.
     *
     * @param text the text
     * @return a new label
     */
    public static JLabel createLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(JStudioTheme.getTextPrimary());
        label.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_NORMAL));
        return label;
    }

    /**
     * Creates a 1 pixel theme-colored line along the bottom edge.
     *
     * @return a new border
     */
    public static Border createBottomBorder()
    {
        return BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder());
    }

    /**
     * Creates a 1 pixel theme-colored line along the top edge.
     *
     * @return a new border
     */
    public static Border createTopBorder()
    {
        return BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder());
    }

    /**
     * Creates the standard small padding on all four sides.
     *
     * @return a new border
     */
    public static Border createEmptyBorder()
    {
        return BorderFactory.createEmptyBorder(UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL);
    }

    /**
     * Creates equal padding on all four sides.
     *
     * @param size the padding in pixels
     * @return a new border
     */
    public static Border createEmptyBorder(int size)
    {
        return BorderFactory.createEmptyBorder(size, size, size, size);
    }

    /**
     * Creates padding.
     *
     * @param top the top padding
     * @param left the left padding
     * @param bottom the bottom padding
     * @param right the right padding
     * @return a new border
     */
    public static Border createEmptyBorder(int top, int left, int bottom, int right)
    {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    /**
     * Creates horizontal glue for a box layout.
     *
     * @return a new glue component
     */
    public static Component createHorizontalGlue()
    {
        return Box.createHorizontalGlue();
    }

    /**
     * Creates vertical glue for a box layout.
     *
     * @return a new glue component
     */
    public static Component createVerticalGlue()
    {
        return Box.createVerticalGlue();
    }

    /**
     * Creates a fixed horizontal gap.
     *
     * @param width the gap in pixels
     * @return a new rigid area
     */
    public static Component createHorizontalStrut(int width)
    {
        return Box.createRigidArea(new Dimension(width, 0));
    }

    /**
     * Creates a fixed vertical gap.
     *
     * @param height the gap in pixels
     * @return a new rigid area
     */
    public static Component createVerticalStrut(int height)
    {
        return Box.createRigidArea(new Dimension(0, height));
    }

    /**
     * Creates a transparent panel laying out components left to right.
     *
     * @param components the components, in order
     * @return a new panel
     */
    public static JPanel createHorizontalBox(Component... components)
    {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        for (Component c : components)
        {
            panel.add(c);
        }
        return panel;
    }

    /**
     * Creates a transparent panel laying out components top to bottom.
     *
     * @param components the components, in order
     * @return a new panel
     */
    public static JPanel createVerticalBox(Component... components)
    {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (Component c : components)
        {
            panel.add(c);
        }
        return panel;
    }
}
