package com.tonic.ui.theme;

import java.awt.Color;
import java.awt.Font;

/** Static facade over the current theme's UI and graph colors and fonts, read from ThemeManager on every call. */
public class JStudioTheme
{

    private JStudioTheme()
    {
    }

    /**
     * Background of the main content and editor areas.
     *
     * @return the current theme's color
     */
    public static Color getBgPrimary()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBgPrimary();
    }

    /**
     * Background of side panels, trees, lists, tables and menus.
     *
     * @return the current theme's color
     */
    public static Color getBgSecondary()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBgSecondary();
    }

    /**
     * Background of input fields, combo boxes and table headers.
     *
     * @return the current theme's color
     */
    public static Color getBgTertiary()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBgTertiary();
    }

    /**
     * Background of raised surfaces such as tooltips; also the base of scrollbar thumbs.
     *
     * @return the current theme's color
     */
    public static Color getBgSurface()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBgSurface();
    }

    /**
     * Foreground of ordinary text.
     *
     * @return the current theme's color
     */
    public static Color getTextPrimary()
    {
        return ThemeManager.getInstance().getCurrentTheme().getTextPrimary();
    }

    /**
     * Foreground of muted text such as labels, headers and titles.
     *
     * @return the current theme's color
     */
    public static Color getTextSecondary()
    {
        return ThemeManager.getInstance().getCurrentTheme().getTextSecondary();
    }

    /**
     * Foreground of disabled text.
     *
     * @return the current theme's color
     */
    public static Color getTextDisabled()
    {
        return ThemeManager.getInstance().getCurrentTheme().getTextDisabled();
    }

    /**
     * Primary accent color for highlights, focus underlines and primary buttons.
     *
     * @return the current theme's color
     */
    public static Color getAccent()
    {
        return ThemeManager.getInstance().getCurrentTheme().getAccent();
    }

    /**
     * Secondary accent color.
     *
     * @return the current theme's color
     */
    public static Color getAccentSecondary()
    {
        return ThemeManager.getInstance().getCurrentTheme().getAccentSecondary();
    }

    /**
     * Color for success states.
     *
     * @return the current theme's color
     */
    public static Color getSuccess()
    {
        return ThemeManager.getInstance().getCurrentTheme().getSuccess();
    }

    /**
     * Color for warnings.
     *
     * @return the current theme's color
     */
    public static Color getWarning()
    {
        return ThemeManager.getInstance().getCurrentTheme().getWarning();
    }

    /**
     * Color for errors.
     *
     * @return the current theme's color
     */
    public static Color getError()
    {
        return ThemeManager.getInstance().getCurrentTheme().getError();
    }

    /**
     * Color for informational messages.
     *
     * @return the current theme's color
     */
    public static Color getInfo()
    {
        return ThemeManager.getInstance().getCurrentTheme().getInfo();
    }

    /**
     * Background of selected items and text.
     *
     * @return the current theme's color
     */
    public static Color getSelection()
    {
        return ThemeManager.getInstance().getCurrentTheme().getSelection();
    }

    /**
     * Background of hovered items.
     *
     * @return the current theme's color
     */
    public static Color getHover()
    {
        return ThemeManager.getInstance().getCurrentTheme().getHover();
    }

    /**
     * Background of the current-line highlight in code views.
     *
     * @return the current theme's color
     */
    public static Color getLineHighlight()
    {
        return ThemeManager.getInstance().getCurrentTheme().getLineHighlight();
    }

    /**
     * Highlight for a line linked across the dual view's panes, stronger than the current-line highlight.
     *
     * @return the accent color at alpha 130
     */
    public static Color getLinkHighlight()
    {
        Color c = getAccent();
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), 130);
    }

    /**
     * Color of ordinary borders, separators and grid lines.
     *
     * @return the current theme's color
     */
    public static Color getBorder()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBorder();
    }

    /**
     * Color of the border of a focused component.
     *
     * @return the current theme's color
     */
    public static Color getBorderFocus()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBorderFocus();
    }

    /** Applies the current theme to the look and feel and repaints every window. */
    public static void apply()
    {
        ThemeManager.getInstance().applyTheme();
    }

    /**
     * Fill of an ordinary graph node.
     *
     * @return the current theme's color
     */
    public static Color getGraphNodeFill()
    {
        return ThemeManager.getInstance().getCurrentTheme().getGraphNodeFill();
    }

    /**
     * Outline of an ordinary graph node.
     *
     * @return the current theme's color
     */
    public static Color getGraphNodeStroke()
    {
        return ThemeManager.getInstance().getCurrentTheme().getGraphNodeStroke();
    }

    /**
     * Fill of the focused graph node.
     *
     * @return the current theme's color
     */
    public static Color getGraphFocusFill()
    {
        return ThemeManager.getInstance().getCurrentTheme().getGraphFocusFill();
    }

    /**
     * Outline of the focused graph node.
     *
     * @return the current theme's color
     */
    public static Color getGraphFocusStroke()
    {
        return ThemeManager.getInstance().getCurrentTheme().getGraphFocusStroke();
    }

    /**
     * Fill of a constructor graph node.
     *
     * @return the current theme's color
     */
    public static Color getGraphConstructorFill()
    {
        return ThemeManager.getInstance().getCurrentTheme().getGraphConstructorFill();
    }

    /**
     * Outline of a constructor graph node.
     *
     * @return the current theme's color
     */
    public static Color getGraphConstructorStroke()
    {
        return ThemeManager.getInstance().getCurrentTheme().getGraphConstructorStroke();
    }

    /**
     * Fill of a static method graph node.
     *
     * @return the current theme's color
     */
    public static Color getGraphStaticFill()
    {
        return ThemeManager.getInstance().getCurrentTheme().getGraphStaticFill();
    }

    /**
     * Outline of a static method graph node.
     *
     * @return the current theme's color
     */
    public static Color getGraphStaticStroke()
    {
        return ThemeManager.getInstance().getCurrentTheme().getGraphStaticStroke();
    }

    /**
     * Fill of an external (not loaded) graph node.
     *
     * @return the current theme's color
     */
    public static Color getGraphExternalFill()
    {
        return ThemeManager.getInstance().getCurrentTheme().getGraphExternalFill();
    }

    /**
     * Outline of an external (not loaded) graph node.
     *
     * @return the current theme's color
     */
    public static Color getGraphExternalStroke()
    {
        return ThemeManager.getInstance().getCurrentTheme().getGraphExternalStroke();
    }

    /**
     * Bytecode color for load instructions.
     *
     * @return the current theme's color
     */
    public static Color getBcLoad()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcLoad();
    }

    /**
     * Bytecode color for store instructions.
     *
     * @return the current theme's color
     */
    public static Color getBcStore()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcStore();
    }

    /**
     * Bytecode color for invoke instructions.
     *
     * @return the current theme's color
     */
    public static Color getBcInvoke()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcInvoke();
    }

    /**
     * Bytecode color for field access instructions.
     *
     * @return the current theme's color
     */
    public static Color getBcField()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcField();
    }

    /**
     * Bytecode color for branch instructions.
     *
     * @return the current theme's color
     */
    public static Color getBcBranch()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcBranch();
    }

    /**
     * Bytecode color for stack manipulation instructions.
     *
     * @return the current theme's color
     */
    public static Color getBcStack()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcStack();
    }

    /**
     * Bytecode color for constant-pushing instructions.
     *
     * @return the current theme's color
     */
    public static Color getBcConst()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcConst();
    }

    /**
     * Bytecode color for return instructions.
     *
     * @return the current theme's color
     */
    public static Color getBcReturn()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcReturn();
    }

    /**
     * Bytecode color for object and array creation instructions.
     *
     * @return the current theme's color
     */
    public static Color getBcNew()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcNew();
    }

    /**
     * Bytecode color for arithmetic instructions.
     *
     * @return the current theme's color
     */
    public static Color getBcArithmetic()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcArithmetic();
    }

    /**
     * Bytecode color for type check and cast instructions.
     *
     * @return the current theme's color
     */
    public static Color getBcType()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcType();
    }

    /**
     * Bytecode color for instruction offsets.
     *
     * @return the current theme's color
     */
    public static Color getBcOffset()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcOffset();
    }

    /**
     * The current theme's monospace font for code views.
     *
     * @param size the point size
     * @return the font
     */
    public static Font getCodeFont(int size)
    {
        return ThemeManager.getInstance().getCurrentTheme().getCodeFont(size);
    }

    /**
     * The current theme's font for ordinary UI text.
     *
     * @param size the point size
     * @return the font
     */
    public static Font getUIFont(int size)
    {
        return ThemeManager.getInstance().getCurrentTheme().getUIFont(size);
    }
}
