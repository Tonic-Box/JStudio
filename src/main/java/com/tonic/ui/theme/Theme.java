package com.tonic.ui.theme;

import java.awt.Color;
import java.awt.Font;

public interface Theme {

    String getName();

    String getDisplayName();

    Color getBgPrimary();
    Color getBgSecondary();
    Color getBgTertiary();
    Color getBgSurface();

    Color getTextPrimary();
    Color getTextSecondary();
    Color getTextDisabled();

    Color getAccent();
    Color getAccentSecondary();

    Color getSuccess();
    Color getWarning();
    Color getError();
    Color getInfo();

    Color getSelection();
    Color getHover();
    Color getLineHighlight();
    Color getBorder();
    Color getBorderFocus();

    Color getJavaKeyword();
    Color getJavaType();
    Color getJavaString();
    Color getJavaNumber();
    Color getJavaComment();
    Color getJavaMethod();
    Color getJavaField();
    Color getJavaAnnotation();
    Color getJavaOperator();
    Color getJavaConstant();
    Color getJavaClassName();
    Color getJavaLocalVar();
    Color getJavaParameter();

    Color getBcLoad();
    Color getBcStore();
    Color getBcInvoke();
    Color getBcField();
    Color getBcBranch();
    Color getBcStack();
    Color getBcConst();
    Color getBcReturn();
    Color getBcNew();
    Color getBcArithmetic();
    Color getBcType();
    Color getBcOffset();

    Color getIrPhi();
    Color getIrBinaryOp();
    Color getIrUnaryOp();
    Color getIrConstant();
    Color getIrLoadLocal();
    Color getIrStoreLocal();
    Color getIrInvoke();
    Color getIrGetField();
    Color getIrPutField();
    Color getIrBranch();
    Color getIrGoto();
    Color getIrReturn();
    Color getIrNew();
    Color getIrArrayLoad();
    Color getIrArrayStore();
    Color getIrCast();
    Color getIrThrow();
    Color getIrBlockName();
    Color getIrSsaValue();
    Color getIrType();
    Color getIrBlock();
    Color getIrValue();
    Color getIrOperator();
    Color getIrControl();

    Color getGraphNodeFill();
    Color getGraphNodeStroke();
    Color getGraphFocusFill();
    Color getGraphFocusStroke();
    Color getGraphConstructorFill();
    Color getGraphConstructorStroke();
    Color getGraphStaticFill();
    Color getGraphStaticStroke();
    Color getGraphExternalFill();
    Color getGraphExternalStroke();

    Font getCodeFont(int size);
    Font getUIFont(int size);
}
