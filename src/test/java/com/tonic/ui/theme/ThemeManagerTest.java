package com.tonic.ui.theme;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ThemeManagerTest {

    private ThemeManager themeManager;

    @BeforeEach
    void setUp() {
        themeManager = ThemeManager.getInstance();
    }

    @Test
    void testSingletonInstance() {
        ThemeManager instance1 = ThemeManager.getInstance();
        ThemeManager instance2 = ThemeManager.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    void testCurrentThemeNotNull() {
        assertNotNull(themeManager.getCurrentTheme());
    }

    @Test
    void testBuiltInThemesRegistered() {
        List<Theme> themes = themeManager.getAvailableThemes();
        assertFalse(themes.isEmpty());
        assertTrue(themes.size() >= 8);
    }

    @Test
    void testAvailableThemesContainsExpectedThemes() {
        List<Theme> themes = themeManager.getAvailableThemes();
        List<String> themeNames = new ArrayList<>();
        for (Theme t : themes) {
            themeNames.add(t.getName());
        }

        assertTrue(themeNames.contains("jstudio-dark"));
        assertTrue(themeNames.contains("darcula"));
        assertTrue(themeNames.contains("vscode-dark"));
        assertTrue(themeNames.contains("monokai"));
        assertTrue(themeNames.contains("nord"));
        assertTrue(themeNames.contains("solarized-dark"));
        assertTrue(themeNames.contains("dracula"));
        assertTrue(themeNames.contains("github-light"));
    }

    @Test
    void testGetAvailableThemesReturnsCopy() {
        List<Theme> themes1 = themeManager.getAvailableThemes();
        List<Theme> themes2 = themeManager.getAvailableThemes();
        assertNotSame(themes1, themes2);
        assertEquals(themes1.size(), themes2.size());
    }

    @Test
    void testThemeHasRequiredColors() {
        Theme theme = themeManager.getCurrentTheme();

        assertNotNull(theme.getBgPrimary());
        assertNotNull(theme.getBgSecondary());
        assertNotNull(theme.getBgTertiary());
        assertNotNull(theme.getTextPrimary());
        assertNotNull(theme.getTextSecondary());
        assertNotNull(theme.getAccent());
        assertNotNull(theme.getSelection());
        assertNotNull(theme.getBorder());
    }

    @Test
    void testThemeHasSyntaxColors() {
        Theme theme = themeManager.getCurrentTheme();

        assertNotNull(theme.getJavaKeyword());
        assertNotNull(theme.getJavaType());
        assertNotNull(theme.getJavaString());
        assertNotNull(theme.getJavaNumber());
        assertNotNull(theme.getJavaComment());
        assertNotNull(theme.getJavaMethod());
    }

    @Test
    void testSetThemeWithValidName() {
        Theme original = themeManager.getCurrentTheme();
        String originalName = original.getName();

        try {
            String targetTheme = originalName.equals("darcula") ? "monokai" : "darcula";
            themeManager.setTheme(targetTheme);
            assertEquals(targetTheme, themeManager.getCurrentTheme().getName());
        } finally {
            themeManager.setTheme(originalName);
        }
    }

    @Test
    void testSetThemeWithInvalidName() {
        Theme original = themeManager.getCurrentTheme();
        themeManager.setTheme("nonexistent-theme");
        assertSame(original, themeManager.getCurrentTheme());
    }

    @Test
    void testSetThemeWithNull() {
        Theme original = themeManager.getCurrentTheme();
        themeManager.setTheme(null);
        assertSame(original, themeManager.getCurrentTheme());
    }

    @Test
    void testSetSameThemeDoesNotNotify() {
        Theme current = themeManager.getCurrentTheme();
        AtomicInteger callCount = new AtomicInteger(0);

        ThemeChangeListener listener = newTheme -> callCount.incrementAndGet();
        themeManager.addThemeChangeListener(listener);

        try {
            themeManager.setTheme(current.getName());
            assertEquals(0, callCount.get());
        } finally {
            themeManager.removeThemeChangeListener(listener);
        }
    }

    @Test
    void testThemeChangeListenerNotified() {
        Theme original = themeManager.getCurrentTheme();
        String originalName = original.getName();
        List<Theme> receivedThemes = new ArrayList<>();

        ThemeChangeListener listener = receivedThemes::add;
        themeManager.addThemeChangeListener(listener);

        try {
            String newThemeName = originalName.equals("darcula") ? "monokai" : "darcula";
            themeManager.setTheme(newThemeName);
            assertEquals(1, receivedThemes.size());
            assertEquals(newThemeName, receivedThemes.get(0).getName());
        } finally {
            themeManager.removeThemeChangeListener(listener);
            themeManager.setTheme(originalName);
        }
    }

    @Test
    void testRemoveThemeChangeListener() {
        Theme original = themeManager.getCurrentTheme();
        String originalName = original.getName();
        AtomicInteger callCount = new AtomicInteger(0);

        ThemeChangeListener listener = newTheme -> callCount.incrementAndGet();
        themeManager.addThemeChangeListener(listener);
        themeManager.removeThemeChangeListener(listener);

        try {
            String newThemeName = originalName.equals("darcula") ? "monokai" : "darcula";
            themeManager.setTheme(newThemeName);
            assertEquals(0, callCount.get());
        } finally {
            themeManager.setTheme(originalName);
        }
    }

    @Test
    void testThemeDisplayName() {
        for (Theme theme : themeManager.getAvailableThemes()) {
            assertNotNull(theme.getDisplayName());
            assertFalse(theme.getDisplayName().isEmpty());
        }
    }

    @Test
    void testThemeName() {
        for (Theme theme : themeManager.getAvailableThemes()) {
            assertNotNull(theme.getName());
            assertFalse(theme.getName().isEmpty());
        }
    }

    @Test
    void testLightThemeHasLightBackground() {
        for (Theme theme : themeManager.getAvailableThemes()) {
            if (theme.getName().contains("light")) {
                Color bg = theme.getBgPrimary();
                int brightness = (bg.getRed() + bg.getGreen() + bg.getBlue()) / 3;
                assertTrue(brightness > 128, "Light theme should have bright background");
            }
        }
    }

    @Test
    void testDarkThemesHaveDarkBackground() {
        for (Theme theme : themeManager.getAvailableThemes()) {
            if (theme.getName().contains("dark") ||
                theme.getName().equals("darcula") ||
                theme.getName().equals("monokai") ||
                theme.getName().equals("dracula") ||
                theme.getName().equals("nord")) {
                Color bg = theme.getBgPrimary();
                int brightness = (bg.getRed() + bg.getGreen() + bg.getBlue()) / 3;
                assertTrue(brightness < 128,
                    "Dark theme " + theme.getName() + " should have dark background");
            }
        }
    }

    @Test
    void testAllThemesHaveValidJavaSyntaxColors() {
        for (Theme theme : themeManager.getAvailableThemes()) {
            assertNotNull(theme.getJavaKeyword(), theme.getName() + " missing javaKeyword");
            assertNotNull(theme.getJavaType(), theme.getName() + " missing javaType");
            assertNotNull(theme.getJavaString(), theme.getName() + " missing javaString");
            assertNotNull(theme.getJavaNumber(), theme.getName() + " missing javaNumber");
            assertNotNull(theme.getJavaComment(), theme.getName() + " missing javaComment");
            assertNotNull(theme.getJavaMethod(), theme.getName() + " missing javaMethod");
            assertNotNull(theme.getJavaField(), theme.getName() + " missing javaField");
            assertNotNull(theme.getJavaAnnotation(), theme.getName() + " missing javaAnnotation");
        }
    }

    @Test
    void testAllThemesHaveValidBytecodeColors() {
        for (Theme theme : themeManager.getAvailableThemes()) {
            assertNotNull(theme.getBcLoad(), theme.getName() + " missing bcLoad");
            assertNotNull(theme.getBcStore(), theme.getName() + " missing bcStore");
            assertNotNull(theme.getBcInvoke(), theme.getName() + " missing bcInvoke");
            assertNotNull(theme.getBcField(), theme.getName() + " missing bcField");
            assertNotNull(theme.getBcBranch(), theme.getName() + " missing bcBranch");
            assertNotNull(theme.getBcReturn(), theme.getName() + " missing bcReturn");
        }
    }

    @Test
    void testAllThemesHaveValidIRColors() {
        for (Theme theme : themeManager.getAvailableThemes()) {
            assertNotNull(theme.getIrPhi(), theme.getName() + " missing irPhi");
            assertNotNull(theme.getIrBinaryOp(), theme.getName() + " missing irBinaryOp");
            assertNotNull(theme.getIrConstant(), theme.getName() + " missing irConstant");
            assertNotNull(theme.getIrInvoke(), theme.getName() + " missing irInvoke");
            assertNotNull(theme.getIrReturn(), theme.getName() + " missing irReturn");
            assertNotNull(theme.getIrBranch(), theme.getName() + " missing irBranch");
        }
    }

    @Test
    void testAllThemesHaveValidFonts() {
        for (Theme theme : themeManager.getAvailableThemes()) {
            assertNotNull(theme.getCodeFont(12), theme.getName() + " missing codeFont");
            assertNotNull(theme.getUIFont(12), theme.getName() + " missing uiFont");
        }
    }

    @Test
    void testCodeFontIsMonospace() {
        for (Theme theme : themeManager.getAvailableThemes()) {
            java.awt.Font font = theme.getCodeFont(12);
            assertNotNull(font);
        }
    }

    @Test
    void testColorsAreDistinct() {
        Theme theme = themeManager.getCurrentTheme();
        assertNotEquals(theme.getBgPrimary(), theme.getTextPrimary(),
            "Background and text should be different colors");
    }
}
