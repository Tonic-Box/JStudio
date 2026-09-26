package com.tonic.ui.theme;

import java.awt.Color;

/**
 * Syntax highlighting color facade.
 * Delegates to ThemeManager for current theme colors.
 */
public class SyntaxColors
{

    private SyntaxColors()
    {
    }

    public static Color getJavaKeyword()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaKeyword();
    }

    public static Color getJavaType()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaType();
    }

    public static Color getJavaString()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaString();
    }

    public static Color getJavaNumber()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaNumber();
    }

    public static Color getJavaComment()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaComment();
    }

    public static Color getJavaMethod()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaMethod();
    }

    public static Color getJavaField()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaField();
    }

    public static Color getJavaAnnotation()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaAnnotation();
    }

    public static Color getJavaOperator()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaOperator();
    }

    public static Color getJavaConstant()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaConstant();
    }

    public static Color getJavaClassName()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaClassName();
    }

    public static Color getJavaLocalVar()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaLocalVar();
    }

    public static Color getJavaParameter()
    {
        return ThemeManager.getInstance().getCurrentTheme().getJavaParameter();
    }

    public static Color getBcLoad()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcLoad();
    }

    public static Color getBcStore()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcStore();
    }

    public static Color getBcInvoke()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcInvoke();
    }

    public static Color getBcField()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcField();
    }

    public static Color getBcBranch()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcBranch();
    }

    public static Color getBcStack()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcStack();
    }

    public static Color getBcConst()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcConst();
    }

    public static Color getBcReturn()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcReturn();
    }

    public static Color getBcNew()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcNew();
    }

    public static Color getBcArithmetic()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcArithmetic();
    }

    public static Color getBcType()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcType();
    }

    public static Color getBcOffset()
    {
        return ThemeManager.getInstance().getCurrentTheme().getBcOffset();
    }

    public static Color getIrPhi()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrPhi();
    }

    public static Color getIrBinaryOp()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrBinaryOp();
    }

    public static Color getIrUnaryOp()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrUnaryOp();
    }

    public static Color getIrConstant()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrConstant();
    }

    public static Color getIrLoadLocal()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrLoadLocal();
    }

    public static Color getIrStoreLocal()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrStoreLocal();
    }

    public static Color getIrInvoke()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrInvoke();
    }

    public static Color getIrGetField()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrGetField();
    }

    public static Color getIrPutField()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrPutField();
    }

    public static Color getIrBranch()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrBranch();
    }

    public static Color getIrGoto()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrGoto();
    }

    public static Color getIrReturn()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrReturn();
    }

    public static Color getIrNew()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrNew();
    }

    public static Color getIrArrayLoad()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrArrayLoad();
    }

    public static Color getIrArrayStore()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrArrayStore();
    }

    public static Color getIrCast()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrCast();
    }

    public static Color getIrThrow()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrThrow();
    }

    public static Color getIrBlockName()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrBlockName();
    }

    public static Color getIrSsaValue()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrSsaValue();
    }

    public static Color getIrType()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrType();
    }

    public static Color getIrBlock()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrBlock();
    }

    public static Color getIrValue()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrValue();
    }

    public static Color getIrOperator()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrOperator();
    }

    public static Color getIrControl()
    {
        return ThemeManager.getInstance().getCurrentTheme().getIrControl();
    }
}
