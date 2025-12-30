package com.tonic.ui.theme;

import java.awt.Color;
import java.awt.Font;

/**
 * JStudio theme facade.
 * Delegates to ThemeManager for current theme colors.
 * Provides static accessor methods for backward compatibility.
 */
public class JStudioTheme {

    private JStudioTheme() {
    }

    public static Color getBgPrimary() {
        return ThemeManager.getInstance().getCurrentTheme().getBgPrimary();
    }

    public static Color getBgSecondary() {
        return ThemeManager.getInstance().getCurrentTheme().getBgSecondary();
    }

    public static Color getBgTertiary() {
        return ThemeManager.getInstance().getCurrentTheme().getBgTertiary();
    }

    public static Color getBgSurface() {
        return ThemeManager.getInstance().getCurrentTheme().getBgSurface();
    }

    public static Color getTextPrimary() {
        return ThemeManager.getInstance().getCurrentTheme().getTextPrimary();
    }

    public static Color getTextSecondary() {
        return ThemeManager.getInstance().getCurrentTheme().getTextSecondary();
    }

    public static Color getTextDisabled() {
        return ThemeManager.getInstance().getCurrentTheme().getTextDisabled();
    }

    public static Color getAccent() {
        return ThemeManager.getInstance().getCurrentTheme().getAccent();
    }

    public static Color getAccentSecondary() {
        return ThemeManager.getInstance().getCurrentTheme().getAccentSecondary();
    }

    public static Color getSuccess() {
        return ThemeManager.getInstance().getCurrentTheme().getSuccess();
    }

    public static Color getWarning() {
        return ThemeManager.getInstance().getCurrentTheme().getWarning();
    }

    public static Color getError() {
        return ThemeManager.getInstance().getCurrentTheme().getError();
    }

    public static Color getInfo() {
        return ThemeManager.getInstance().getCurrentTheme().getInfo();
    }

    public static Color getSelection() {
        return ThemeManager.getInstance().getCurrentTheme().getSelection();
    }

    public static Color getHover() {
        return ThemeManager.getInstance().getCurrentTheme().getHover();
    }

    public static Color getLineHighlight() {
        return ThemeManager.getInstance().getCurrentTheme().getLineHighlight();
    }

    public static Color getBorder() {
        return ThemeManager.getInstance().getCurrentTheme().getBorder();
    }

    public static Color getBorderFocus() {
        return ThemeManager.getInstance().getCurrentTheme().getBorderFocus();
    }

    public static void apply() {
        ThemeManager.getInstance().applyTheme();
    }

    public static Color getGraphNodeFill() {
        return ThemeManager.getInstance().getCurrentTheme().getGraphNodeFill();
    }

    public static Color getGraphNodeStroke() {
        return ThemeManager.getInstance().getCurrentTheme().getGraphNodeStroke();
    }

    public static Color getGraphFocusFill() {
        return ThemeManager.getInstance().getCurrentTheme().getGraphFocusFill();
    }

    public static Color getGraphFocusStroke() {
        return ThemeManager.getInstance().getCurrentTheme().getGraphFocusStroke();
    }

    public static Color getGraphConstructorFill() {
        return ThemeManager.getInstance().getCurrentTheme().getGraphConstructorFill();
    }

    public static Color getGraphConstructorStroke() {
        return ThemeManager.getInstance().getCurrentTheme().getGraphConstructorStroke();
    }

    public static Color getGraphStaticFill() {
        return ThemeManager.getInstance().getCurrentTheme().getGraphStaticFill();
    }

    public static Color getGraphStaticStroke() {
        return ThemeManager.getInstance().getCurrentTheme().getGraphStaticStroke();
    }

    public static Color getGraphExternalFill() {
        return ThemeManager.getInstance().getCurrentTheme().getGraphExternalFill();
    }

    public static Color getGraphExternalStroke() {
        return ThemeManager.getInstance().getCurrentTheme().getGraphExternalStroke();
    }

    public static Color getBcLoad() {
        return ThemeManager.getInstance().getCurrentTheme().getBcLoad();
    }

    public static Color getBcStore() {
        return ThemeManager.getInstance().getCurrentTheme().getBcStore();
    }

    public static Color getBcInvoke() {
        return ThemeManager.getInstance().getCurrentTheme().getBcInvoke();
    }

    public static Color getBcField() {
        return ThemeManager.getInstance().getCurrentTheme().getBcField();
    }

    public static Color getBcBranch() {
        return ThemeManager.getInstance().getCurrentTheme().getBcBranch();
    }

    public static Color getBcStack() {
        return ThemeManager.getInstance().getCurrentTheme().getBcStack();
    }

    public static Color getBcConst() {
        return ThemeManager.getInstance().getCurrentTheme().getBcConst();
    }

    public static Color getBcReturn() {
        return ThemeManager.getInstance().getCurrentTheme().getBcReturn();
    }

    public static Color getBcNew() {
        return ThemeManager.getInstance().getCurrentTheme().getBcNew();
    }

    public static Color getBcArithmetic() {
        return ThemeManager.getInstance().getCurrentTheme().getBcArithmetic();
    }

    public static Color getBcType() {
        return ThemeManager.getInstance().getCurrentTheme().getBcType();
    }

    public static Color getBcOffset() {
        return ThemeManager.getInstance().getCurrentTheme().getBcOffset();
    }

    public static Font getCodeFont(int size) {
        return ThemeManager.getInstance().getCurrentTheme().getCodeFont(size);
    }

    public static Font getUIFont(int size) {
        return ThemeManager.getInstance().getCurrentTheme().getUIFont(size);
    }
}
