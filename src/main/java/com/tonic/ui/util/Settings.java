package com.tonic.ui.util;

import java.util.prefs.Preferences;

/**
 * Application settings using Java Preferences API.
 */
public class Settings {

    private static final String PREF_WINDOW_X = "window.x";
    private static final String PREF_WINDOW_Y = "window.y";
    private static final String PREF_WINDOW_WIDTH = "window.width";
    private static final String PREF_WINDOW_HEIGHT = "window.height";
    private static final String PREF_WINDOW_MAXIMIZED = "window.maximized";

    private static final String PREF_NAV_WIDTH = "divider.navigator";
    private static final String PREF_PROPS_WIDTH = "divider.properties";
    private static final String PREF_CONSOLE_HEIGHT = "divider.console";

    private static final String PREF_FONT_SIZE = "editor.fontSize";
    private static final String PREF_FONT_FAMILY = "editor.fontFamily";
    private static final String PREF_WORD_WRAP = "editor.wordWrap";
    private static final String PREF_LAST_DIR = "file.lastDirectory";

    private static final String PREF_RESTORE_SESSION = "session.restore";
    private static final String PREF_LAST_PROJECT = "session.lastProject";
    private static final String PREF_THEME = "appearance.theme";
    private static final String PREF_LOAD_JDK_CLASSES = "classpool.loadJdk";

    private static Settings instance;
    private final Preferences prefs;

    private Settings() {
        prefs = Preferences.userNodeForPackage(Settings.class);
    }

    public static synchronized Settings getInstance() {
        if (instance == null) {
            instance = new Settings();
        }
        return instance;
    }

    // Window bounds
    public int getWindowX() { return prefs.getInt(PREF_WINDOW_X, -1); }
    public void setWindowX(int x) { prefs.putInt(PREF_WINDOW_X, x); }

    public int getWindowY() { return prefs.getInt(PREF_WINDOW_Y, -1); }
    public void setWindowY(int y) { prefs.putInt(PREF_WINDOW_Y, y); }

    public int getWindowWidth() { return prefs.getInt(PREF_WINDOW_WIDTH, 1400); }
    public void setWindowWidth(int width) { prefs.putInt(PREF_WINDOW_WIDTH, width); }

    public int getWindowHeight() { return prefs.getInt(PREF_WINDOW_HEIGHT, 900); }
    public void setWindowHeight(int height) { prefs.putInt(PREF_WINDOW_HEIGHT, height); }

    public boolean isWindowMaximized() { return prefs.getBoolean(PREF_WINDOW_MAXIMIZED, false); }
    public void setWindowMaximized(boolean maximized) { prefs.putBoolean(PREF_WINDOW_MAXIMIZED, maximized); }

    // Divider positions
    public int getNavigatorWidth() { return prefs.getInt(PREF_NAV_WIDTH, 250); }
    public void setNavigatorWidth(int width) { prefs.putInt(PREF_NAV_WIDTH, width); }

    public int getPropertiesWidth() { return prefs.getInt(PREF_PROPS_WIDTH, 300); }
    public void setPropertiesWidth(int width) { prefs.putInt(PREF_PROPS_WIDTH, width); }

    public int getConsoleHeight() { return prefs.getInt(PREF_CONSOLE_HEIGHT, 150); }
    public void setConsoleHeight(int height) { prefs.putInt(PREF_CONSOLE_HEIGHT, height); }

    // Editor settings
    public int getFontSize() { return prefs.getInt(PREF_FONT_SIZE, 13); }
    public void setFontSize(int size) { prefs.putInt(PREF_FONT_SIZE, size); }

    public String getFontFamily() { return prefs.get(PREF_FONT_FAMILY, ""); }
    public void setFontFamily(String family) { prefs.put(PREF_FONT_FAMILY, family != null ? family : ""); }

    public boolean isWordWrapEnabled() { return prefs.getBoolean(PREF_WORD_WRAP, false); }
    public void setWordWrapEnabled(boolean enabled) { prefs.putBoolean(PREF_WORD_WRAP, enabled); }

    // File chooser
    public String getLastDirectory() { return prefs.get(PREF_LAST_DIR, System.getProperty("user.home")); }
    public void setLastDirectory(String dir) { prefs.put(PREF_LAST_DIR, dir); }

    // Session restore
    public boolean isRestoreSessionEnabled() { return prefs.getBoolean(PREF_RESTORE_SESSION, false); }
    public void setRestoreSessionEnabled(boolean enabled) { prefs.putBoolean(PREF_RESTORE_SESSION, enabled); }

    public String getLastProject() { return prefs.get(PREF_LAST_PROJECT, null); }
    public void setLastProject(String path) { prefs.put(PREF_LAST_PROJECT, path != null ? path : ""); }

    // Theme
    public String getTheme() { return prefs.get(PREF_THEME, "jstudio-dark"); }
    public void setTheme(String themeName) { prefs.put(PREF_THEME, themeName); }

    /**
     * Save window bounds.
     */
    public void saveWindowBounds(int x, int y, int width, int height, boolean maximized) {
        setWindowX(x);
        setWindowY(y);
        setWindowWidth(width);
        setWindowHeight(height);
        setWindowMaximized(maximized);
    }

    /**
     * Save divider positions.
     */
    public void saveDividerPositions(int navWidth, int propsWidth, int consoleHeight) {
        setNavigatorWidth(navWidth);
        setPropertiesWidth(propsWidth);
        setConsoleHeight(consoleHeight);
    }

    // Execution settings
    public boolean isLoadJdkClassesEnabled() { return prefs.getBoolean(PREF_LOAD_JDK_CLASSES, true); }
    public void setLoadJdkClassesEnabled(boolean enabled) { prefs.putBoolean(PREF_LOAD_JDK_CLASSES, enabled); }
}
