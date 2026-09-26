package com.tonic.ui.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Manages the list of recently opened files using Java Preferences API.
 */
public class RecentFilesManager
{

    private static final String PREFS_KEY_PREFIX = "recentFile_";
    private static final String PREFS_KEY_COUNT = "recentFileCount";
    private static final int MAX_RECENT_FILES = 10;

    private static RecentFilesManager instance;
    private final Preferences prefs;
    private final List<File> recentFiles;
    private final List<RecentFilesListener> listeners;

    /** Hears about changes to the recent files list. */
    public interface RecentFilesListener
    {
        /**
         * Called after the list changes.
         *
         * @param recentFiles the recent files that still exist, newest first
         */
        void onRecentFilesChanged(List<File> recentFiles);
    }

    private RecentFilesManager()
    {
        prefs = Preferences.userNodeForPackage(RecentFilesManager.class);
        recentFiles = new ArrayList<>();
        listeners = new ArrayList<>();
        loadFromPreferences();
    }

    /**
     * Gives the shared manager, loading it from preferences on first use.
     *
     * @return the manager
     */
    public static synchronized RecentFilesManager getInstance()
    {
        if (instance == null)
        {
            instance = new RecentFilesManager();
        }
        return instance;
    }

    /**
     * Moves a file to the front of the list, dropping the oldest past ten.
     *
     * @param file the file just opened; null or a missing file does nothing
     */
    public void addFile(File file)
    {
        if (file == null || !file.exists())
        {
            return;
        }

        recentFiles.removeIf(f -> f.getAbsolutePath().equals(file.getAbsolutePath()));

        recentFiles.add(0, file);

        while (recentFiles.size() > MAX_RECENT_FILES)
        {
            recentFiles.remove(recentFiles.size() - 1);
        }

        saveToPreferences();
        notifyListeners();
    }

    /**
     * Lists the recent files that still exist.
     *
     * @return a new list, newest first
     */
    public List<File> getRecentFiles()
    {
        List<File> result = new ArrayList<>();
        for (File file : recentFiles)
        {
            if (file.exists())
            {
                result.add(file);
            }
        }
        return result;
    }

    /**
     * Finds the newest recent file that still exists.
     *
     * @return the file, or null if none exists
     */
    public File getMostRecent()
    {
        for (File file : recentFiles)
        {
            if (file.exists())
            {
                return file;
            }
        }
        return null;
    }

    /** Empties the list. */
    public void clear()
    {
        recentFiles.clear();
        saveToPreferences();
        notifyListeners();
    }

    /**
     * Registers a listener; registering the same one twice has no effect.
     *
     * @param listener the listener to add
     */
    public void addListener(RecentFilesListener listener)
    {
        if (!listeners.contains(listener))
        {
            listeners.add(listener);
        }
    }

    /**
     * Unregisters a listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(RecentFilesListener listener)
    {
        listeners.remove(listener);
    }

    private void loadFromPreferences()
    {
        recentFiles.clear();
        int count = prefs.getInt(PREFS_KEY_COUNT, 0);
        for (int i = 0; i < count && i < MAX_RECENT_FILES; i++)
        {
            String path = prefs.get(PREFS_KEY_PREFIX + i, null);
            if (path != null)
            {
                File file = new File(path);
                if (file.exists())
                {
                    recentFiles.add(file);
                }
            }
        }
    }

    private void saveToPreferences()
    {
        int oldCount = prefs.getInt(PREFS_KEY_COUNT, 0);
        for (int i = 0; i < oldCount; i++)
        {
            prefs.remove(PREFS_KEY_PREFIX + i);
        }

        prefs.putInt(PREFS_KEY_COUNT, recentFiles.size());
        for (int i = 0; i < recentFiles.size(); i++)
        {
            prefs.put(PREFS_KEY_PREFIX + i, recentFiles.get(i).getAbsolutePath());
        }
    }

    private void notifyListeners()
    {
        List<File> files = getRecentFiles();
        for (RecentFilesListener listener : listeners)
        {
            listener.onRecentFilesChanged(files);
        }
    }
}
