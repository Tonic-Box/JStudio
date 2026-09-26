package com.tonic.ui.theme;

import java.awt.Color;

/** Static facade over the current theme's syntax highlighting colors, read from ThemeManager on every call. */
public class SyntaxColors
{

    private SyntaxColors()
    {
    }

    /**
     * Java source color for keywords.
     *
     * @return the current theme's color
     */
    public static Color getJavaKeyword()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaKeyword();
    }

    /**
     * Java source color for type names.
     *
     * @return the current theme's color
     */
    public static Color getJavaType()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaType();
    }

    /**
     * Java source color for string literals.
     *
     * @return the current theme's color
     */
    public static Color getJavaString()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaString();
    }

    /**
     * Java source color for numeric literals.
     *
     * @return the current theme's color
     */
    public static Color getJavaNumber()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaNumber();
    }

    /**
     * Java source color for comments.
     *
     * @return the current theme's color
     */
    public static Color getJavaComment()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaComment();
    }

    /**
     * Java source color for method names.
     *
     * @return the current theme's color
     */
    public static Color getJavaMethod()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaMethod();
    }

    /**
     * Java source color for field names.
     *
     * @return the current theme's color
     */
    public static Color getJavaField()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaField();
    }

    /**
     * Java source color for annotations.
     *
     * @return the current theme's color
     */
    public static Color getJavaAnnotation()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaAnnotation();
    }

    /**
     * Java source color for operators.
     *
     * @return the current theme's color
     */
    public static Color getJavaOperator()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaOperator();
    }

    /**
     * Java source color for constants.
     *
     * @return the current theme's color
     */
    public static Color getJavaConstant()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaConstant();
    }

    /**
     * Java source color for class names.
     *
     * @return the current theme's color
     */
    public static Color getJavaClassName()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaClassName();
    }

    /**
     * Java source color for local variables.
     *
     * @return the current theme's color
     */
    public static Color getJavaLocalVar()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaLocalVar();
    }

    /**
     * Java source color for parameters.
     *
     * @return the current theme's color
     */
    public static Color getJavaParameter()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaParameter();
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
     * IR color for phi instructions.
     *
     * @return the current theme's color
     */
    public static Color getIrPhi()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrPhi();
    }

    /**
     * IR color for binary operations.
     *
     * @return the current theme's color
     */
    public static Color getIrBinaryOp()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrBinaryOp();
    }

    /**
     * IR color for unary operations.
     *
     * @return the current theme's color
     */
    public static Color getIrUnaryOp()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrUnaryOp();
    }

    /**
     * IR color for constants.
     *
     * @return the current theme's color
     */
    public static Color getIrConstant()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrConstant();
    }

    /**
     * IR color for local loads.
     *
     * @return the current theme's color
     */
    public static Color getIrLoadLocal()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrLoadLocal();
    }

    /**
     * IR color for local stores.
     *
     * @return the current theme's color
     */
    public static Color getIrStoreLocal()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrStoreLocal();
    }

    /**
     * IR color for invocations.
     *
     * @return the current theme's color
     */
    public static Color getIrInvoke()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrInvoke();
    }

    /**
     * IR color for field reads.
     *
     * @return the current theme's color
     */
    public static Color getIrGetField()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrGetField();
    }

    /**
     * IR color for field writes.
     *
     * @return the current theme's color
     */
    public static Color getIrPutField()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrPutField();
    }

    /**
     * IR color for conditional branches.
     *
     * @return the current theme's color
     */
    public static Color getIrBranch()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrBranch();
    }

    /**
     * IR color for gotos.
     *
     * @return the current theme's color
     */
    public static Color getIrGoto()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrGoto();
    }

    /**
     * IR color for returns.
     *
     * @return the current theme's color
     */
    public static Color getIrReturn()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrReturn();
    }

    /**
     * IR color for allocations.
     *
     * @return the current theme's color
     */
    public static Color getIrNew()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrNew();
    }

    /**
     * IR color for array loads.
     *
     * @return the current theme's color
     */
    public static Color getIrArrayLoad()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrArrayLoad();
    }

    /**
     * IR color for array stores.
     *
     * @return the current theme's color
     */
    public static Color getIrArrayStore()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrArrayStore();
    }

    /**
     * IR color for casts.
     *
     * @return the current theme's color
     */
    public static Color getIrCast()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrCast();
    }

    /**
     * IR color for throws.
     *
     * @return the current theme's color
     */
    public static Color getIrThrow()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrThrow();
    }

    /**
     * IR color for block names.
     *
     * @return the current theme's color
     */
    public static Color getIrBlockName()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrBlockName();
    }

    /**
     * IR color for SSA values.
     *
     * @return the current theme's color
     */
    public static Color getIrSsaValue()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrSsaValue();
    }

    /**
     * IR color for types.
     *
     * @return the current theme's color
     */
    public static Color getIrType()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrType();
    }

    /**
     * IR color for block labels.
     *
     * @return the current theme's color
     */
    public static Color getIrBlock()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrBlock();
    }

    /**
     * IR color for values.
     *
     * @return the current theme's color
     */
    public static Color getIrValue()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrValue();
    }

    /**
     * IR color for operators.
     *
     * @return the current theme's color
     */
    public static Color getIrOperator()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrOperator();
    }

    /**
     * IR color for control-flow instructions.
     *
     * @return the current theme's color
     */
    public static Color getIrControl()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrControl();
    }
}
