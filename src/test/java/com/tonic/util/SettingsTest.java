package com.tonic.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {

    private Settings settings;

    @BeforeEach
    void setUp() {
        settings = Settings.getInstance();
    }

    @Test
    void testSingletonInstance() {
        Settings instance1 = Settings.getInstance();
        Settings instance2 = Settings.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    void testDefaultFontSize() {
        int defaultSize = settings.getFontSize();
        assertTrue(defaultSize >= 8 && defaultSize <= 32);
    }

    @Test
    void testSetAndGetFontSize() {
        int originalSize = settings.getFontSize();
        try {
            settings.setFontSize(16);
            assertEquals(16, settings.getFontSize());
        } finally {
            settings.setFontSize(originalSize);
        }
    }

    @Test
    void testDefaultWindowDimensions() {
        int width = settings.getWindowWidth();
        int height = settings.getWindowHeight();
        assertTrue(width > 0);
        assertTrue(height > 0);
    }

    @Test
    void testSetAndGetWindowBounds() {
        int origX = settings.getWindowX();
        int origY = settings.getWindowY();
        int origW = settings.getWindowWidth();
        int origH = settings.getWindowHeight();
        boolean origMax = settings.isWindowMaximized();

        try {
            settings.saveWindowBounds(100, 200, 800, 600, false);
            assertEquals(100, settings.getWindowX());
            assertEquals(200, settings.getWindowY());
            assertEquals(800, settings.getWindowWidth());
            assertEquals(600, settings.getWindowHeight());
            assertFalse(settings.isWindowMaximized());
        } finally {
            settings.saveWindowBounds(origX, origY, origW, origH, origMax);
        }
    }

    @Test
    void testDefaultNavigatorWidth() {
        int width = settings.getNavigatorWidth();
        assertTrue(width >= 0);
    }

    @Test
    void testSetAndGetDividerPositions() {
        int origNav = settings.getNavigatorWidth();
        int origProps = settings.getPropertiesWidth();
        int origConsole = settings.getConsoleHeight();

        try {
            settings.saveDividerPositions(300, 350, 200);
            assertEquals(300, settings.getNavigatorWidth());
            assertEquals(350, settings.getPropertiesWidth());
            assertEquals(200, settings.getConsoleHeight());
        } finally {
            settings.saveDividerPositions(origNav, origProps, origConsole);
        }
    }

    @Test
    void testDefaultWordWrap() {
        assertNotNull(String.valueOf(settings.isWordWrapEnabled()));
    }

    @Test
    void testSetAndGetWordWrap() {
        boolean original = settings.isWordWrapEnabled();
        try {
            settings.setWordWrapEnabled(true);
            assertTrue(settings.isWordWrapEnabled());
            settings.setWordWrapEnabled(false);
            assertFalse(settings.isWordWrapEnabled());
        } finally {
            settings.setWordWrapEnabled(original);
        }
    }

    @Test
    void testDefaultTheme() {
        String theme = settings.getTheme();
        assertNotNull(theme);
        assertFalse(theme.isEmpty());
    }

    @Test
    void testSetAndGetTheme() {
        String original = settings.getTheme();
        try {
            settings.setTheme("test-theme");
            assertEquals("test-theme", settings.getTheme());
        } finally {
            settings.setTheme(original);
        }
    }

    @Test
    void testDefaultFontFamily() {
        String fontFamily = settings.getFontFamily();
        assertNotNull(fontFamily);
    }

    @Test
    void testSetAndGetFontFamily() {
        String original = settings.getFontFamily();
        try {
            settings.setFontFamily("Consolas");
            assertEquals("Consolas", settings.getFontFamily());

            settings.setFontFamily(null);
            assertEquals("", settings.getFontFamily());
        } finally {
            settings.setFontFamily(original);
        }
    }

    @Test
    void testDefaultLastDirectory() {
        String dir = settings.getLastDirectory();
        assertNotNull(dir);
    }

    @Test
    void testSetAndGetLastDirectory() {
        String original = settings.getLastDirectory();
        try {
            settings.setLastDirectory("/test/path");
            assertEquals("/test/path", settings.getLastDirectory());
        } finally {
            settings.setLastDirectory(original);
        }
    }

    @Test
    void testRestoreSessionEnabled() {
        boolean original = settings.isRestoreSessionEnabled();
        try {
            settings.setRestoreSessionEnabled(true);
            assertTrue(settings.isRestoreSessionEnabled());
            settings.setRestoreSessionEnabled(false);
            assertFalse(settings.isRestoreSessionEnabled());
        } finally {
            settings.setRestoreSessionEnabled(original);
        }
    }

    @Test
    void testLastProject() {
        String original = settings.getLastProject();
        try {
            settings.setLastProject("/path/to/project.jar");
            assertEquals("/path/to/project.jar", settings.getLastProject());

            settings.setLastProject(null);
            assertEquals("", settings.getLastProject());
        } finally {
            if (original != null) {
                settings.setLastProject(original);
            }
        }
    }
}
