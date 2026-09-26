package com.tonic.ui.theme;

import com.tonic.ui.core.constants.UIConstants;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.table.JTableHeader;
import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Static one-shot styling helpers for the recurring button, text field, table, combo, border and hover shapes; they set colors once and do not follow theme switches. */
public final class ThemeStyles
{

    private ThemeStyles()
    {
    }

    /**
     * Styles a button with the themed button border and no focus paint.
     *
     * @param button the button
     * @param primary true for the accent background with white text, false for the secondary background
     */
    public static void styleButton(JButton button, boolean primary)
    {
        if (primary)
        {
            button.setBackground(JStudioTheme.getAccent());
            button.setForeground(Color.WHITE);
        }
        else
        {
            button.setBackground(JStudioTheme.getBgSecondary());
            button.setForeground(JStudioTheme.getTextPrimary());
        }
        button.setFocusPainted(false);
        button.setBorder(themedButtonBorder());
    }

    /**
     * Styles a single-line input field with the secondary background, code font and themed field border.
     *
     * @param field the field
     */
    public static void styleTextField(JTextField field)
    {
        field.setBackground(JStudioTheme.getBgSecondary());
        field.setForeground(JStudioTheme.getTextPrimary());
        field.setCaretColor(JStudioTheme.getTextPrimary());
        field.setBorder(themedFieldBorder());
        field.setFont(JStudioTheme.getCodeFont(12));
    }

    /**
     * Styles a table body to match ThemedJTable; pair with styleTableHeader.
     *
     * @param table the table
     */
    public static void styleTable(JTable table)
    {
        table.setBackground(JStudioTheme.getBgSecondary());
        table.setForeground(JStudioTheme.getTextPrimary());
        table.setSelectionBackground(JStudioTheme.getSelection());
        table.setSelectionForeground(JStudioTheme.getTextPrimary());
        table.setGridColor(JStudioTheme.getBorder());
        table.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE));
    }

    /**
     * Styles a table's header to match ThemedJTable; does nothing if the table has no header.
     *
     * @param table the table
     */
    public static void styleTableHeader(JTable table)
    {
        JTableHeader header = table.getTableHeader();
        if (header != null)
        {
            header.setBackground(JStudioTheme.getBgTertiary());
            header.setForeground(JStudioTheme.getTextSecondary());
            header.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
        }
    }

    /**
     * Styles a combo box with the tertiary background.
     *
     * @param combo the combo box
     */
    public static void styleComboBox(JComboBox<?> combo)
    {
        combo.setBackground(JStudioTheme.getBgTertiary());
        combo.setForeground(JStudioTheme.getTextPrimary());
    }

    /**
     * Creates the border used by input fields: a theme line plus 5, 8, 5, 8 padding.
     *
     * @return a new border
     */
    public static Border themedFieldBorder()
    {
        return themedInputBorder(5, 8, 5, 8);
    }

    /**
     * Creates the border used by buttons: a theme line plus 6, 16, 6, 16 padding.
     *
     * @return a new border
     */
    public static Border themedButtonBorder()
    {
        return themedInputBorder(6, 16, 6, 16);
    }

    /**
     * Creates a 1 pixel theme-colored line border with empty padding inside it.
     *
     * @param top the top padding
     * @param left the left padding
     * @param bottom the bottom padding
     * @param right the right padding
     * @return a new border
     */
    public static Border themedInputBorder(int top, int left, int bottom, int right)
    {
        return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()), BorderFactory.createEmptyBorder(top, left, bottom, right));
    }

    /**
     * Adds a listener that paints the hover color on mouse enter and restores a background on exit.
     *
     * @param button the button
     * @param restoreBg the background to restore on exit
     * @return the installed listener, for callers that need to remove it
     */
    public static MouseAdapter addHoverEffect(AbstractButton button, Color restoreBg)
    {
        MouseAdapter adapter = new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                button.setBackground(JStudioTheme.getHover());
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                button.setBackground(restoreBg);
            }
        };
        button.addMouseListener(adapter);
        return adapter;
    }

    /**
     * Adds a listener for borderless icon buttons that fills the content area with the hover color on mouse enter and unfills it on exit.
     *
     * @param button the button
     * @return the installed listener, for callers that need to remove it
     */
    public static MouseAdapter addFillHoverEffect(AbstractButton button)
    {
        MouseAdapter adapter = new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                button.setContentAreaFilled(true);
                button.setBackground(JStudioTheme.getHover());
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                button.setContentAreaFilled(false);
            }
        };
        button.addMouseListener(adapter);
        return adapter;
    }
}
