package com.tonic.util;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

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
    private static final String PREF_USAGE_LENS = "editor.usageLens";
    private static final String PREF_LIVE_AGENT_PATH = "live.agentPath";
    private static final String PREF_LAST_DIR = "file.lastDirectory";

    private static final String PREF_RESTORE_SESSION = "session.restore";
    private static final String PREF_LAST_PROJECT = "session.lastProject";
    private static final String PREF_THEME = "appearance.theme";
    private static final String PREF_LOAD_JDK_CLASSES = "classpool.loadJdk";
    private static final String PREF_DEBUG_SUSPEND_ALL = "debug.suspendAll";

    private static final String PREF_UPDATE_CHECK = "update.checkOnStartup";
    private static final String PREF_UPDATE_SKIPPED = "update.skippedVersion";

    private static final String PREF_DEADCODE_PUBLIC = "deadcode.publicEntryPoints";
    private static final String PREF_DEADCODE_KEEP = "deadcode.keepList";
    private static final String PREF_DEADCODE_SKIP = "deadcode.skipList";

    private static final String PREF_RUN_ARGS = "run.programArgs";
    private static final String PREF_RUN_VMOPTS = "run.vmOptions";
    private static final String PREF_RUN_WORKDIR = "run.workingDir";
    private static final String PREF_RUN_JDK = "run.jdkHome";

    private static final String PREF_PLUGINS_DISABLED = "plugins.disabled";

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

    public boolean isUsageLensEnabled() { return prefs.getBoolean(PREF_USAGE_LENS, true); }
    public void setUsageLensEnabled(boolean enabled) { prefs.putBoolean(PREF_USAGE_LENS, enabled); }

    public String getLiveAgentPath() { return prefs.get(PREF_LIVE_AGENT_PATH, ""); }
    public void setLiveAgentPath(String path) { prefs.put(PREF_LIVE_AGENT_PATH, path != null ? path : ""); }

    // File chooser
    public String getLastDirectory() { return prefs.get(PREF_LAST_DIR, System.getProperty("user.home")); }
    public void setLastDirectory(String dir) { prefs.put(PREF_LAST_DIR, dir); }

    // Remove Dead Code
    public boolean isDeadCodePublicEntryPoints() { return prefs.getBoolean(PREF_DEADCODE_PUBLIC, false); }
    public void setDeadCodePublicEntryPoints(boolean v) { prefs.putBoolean(PREF_DEADCODE_PUBLIC, v); }
    public String getDeadCodeKeepList() { return prefs.get(PREF_DEADCODE_KEEP, ""); }
    public void setDeadCodeKeepList(String v) { prefs.put(PREF_DEADCODE_KEEP, v != null ? v : ""); }
    public String getDeadCodeSkipList() { return prefs.get(PREF_DEADCODE_SKIP, ""); }
    public void setDeadCodeSkipList(String v) { prefs.put(PREF_DEADCODE_SKIP, v != null ? v : ""); }

    /** Ids of GUI plugins the user has disabled (newline-separated in prefs). */
    public Set<String> getDisabledPlugins() {
        String raw = prefs.get(PREF_PLUGINS_DISABLED, "");
        if (raw.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(raw.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void setDisabledPlugins(Set<String> ids) {
        prefs.put(PREF_PLUGINS_DISABLED, ids == null ? "" : String.join("\n", ids));
    }

    // Run configuration
    public String getRunProgramArgs() { return prefs.get(PREF_RUN_ARGS, ""); }
    public void setRunProgramArgs(String v) { prefs.put(PREF_RUN_ARGS, v != null ? v : ""); }
    public String getRunVmOptions() { return prefs.get(PREF_RUN_VMOPTS, ""); }
    public void setRunVmOptions(String v) { prefs.put(PREF_RUN_VMOPTS, v != null ? v : ""); }
    public String getRunWorkingDir() { return prefs.get(PREF_RUN_WORKDIR, ""); }
    public void setRunWorkingDir(String v) { prefs.put(PREF_RUN_WORKDIR, v != null ? v : ""); }
    public String getRunJdkHome() { return prefs.get(PREF_RUN_JDK, ""); }
    public void setRunJdkHome(String v) { prefs.put(PREF_RUN_JDK, v != null ? v : ""); }

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

    // Debugger (JDI): suspend the whole VM on a breakpoint hit (off = only the thread that hit)
    public boolean isDebuggerSuspendAll() { return prefs.getBoolean(PREF_DEBUG_SUSPEND_ALL, true); }
    public void setDebuggerSuspendAll(boolean enabled) { prefs.putBoolean(PREF_DEBUG_SUSPEND_ALL, enabled); }

    // Update checks
    public boolean isUpdateCheckEnabled() { return prefs.getBoolean(PREF_UPDATE_CHECK, true); }
    public void setUpdateCheckEnabled(boolean enabled) { prefs.putBoolean(PREF_UPDATE_CHECK, enabled); }

    public String getSkippedVersion() { return prefs.get(PREF_UPDATE_SKIPPED, ""); }
    public void setSkippedVersion(String version) { prefs.put(PREF_UPDATE_SKIPPED, version != null ? version : ""); }
}
