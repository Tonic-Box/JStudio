package com.tonic.util;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/** The application's persisted settings, stored in the user Preferences node for this package. */
public class Settings
{

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

    private Settings()
    {
        prefs = Preferences.userNodeForPackage(Settings.class);
    }

    /**
     * Returns the shared settings, creating it on first use.
     *
     * @return the shared settings
     */
    public static synchronized Settings getInstance()
    {
        if (instance == null)
        {
            instance = new Settings();
        }
        return instance;
    }

    /**
     * Reads the saved window x position.
     *
     * @return the window x position, or -1 if none is saved
     */
    public int getWindowX()
    {
        return prefs.getInt(PREF_WINDOW_X, -1);
    }

    /**
     * Saves the saved window x position.
     *
     * @param x the x position
     */
    public void setWindowX(int x)
    {
        prefs.putInt(PREF_WINDOW_X, x);
    }

    /**
     * Reads the saved window y position.
     *
     * @return the window y position, or -1 if none is saved
     */
    public int getWindowY()
    {
        return prefs.getInt(PREF_WINDOW_Y, -1);
    }

    /**
     * Saves the saved window y position.
     *
     * @param y the y position
     */
    public void setWindowY(int y)
    {
        prefs.putInt(PREF_WINDOW_Y, y);
    }

    /**
     * Reads the saved window width.
     *
     * @return the window width, 1400 by default
     */
    public int getWindowWidth()
    {
        return prefs.getInt(PREF_WINDOW_WIDTH, 1400);
    }

    /**
     * Saves the saved window width.
     *
     * @param width the width
     */
    public void setWindowWidth(int width)
    {
        prefs.putInt(PREF_WINDOW_WIDTH, width);
    }

    /**
     * Reads the saved window height.
     *
     * @return the window height, 900 by default
     */
    public int getWindowHeight()
    {
        return prefs.getInt(PREF_WINDOW_HEIGHT, 900);
    }

    /**
     * Saves the saved window height.
     *
     * @param height the height
     */
    public void setWindowHeight(int height)
    {
        prefs.putInt(PREF_WINDOW_HEIGHT, height);
    }

    /**
     * Reads whether the window was saved maximized.
     *
     * @return whether the window is maximized, false by default
     */
    public boolean isWindowMaximized()
    {
        return prefs.getBoolean(PREF_WINDOW_MAXIMIZED, false);
    }

    /**
     * Saves whether the window was saved maximized.
     *
     * @param maximized whether the window is maximized
     */
    public void setWindowMaximized(boolean maximized)
    {
        prefs.putBoolean(PREF_WINDOW_MAXIMIZED, maximized);
    }

    /**
     * Reads the saved navigator panel width.
     *
     * @return the navigator width, 250 by default
     */
    public int getNavigatorWidth()
    {
        return prefs.getInt(PREF_NAV_WIDTH, 250);
    }

    /**
     * Saves the saved navigator panel width.
     *
     * @param width the width
     */
    public void setNavigatorWidth(int width)
    {
        prefs.putInt(PREF_NAV_WIDTH, width);
    }

    /**
     * Reads the saved properties panel width.
     *
     * @return the properties panel width, 300 by default
     */
    public int getPropertiesWidth()
    {
        return prefs.getInt(PREF_PROPS_WIDTH, 300);
    }

    /**
     * Saves the saved properties panel width.
     *
     * @param width the width
     */
    public void setPropertiesWidth(int width)
    {
        prefs.putInt(PREF_PROPS_WIDTH, width);
    }

    /**
     * Reads the saved console panel height.
     *
     * @return the console height, 150 by default
     */
    public int getConsoleHeight()
    {
        return prefs.getInt(PREF_CONSOLE_HEIGHT, 150);
    }

    /**
     * Saves the saved console panel height.
     *
     * @param height the height
     */
    public void setConsoleHeight(int height)
    {
        prefs.putInt(PREF_CONSOLE_HEIGHT, height);
    }

    /**
     * Reads the editor font size.
     *
     * @return the font size, 13 by default
     */
    public int getFontSize()
    {
        return prefs.getInt(PREF_FONT_SIZE, 13);
    }

    /**
     * Saves the editor font size.
     *
     * @param size the size in points
     */
    public void setFontSize(int size)
    {
        prefs.putInt(PREF_FONT_SIZE, size);
    }

    /**
     * Reads the editor font family.
     *
     * @return the font family, or empty for the default font
     */
    public String getFontFamily()
    {
        return prefs.get(PREF_FONT_FAMILY, "");
    }

    /**
     * Saves the editor font family.
     *
     * @param family the family, or null for the default font
     */
    public void setFontFamily(String family)
    {
        prefs.put(PREF_FONT_FAMILY, family != null ? family : "");
    }

    /**
     * Reads whether the editor wraps long lines.
     *
     * @return whether word wrap is on, false by default
     */
    public boolean isWordWrapEnabled()
    {
        return prefs.getBoolean(PREF_WORD_WRAP, false);
    }

    /**
     * Saves whether the editor wraps long lines.
     *
     * @param enabled whether to wrap
     */
    public void setWordWrapEnabled(boolean enabled)
    {
        prefs.putBoolean(PREF_WORD_WRAP, enabled);
    }

    /**
     * Reads whether the editor shows usage counts above declarations.
     *
     * @return whether the usage lens is on, true by default
     */
    public boolean isUsageLensEnabled()
    {
        return prefs.getBoolean(PREF_USAGE_LENS, true);
    }

    /**
     * Saves whether the editor shows usage counts above declarations.
     *
     * @param enabled whether to show it
     */
    public void setUsageLensEnabled(boolean enabled)
    {
        prefs.putBoolean(PREF_USAGE_LENS, enabled);
    }

    /**
     * Reads the live agent jar path.
     *
     * @return the agent path, or empty if unset
     */
    public String getLiveAgentPath()
    {
        return prefs.get(PREF_LIVE_AGENT_PATH, "");
    }

    /**
     * Saves the live agent jar path.
     *
     * @param path the path, or null to clear it
     */
    public void setLiveAgentPath(String path)
    {
        prefs.put(PREF_LIVE_AGENT_PATH, path != null ? path : "");
    }

    /**
     * Reads the last directory used in a file chooser.
     *
     * @return the directory, the user's home by default
     */
    public String getLastDirectory()
    {
        return prefs.get(PREF_LAST_DIR, System.getProperty("user.home"));
    }

    /**
     * Saves the last directory used in a file chooser.
     *
     * @param dir the directory; must not be null
     */
    public void setLastDirectory(String dir)
    {
        prefs.put(PREF_LAST_DIR, dir);
    }

    /**
     * Reads whether dead code analysis treats public members as entry points.
     *
     * @return whether public members are entry points, false by default
     */
    public boolean isDeadCodePublicEntryPoints()
    {
        return prefs.getBoolean(PREF_DEADCODE_PUBLIC, false);
    }

    /**
     * Saves whether dead code analysis treats public members as entry points.
     *
     * @param v whether to treat them as entry points
     */
    public void setDeadCodePublicEntryPoints(boolean v)
    {
        prefs.putBoolean(PREF_DEADCODE_PUBLIC, v);
    }

    /**
     * Reads the dead code analysis keep list.
     *
     * @return the keep list text, or empty
     */
    public String getDeadCodeKeepList()
    {
        return prefs.get(PREF_DEADCODE_KEEP, "");
    }

    /**
     * Saves the dead code analysis keep list.
     *
     * @param v the keep list text, or null to clear it
     */
    public void setDeadCodeKeepList(String v)
    {
        prefs.put(PREF_DEADCODE_KEEP, v != null ? v : "");
    }

    /**
     * Reads the dead code analysis skip list.
     *
     * @return the skip list text, or empty
     */
    public String getDeadCodeSkipList()
    {
        return prefs.get(PREF_DEADCODE_SKIP, "");
    }

    /**
     * Saves the dead code analysis skip list.
     *
     * @param v the skip list text, or null to clear it
     */
    public void setDeadCodeSkipList(String v)
    {
        prefs.put(PREF_DEADCODE_SKIP, v != null ? v : "");
    }

    /**
     * Reads the ids of GUI plugins the user has disabled.
     *
     * @return the ids in saved order, empty if none
     */
    public Set<String> getDisabledPlugins()
    {
        String raw = prefs.get(PREF_PLUGINS_DISABLED, "");
        if (raw.isEmpty())
        {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(raw.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Saves the ids of GUI plugins the user has disabled.
     *
     * @param ids the ids, or null for none
     */
    public void setDisabledPlugins(Set<String> ids)
    {
        prefs.put(PREF_PLUGINS_DISABLED, ids == null ? "" : String.join("\n", ids));
    }

    /**
     * Reads the program arguments for Run.
     *
     * @return the arguments, or empty
     */
    public String getRunProgramArgs()
    {
        return prefs.get(PREF_RUN_ARGS, "");
    }

    /**
     * Saves the program arguments for Run.
     *
     * @param v the arguments, or null to clear them
     */
    public void setRunProgramArgs(String v)
    {
        prefs.put(PREF_RUN_ARGS, v != null ? v : "");
    }

    /**
     * Reads the JVM options for Run.
     *
     * @return the options, or empty
     */
    public String getRunVmOptions()
    {
        return prefs.get(PREF_RUN_VMOPTS, "");
    }

    /**
     * Saves the JVM options for Run.
     *
     * @param v the options, or null to clear them
     */
    public void setRunVmOptions(String v)
    {
        prefs.put(PREF_RUN_VMOPTS, v != null ? v : "");
    }

    /**
     * Reads the working directory for Run.
     *
     * @return the directory, or empty if unset
     */
    public String getRunWorkingDir()
    {
        return prefs.get(PREF_RUN_WORKDIR, "");
    }

    /**
     * Saves the working directory for Run.
     *
     * @param v the directory, or null to clear it
     */
    public void setRunWorkingDir(String v)
    {
        prefs.put(PREF_RUN_WORKDIR, v != null ? v : "");
    }

    /**
     * Reads the JDK home for Run.
     *
     * @return the JDK home, or empty if unset
     */
    public String getRunJdkHome()
    {
        return prefs.get(PREF_RUN_JDK, "");
    }

    /**
     * Saves the JDK home for Run.
     *
     * @param v the JDK home, or null to clear it
     */
    public void setRunJdkHome(String v)
    {
        prefs.put(PREF_RUN_JDK, v != null ? v : "");
    }

    /**
     * Reads whether the last project reopens on startup.
     *
     * @return whether session restore is on, false by default
     */
    public boolean isRestoreSessionEnabled()
    {
        return prefs.getBoolean(PREF_RESTORE_SESSION, false);
    }

    /**
     * Saves whether the last project reopens on startup.
     *
     * @param enabled whether to restore
     */
    public void setRestoreSessionEnabled(boolean enabled)
    {
        prefs.putBoolean(PREF_RESTORE_SESSION, enabled);
    }

    /**
     * Reads the last opened project.
     *
     * @return the project path, null if never saved, or empty if cleared
     */
    public String getLastProject()
    {
        return prefs.get(PREF_LAST_PROJECT, null);
    }

    /**
     * Saves the last opened project.
     *
     * @param path the path, or null to clear it
     */
    public void setLastProject(String path)
    {
        prefs.put(PREF_LAST_PROJECT, path != null ? path : "");
    }

    /**
     * Reads the UI theme name.
     *
     * @return the theme name, jstudio-dark by default
     */
    public String getTheme()
    {
        return prefs.get(PREF_THEME, "jstudio-dark");
    }

    /**
     * Saves the UI theme name.
     *
     * @param themeName the theme name; must not be null
     */
    public void setTheme(String themeName)
    {
        prefs.put(PREF_THEME, themeName);
    }

    /**
     * Saves the window bounds and maximized state.
     *
     * @param x the x position
     * @param y the y position
     * @param width the width
     * @param height the height
     * @param maximized whether the window is maximized
     */
    public void saveWindowBounds(int x, int y, int width, int height, boolean maximized)
    {
        setWindowX(x);
        setWindowY(y);
        setWindowWidth(width);
        setWindowHeight(height);
        setWindowMaximized(maximized);
    }

    /**
     * Saves the main window's divider positions.
     *
     * @param navWidth the navigator width
     * @param propsWidth the properties panel width
     * @param consoleHeight the console height
     */
    public void saveDividerPositions(int navWidth, int propsWidth, int consoleHeight)
    {
        setNavigatorWidth(navWidth);
        setPropertiesWidth(propsWidth);
        setConsoleHeight(consoleHeight);
    }

    /**
     * Reads whether JDK classes are loaded into the class pool.
     *
     * @return whether JDK classes load, true by default
     */
    public boolean isLoadJdkClassesEnabled()
    {
        return prefs.getBoolean(PREF_LOAD_JDK_CLASSES, true);
    }

    /**
     * Saves whether JDK classes are loaded into the class pool.
     *
     * @param enabled whether to load them
     */
    public void setLoadJdkClassesEnabled(boolean enabled)
    {
        prefs.putBoolean(PREF_LOAD_JDK_CLASSES, enabled);
    }

    /**
     * Reads whether a breakpoint suspends all threads rather than one.
     *
     * @return whether all threads suspend, true by default
     */
    public boolean isDebuggerSuspendAll()
    {
        return prefs.getBoolean(PREF_DEBUG_SUSPEND_ALL, true);
    }

    /**
     * Saves whether a breakpoint suspends all threads rather than one.
     *
     * @param enabled whether to suspend all threads
     */
    public void setDebuggerSuspendAll(boolean enabled)
    {
        prefs.putBoolean(PREF_DEBUG_SUSPEND_ALL, enabled);
    }

    /**
     * Reads whether updates are checked on startup.
     *
     * @return whether the startup check is on, true by default
     */
    public boolean isUpdateCheckEnabled()
    {
        return prefs.getBoolean(PREF_UPDATE_CHECK, true);
    }

    /**
     * Saves whether updates are checked on startup.
     *
     * @param enabled whether to check
     */
    public void setUpdateCheckEnabled(boolean enabled)
    {
        prefs.putBoolean(PREF_UPDATE_CHECK, enabled);
    }

    /**
     * Reads the update version the user chose to skip.
     *
     * @return the skipped version, or empty
     */
    public String getSkippedVersion()
    {
        return prefs.get(PREF_UPDATE_SKIPPED, "");
    }

    /**
     * Saves the update version the user chose to skip.
     *
     * @param version the version, or null to clear it
     */
    public void setSkippedVersion(String version)
    {
        prefs.put(PREF_UPDATE_SKIPPED, version != null ? version : "");
    }
}
