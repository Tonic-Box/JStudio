package com.tonic.ui.theme;

import java.awt.Color;
import java.util.Map;

public class ConfigurableTheme extends AbstractTheme {

    private final String name;
    private final String displayName;
    private final Map<String, Color> colors;

    public ConfigurableTheme(String name, String displayName, Map<String, Color> colors) {
        this.name = name;
        this.displayName = displayName;
        this.colors = colors;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    private Color getColor(String key, int defaultRgb) {
        return colors.getOrDefault(key, new Color(defaultRgb));
    }

    @Override
    public Color getBgPrimary() {
        return getColor("bgPrimary", 0x2B2B2B);
    }

    @Override
    public Color getBgSecondary() {
        return getColor("bgSecondary", 0x3C3F41);
    }

    @Override
    public Color getBgTertiary() {
        return getColor("bgTertiary", 0x313335);
    }

    @Override
    public Color getBgSurface() {
        return getColor("bgSurface", 0x45494A);
    }

    @Override
    public Color getTextPrimary() {
        return getColor("textPrimary", 0xA9B7C6);
    }

    @Override
    public Color getTextSecondary() {
        return getColor("textSecondary", 0x808080);
    }

    @Override
    public Color getTextDisabled() {
        return getColor("textDisabled", 0x5C5C5C);
    }

    @Override
    public Color getAccent() {
        return getColor("accent", 0x6897BB);
    }

    @Override
    public Color getAccentSecondary() {
        return getColor("accentSecondary", 0xCC7832);
    }

    @Override
    public Color getSuccess() {
        return getColor("success", 0x6A8759);
    }

    @Override
    public Color getWarning() {
        return getColor("warning", 0xBBB529);
    }

    @Override
    public Color getError() {
        return getColor("error", 0xFF6B68);
    }

    @Override
    public Color getInfo() {
        return getColor("info", 0x6897BB);
    }

    @Override
    public Color getSelection() {
        return getColor("selection", 0x214283);
    }

    @Override
    public Color getHover() {
        return getColor("hover", 0x323232);
    }

    @Override
    public Color getLineHighlight() {
        return getColor("lineHighlight", 0x323232);
    }

    @Override
    public Color getBorder() {
        return getColor("border", 0x555555);
    }

    @Override
    public Color getBorderFocus() {
        return getColor("borderFocus", 0x6897BB);
    }

    @Override
    public Color getJavaKeyword() {
        return getColor("javaKeyword", 0xCC7832);
    }

    @Override
    public Color getJavaType() {
        return getColor("javaType", 0xA9B7C6);
    }

    @Override
    public Color getJavaString() {
        return getColor("javaString", 0x6A8759);
    }

    @Override
    public Color getJavaNumber() {
        return getColor("javaNumber", 0x6897BB);
    }

    @Override
    public Color getJavaComment() {
        return getColor("javaComment", 0x808080);
    }

    @Override
    public Color getJavaMethod() {
        return getColor("javaMethod", 0xFFC66D);
    }

    @Override
    public Color getJavaField() {
        return getColor("javaField", 0x9876AA);
    }

    @Override
    public Color getJavaAnnotation() {
        return getColor("javaAnnotation", 0xBBB529);
    }

    @Override
    public Color getJavaOperator() {
        return getColor("javaOperator", 0xA9B7C6);
    }

    @Override
    public Color getJavaConstant() {
        return getColor("javaConstant", 0x9876AA);
    }

    @Override
    public Color getJavaClassName() {
        return getColor("javaClassName", 0xA9B7C6);
    }

    @Override
    public Color getJavaLocalVar() {
        return getColor("javaLocalVar", 0xA9B7C6);
    }

    @Override
    public Color getJavaParameter() {
        return getColor("javaParameter", 0xA9B7C6);
    }

    @Override
    public Color getBcLoad() {
        return getColor("bcLoad", 0x6A8759);
    }

    @Override
    public Color getBcStore() {
        return getColor("bcStore", 0xFF6B68);
    }

    @Override
    public Color getBcInvoke() {
        return getColor("bcInvoke", 0xFFC66D);
    }

    @Override
    public Color getBcField() {
        return getColor("bcField", 0x9876AA);
    }

    @Override
    public Color getBcBranch() {
        return getColor("bcBranch", 0xCC7832);
    }

    @Override
    public Color getBcStack() {
        return getColor("bcStack", 0x6897BB);
    }

    @Override
    public Color getBcConst() {
        return getColor("bcConst", 0x6897BB);
    }

    @Override
    public Color getBcReturn() {
        return getColor("bcReturn", 0xCC7832);
    }

    @Override
    public Color getBcNew() {
        return getColor("bcNew", 0xCC7832);
    }

    @Override
    public Color getBcArithmetic() {
        return getColor("bcArithmetic", 0xA9B7C6);
    }

    @Override
    public Color getBcType() {
        return getColor("bcType", 0xA9B7C6);
    }

    @Override
    public Color getBcOffset() {
        return getColor("bcOffset", 0x808080);
    }

    @Override
    public Color getIrPhi() {
        return getColor("irPhi", 0x9876AA);
    }

    @Override
    public Color getIrBinaryOp() {
        return getColor("irBinaryOp", 0xA9B7C6);
    }

    @Override
    public Color getIrUnaryOp() {
        return getColor("irUnaryOp", 0xA9B7C6);
    }

    @Override
    public Color getIrConstant() {
        return getColor("irConstant", 0x6897BB);
    }

    @Override
    public Color getIrLoadLocal() {
        return getColor("irLoadLocal", 0x6A8759);
    }

    @Override
    public Color getIrStoreLocal() {
        return getColor("irStoreLocal", 0xFF6B68);
    }

    @Override
    public Color getIrInvoke() {
        return getColor("irInvoke", 0xFFC66D);
    }

    @Override
    public Color getIrGetField() {
        return getColor("irGetField", 0x9876AA);
    }

    @Override
    public Color getIrPutField() {
        return getColor("irPutField", 0xFF6B68);
    }

    @Override
    public Color getIrBranch() {
        return getColor("irBranch", 0xCC7832);
    }

    @Override
    public Color getIrGoto() {
        return getColor("irGoto", 0xCC7832);
    }

    @Override
    public Color getIrReturn() {
        return getColor("irReturn", 0xCC7832);
    }

    @Override
    public Color getIrNew() {
        return getColor("irNew", 0xCC7832);
    }

    @Override
    public Color getIrArrayLoad() {
        return getColor("irArrayLoad", 0x6A8759);
    }

    @Override
    public Color getIrArrayStore() {
        return getColor("irArrayStore", 0xFF6B68);
    }

    @Override
    public Color getIrCast() {
        return getColor("irCast", 0xCC7832);
    }

    @Override
    public Color getIrThrow() {
        return getColor("irThrow", 0xFF6B68);
    }

    @Override
    public Color getIrBlockName() {
        return getColor("irBlockName", 0x6897BB);
    }

    @Override
    public Color getIrSsaValue() {
        return getColor("irSsaValue", 0xA9B7C6);
    }

    @Override
    public Color getIrType() {
        return getColor("irType", 0x808080);
    }

    @Override
    public Color getIrBlock() {
        return getColor("irBlock", 0xCC7832);
    }

    @Override
    public Color getIrValue() {
        return getColor("irValue", 0xA9B7C6);
    }

    @Override
    public Color getIrOperator() {
        return getColor("irOperator", 0xA9B7C6);
    }

    @Override
    public Color getIrControl() {
        return getColor("irControl", 0xCC7832);
    }

    @Override
    public Color getGraphNodeFill() {
        return getColor("graphNodeFill", 0x252535);
    }

    @Override
    public Color getGraphNodeStroke() {
        return getColor("graphNodeStroke", 0x7AA2F7);
    }

    @Override
    public Color getGraphFocusFill() {
        return getColor("graphFocusFill", 0x3D4070);
    }

    @Override
    public Color getGraphFocusStroke() {
        return getColor("graphFocusStroke", 0xE0AF68);
    }

    @Override
    public Color getGraphConstructorFill() {
        return getColor("graphConstructorFill", 0x1E3A2F);
    }

    @Override
    public Color getGraphConstructorStroke() {
        return getColor("graphConstructorStroke", 0x9ECE6A);
    }

    @Override
    public Color getGraphStaticFill() {
        return getColor("graphStaticFill", 0x2D2640);
    }

    @Override
    public Color getGraphStaticStroke() {
        return getColor("graphStaticStroke", 0xBB9AF7);
    }

    @Override
    public Color getGraphExternalFill() {
        return getColor("graphExternalFill", 0x1E1E2E);
    }

    @Override
    public Color getGraphExternalStroke() {
        return getColor("graphExternalStroke", 0x565F89);
    }
}
