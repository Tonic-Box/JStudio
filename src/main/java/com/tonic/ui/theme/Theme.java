package com.tonic.ui.theme;

import java.awt.Color;
import java.awt.Font;

/** A named palette of UI, syntax and graph colors plus the code and UI fonts. */
public interface Theme
{

    /**
     * The theme's key, as used in settings and theme file names.
     *
     * @return the key
     */
    String getName();

    /**
     * The theme's name as shown to the user.
     *
     * @return the display name
     */
    String getDisplayName();

    /**
     * Background of the main content and editor areas.
     *
     * @return the color
     */
    Color getBgPrimary();

    /**
     * Background of side panels, trees, lists, tables and menus.
     *
     * @return the color
     */
    Color getBgSecondary();

    /**
     * Background of input fields, combo boxes and table headers.
     *
     * @return the color
     */
    Color getBgTertiary();

    /**
     * Background of raised surfaces such as tooltips; also the base of scrollbar thumbs.
     *
     * @return the color
     */
    Color getBgSurface();

    /**
     * Foreground of ordinary text.
     *
     * @return the color
     */
    Color getTextPrimary();

    /**
     * Foreground of muted text such as labels, headers and titles.
     *
     * @return the color
     */
    Color getTextSecondary();

    /**
     * Foreground of disabled text.
     *
     * @return the color
     */
    Color getTextDisabled();

    /**
     * Primary accent color for highlights, focus underlines and primary buttons.
     *
     * @return the color
     */
    Color getAccent();

    /**
     * Secondary accent color.
     *
     * @return the color
     */
    Color getAccentSecondary();

    /**
     * Color for success states.
     *
     * @return the color
     */
    Color getSuccess();

    /**
     * Color for warnings.
     *
     * @return the color
     */
    Color getWarning();

    /**
     * Color for errors.
     *
     * @return the color
     */
    Color getError();

    /**
     * Color for informational messages.
     *
     * @return the color
     */
    Color getInfo();

    /**
     * Background of selected items and text.
     *
     * @return the color
     */
    Color getSelection();

    /**
     * Background of hovered items.
     *
     * @return the color
     */
    Color getHover();

    /**
     * Background of the current-line highlight in code views.
     *
     * @return the color
     */
    Color getLineHighlight();

    /**
     * Color of ordinary borders, separators and grid lines.
     *
     * @return the color
     */
    Color getBorder();

    /**
     * Color of the border of a focused component.
     *
     * @return the color
     */
    Color getBorderFocus();

    /**
     * Java source color for keywords.
     *
     * @return the color
     */
    Color getJavaKeyword();

    /**
     * Java source color for type names.
     *
     * @return the color
     */
    Color getJavaType();

    /**
     * Java source color for string literals.
     *
     * @return the color
     */
    Color getJavaString();

    /**
     * Java source color for numeric literals.
     *
     * @return the color
     */
    Color getJavaNumber();

    /**
     * Java source color for comments.
     *
     * @return the color
     */
    Color getJavaComment();

    /**
     * Java source color for method names.
     *
     * @return the color
     */
    Color getJavaMethod();

    /**
     * Java source color for field names.
     *
     * @return the color
     */
    Color getJavaField();

    /**
     * Java source color for annotations.
     *
     * @return the color
     */
    Color getJavaAnnotation();

    /**
     * Java source color for operators.
     *
     * @return the color
     */
    Color getJavaOperator();

    /**
     * Java source color for constants.
     *
     * @return the color
     */
    Color getJavaConstant();

    /**
     * Java source color for class names.
     *
     * @return the color
     */
    Color getJavaClassName();

    /**
     * Java source color for local variables.
     *
     * @return the color
     */
    Color getJavaLocalVar();

    /**
     * Java source color for parameters.
     *
     * @return the color
     */
    Color getJavaParameter();

    /**
     * Bytecode color for load instructions.
     *
     * @return the color
     */
    Color getBcLoad();

    /**
     * Bytecode color for store instructions.
     *
     * @return the color
     */
    Color getBcStore();

    /**
     * Bytecode color for invoke instructions.
     *
     * @return the color
     */
    Color getBcInvoke();

    /**
     * Bytecode color for field access instructions.
     *
     * @return the color
     */
    Color getBcField();

    /**
     * Bytecode color for branch instructions.
     *
     * @return the color
     */
    Color getBcBranch();

    /**
     * Bytecode color for stack manipulation instructions.
     *
     * @return the color
     */
    Color getBcStack();

    /**
     * Bytecode color for constant-pushing instructions.
     *
     * @return the color
     */
    Color getBcConst();

    /**
     * Bytecode color for return instructions.
     *
     * @return the color
     */
    Color getBcReturn();

    /**
     * Bytecode color for object and array creation instructions.
     *
     * @return the color
     */
    Color getBcNew();

    /**
     * Bytecode color for arithmetic instructions.
     *
     * @return the color
     */
    Color getBcArithmetic();

    /**
     * Bytecode color for type check and cast instructions.
     *
     * @return the color
     */
    Color getBcType();

    /**
     * Bytecode color for instruction offsets.
     *
     * @return the color
     */
    Color getBcOffset();

    /**
     * IR color for phi instructions.
     *
     * @return the color
     */
    Color getIrPhi();

    /**
     * IR color for binary operations.
     *
     * @return the color
     */
    Color getIrBinaryOp();

    /**
     * IR color for unary operations.
     *
     * @return the color
     */
    Color getIrUnaryOp();

    /**
     * IR color for constants.
     *
     * @return the color
     */
    Color getIrConstant();

    /**
     * IR color for local loads.
     *
     * @return the color
     */
    Color getIrLoadLocal();

    /**
     * IR color for local stores.
     *
     * @return the color
     */
    Color getIrStoreLocal();

    /**
     * IR color for invocations.
     *
     * @return the color
     */
    Color getIrInvoke();

    /**
     * IR color for field reads.
     *
     * @return the color
     */
    Color getIrGetField();

    /**
     * IR color for field writes.
     *
     * @return the color
     */
    Color getIrPutField();

    /**
     * IR color for conditional branches.
     *
     * @return the color
     */
    Color getIrBranch();

    /**
     * IR color for gotos.
     *
     * @return the color
     */
    Color getIrGoto();

    /**
     * IR color for returns.
     *
     * @return the color
     */
    Color getIrReturn();

    /**
     * IR color for allocations.
     *
     * @return the color
     */
    Color getIrNew();

    /**
     * IR color for array loads.
     *
     * @return the color
     */
    Color getIrArrayLoad();

    /**
     * IR color for array stores.
     *
     * @return the color
     */
    Color getIrArrayStore();

    /**
     * IR color for casts.
     *
     * @return the color
     */
    Color getIrCast();

    /**
     * IR color for throws.
     *
     * @return the color
     */
    Color getIrThrow();

    /**
     * IR color for block names.
     *
     * @return the color
     */
    Color getIrBlockName();

    /**
     * IR color for SSA values.
     *
     * @return the color
     */
    Color getIrSsaValue();

    /**
     * IR color for types.
     *
     * @return the color
     */
    Color getIrType();

    /**
     * IR color for block labels.
     *
     * @return the color
     */
    Color getIrBlock();

    /**
     * IR color for values.
     *
     * @return the color
     */
    Color getIrValue();

    /**
     * IR color for operators.
     *
     * @return the color
     */
    Color getIrOperator();

    /**
     * IR color for control-flow instructions.
     *
     * @return the color
     */
    Color getIrControl();

    /**
     * Fill of an ordinary graph node.
     *
     * @return the color
     */
    Color getGraphNodeFill();

    /**
     * Outline of an ordinary graph node.
     *
     * @return the color
     */
    Color getGraphNodeStroke();

    /**
     * Fill of the focused graph node.
     *
     * @return the color
     */
    Color getGraphFocusFill();

    /**
     * Outline of the focused graph node.
     *
     * @return the color
     */
    Color getGraphFocusStroke();

    /**
     * Fill of a constructor graph node.
     *
     * @return the color
     */
    Color getGraphConstructorFill();

    /**
     * Outline of a constructor graph node.
     *
     * @return the color
     */
    Color getGraphConstructorStroke();

    /**
     * Fill of a static method graph node.
     *
     * @return the color
     */
    Color getGraphStaticFill();

    /**
     * Outline of a static method graph node.
     *
     * @return the color
     */
    Color getGraphStaticStroke();

    /**
     * Fill of an external (not loaded) graph node.
     *
     * @return the color
     */
    Color getGraphExternalFill();

    /**
     * Outline of an external (not loaded) graph node.
     *
     * @return the color
     */
    Color getGraphExternalStroke();

    /**
     * The monospace font for code views.
     *
     * @param size the point size
     * @return the font
     */
    Font getCodeFont(int size);

    /**
     * The font for ordinary UI text.
     *
     * @param size the point size
     * @return the font
     */
    Font getUIFont(int size);
}
