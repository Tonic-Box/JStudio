package com.tonic.ui.theme;

import com.tonic.util.Settings;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractTheme implements Theme {

    private static final String[] DEFAULT_CODE_FONTS = {
        "JetBrains Mono", "Cascadia Code", "Fira Code", "Source Code Pro",
        "Consolas", "Menlo", "Monaco", "DejaVu Sans Mono", "Courier New"
    };

    @Override
    public Font getCodeFont(int size) {
        String configuredFont = Settings.getInstance().getFontFamily();
        if (configuredFont != null && !configuredFont.isEmpty()) {
            Font font = new Font(configuredFont, Font.PLAIN, size);
            if (!font.getFamily().equals("Dialog")) {
                return font;
            }
        }

        for (String fontName : DEFAULT_CODE_FONTS) {
            Font font = new Font(fontName, Font.PLAIN, size);
            if (!font.getFamily().equals("Dialog")) {
                return font;
            }
        }
        return new Font(Font.MONOSPACED, Font.PLAIN, size);
    }

    @Override
    public Font getUIFont(int size) {
        String[] fontNames = {"Inter", "Segoe UI", "SF Pro Display", "Helvetica Neue"};
        for (String fontName : fontNames) {
            Font font = new Font(fontName, Font.PLAIN, size);
            if (!font.getFamily().equals("Dialog")) {
                return font;
            }
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, size);
    }

    public static List<String> getAvailableMonospaceFonts() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] allFonts = ge.getAvailableFontFamilyNames();

        return Arrays.stream(allFonts)
            .filter(AbstractTheme::isMonospaceFont)
            .sorted()
            .collect(Collectors.toList());
    }

    private static boolean isMonospaceFont(String fontName) {
        Font font = new Font(fontName, Font.PLAIN, 12);
        int iWidth = font.canDisplay('i') ? font.createGlyphVector(
            new java.awt.font.FontRenderContext(null, true, true), "i")
            .getPixelBounds(null, 0, 0).width : 0;
        int mWidth = font.canDisplay('m') ? font.createGlyphVector(
            new java.awt.font.FontRenderContext(null, true, true), "m")
            .getPixelBounds(null, 0, 0).width : 0;

        return iWidth > 0 && iWidth == mWidth;
    }
}
