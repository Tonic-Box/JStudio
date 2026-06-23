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

/**
 * Stateless static styling facade over {@link JStudioTheme} tokens: one-shot appliers for the recurring
 * button / text-field / table / combo / border / hover shapes that were copy-pasted across the UI. Each method
 * reproduces an existing inline block verbatim, so it changes only HOW a color is applied, never WHEN - live
 * re-theming stays owned by the {@code Themed*} base classes (their {@code applyChildThemes()} can call these too).
 */
public final class ThemeStyles {

    private ThemeStyles() {
    }

    /**
     * Styles a button. {@code primary} gives the accent background with white text; otherwise the secondary surface.
     * Mirrors {@code Rename*Dialog.styleButton} (including the {@link Color#WHITE} literal on primary).
     */
    public static void styleButton(JButton button, boolean primary) {
        if (primary) {
            button.setBackground(JStudioTheme.getAccent());
            button.setForeground(Color.WHITE);
        } else {
            button.setBackground(JStudioTheme.getBgSecondary());
            button.setForeground(JStudioTheme.getTextPrimary());
        }
        button.setFocusPainted(false);
        button.setBorder(themedButtonBorder());
    }

    /** Styles a single-line input field (secondary surface, code font, themed input border). */
    public static void styleTextField(JTextField field) {
        field.setBackground(JStudioTheme.getBgSecondary());
        field.setForeground(JStudioTheme.getTextPrimary());
        field.setCaretColor(JStudioTheme.getTextPrimary());
        field.setBorder(themedFieldBorder());
        field.setFont(JStudioTheme.getCodeFont(12));
    }

    /** Body styling matching {@link com.tonic.ui.core.component.ThemedJTable}; pair with {@link #styleTableHeader}. */
    public static void styleTable(JTable table) {
        table.setBackground(JStudioTheme.getBgSecondary());
        table.setForeground(JStudioTheme.getTextPrimary());
        table.setSelectionBackground(JStudioTheme.getSelection());
        table.setSelectionForeground(JStudioTheme.getTextPrimary());
        table.setGridColor(JStudioTheme.getBorder());
        table.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE));
    }

    /** Header styling matching {@link com.tonic.ui.core.component.ThemedJTable}; no-op when the table has no header. */
    public static void styleTableHeader(JTable table) {
        JTableHeader header = table.getTableHeader();
        if (header != null) {
            header.setBackground(JStudioTheme.getBgTertiary());
            header.setForeground(JStudioTheme.getTextSecondary());
            header.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
        }
    }

    /** Styles a combo box (tertiary surface), matching {@code ToolbarBuilder.themeViewModeCombo}. */
    public static void styleComboBox(JComboBox<?> combo) {
        combo.setBackground(JStudioTheme.getBgTertiary());
        combo.setForeground(JStudioTheme.getTextPrimary());
    }

    /** The compound (line + 5,8,5,8 padding) border used by input fields. */
    public static Border themedFieldBorder() {
        return themedInputBorder(5, 8, 5, 8);
    }

    /** The compound (line + 6,16,6,16 padding) border used by buttons. */
    public static Border themedButtonBorder() {
        return themedInputBorder(6, 16, 6, 16);
    }

    /** A compound border: a 1px theme line plus the given empty padding. */
    public static Border themedInputBorder(int top, int left, int bottom, int right) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                BorderFactory.createEmptyBorder(top, left, bottom, right));
    }

    /**
     * Adds a background hover effect: paints {@code hover} on enter, restores {@code restoreBg} on exit. Returns the
     * installed adapter so a caller that needs symmetric teardown can detach it.
     */
    public static MouseAdapter addHoverEffect(AbstractButton button, Color restoreBg) {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(JStudioTheme.getHover());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(restoreBg);
            }
        };
        button.addMouseListener(adapter);
        return adapter;
    }

    /**
     * Adds an icon-button hover effect: fills the content area with the hover color on enter and clears it on exit
     * (the borderless-button idiom from {@code ToolbarBuilder.createButton}). Returns the installed adapter.
     */
    public static MouseAdapter addFillHoverEffect(AbstractButton button) {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setContentAreaFilled(true);
                button.setBackground(JStudioTheme.getHover());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setContentAreaFilled(false);
            }
        };
        button.addMouseListener(adapter);
        return adapter;
    }
}
