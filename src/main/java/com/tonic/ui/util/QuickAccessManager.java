package com.tonic.ui.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/** The file chooser's pinned and recent directories, persisted in user preferences; at most ten recent ones are kept. */
public class QuickAccessManager
{

    private static final String PINNED_PREFIX = "pinnedDir_";
    private static final String PINNED_COUNT = "pinnedDirCount";
    private static final String RECENT_PREFIX = "recentDir_";
    private static final String RECENT_COUNT = "recentDirCount";
    private static final int MAX_RECENT = 10;

    private static QuickAccessManager instance;
    private final Preferences prefs;
    private final List<File> pinnedDirectories;
    private final List<File> recentDirectories;
    private final List<QuickAccessListener> listeners;

    /** Hears about changes to the pinned or recent directories. */
    public interface QuickAccessListener
    {
        /**
         * Called after the pinned directories change.
         *
         * @param pinned the pinned directories that still exist, in order
         */
        void onPinnedChanged(List<File> pinned);

        /**
         * Called after the recent directories change.
         *
         * @param recent the recent directories that still exist, newest first
         */
        void onRecentChanged(List<File> recent);
    }

    private QuickAccessManager()
    {
        prefs = Preferences.userNodeForPackage(QuickAccessManager.class);
        pinnedDirectories = new ArrayList<>();
        recentDirectories = new ArrayList<>();
        listeners = new ArrayList<>();
        loadFromPreferences();
    }

    /**
     * Gives the shared manager, loading it from preferences on first use.
     *
     * @return the manager
     */
    public static synchronized QuickAccessManager getInstance()
    {
        if (instance == null)
        {
            instance = new QuickAccessManager();
        }
        return instance;
    }

    /**
     * Pins a directory at the end of the list; does nothing for null, a non-directory or one already pinned.
     *
     * @param dir the directory to pin
     */
    public void addPinned(File dir)
    {
        if (dir == null || !dir.isDirectory())
        {
            return;
        }

        if (isPinned(dir))
        {
            return;
        }

        pinnedDirectories.add(dir);
        savePinnedToPreferences();
        notifyPinnedChanged();
    }

    /**
     * Unpins a directory, matched by absolute path.
     *
     * @param dir the directory to unpin; null does nothing
     */
    public void removePinned(File dir)
    {
        if (dir == null)
        {
            return;
        }

        boolean removed = pinnedDirectories.removeIf(f -> f.getAbsolutePath().equals(dir.getAbsolutePath()));

        if (removed)
        {
            savePinnedToPreferences();
            notifyPinnedChanged();
        }
    }

    /**
     * Moves a pinned directory to another position; does nothing if either index is out of range.
     *
     * @param fromIndex the directory's current position
     * @param toIndex its new position
     */
    public void reorderPinned(int fromIndex, int toIndex)
    {
        if (fromIndex < 0 || fromIndex >= pinnedDirectories.size() ||
                toIndex < 0 || toIndex >= pinnedDirectories.size())
        {
            return;
        }

        File item = pinnedDirectories.remove(fromIndex);
        pinnedDirectories.add(toIndex, item);
        savePinnedToPreferences();
        notifyPinnedChanged();
    }

    /**
     * Moves a pinned directory one place earlier; does nothing if it is first or not pinned.
     *
     * @param dir the pinned directory
     */
    public void movePinnedUp(File dir)
    {
        int index = indexOfPinned(dir);
        if (index > 0)
        {
            reorderPinned(index, index - 1);
        }
    }

    /**
     * Moves a pinned directory one place later; does nothing if it is last or not pinned.
     *
     * @param dir the pinned directory
     */
    public void movePinnedDown(File dir)
    {
        int index = indexOfPinned(dir);
        if (index >= 0 && index < pinnedDirectories.size() - 1)
        {
            reorderPinned(index, index + 1);
        }
    }

    private int indexOfPinned(File dir)
    {
        if (dir == null) return -1;
        for (int i = 0; i < pinnedDirectories.size(); i++)
        {
            if (pinnedDirectories.get(i).getAbsolutePath().equals(dir.getAbsolutePath()))
            {
                return i;
            }
        }
        return -1;
    }

    /**
     * Tells whether a directory is pinned, matched by absolute path.
     *
     * @param dir the directory
     * @return true if pinned; false for null
     */
    public boolean isPinned(File dir)
    {
        if (dir == null) return false;
        return pinnedDirectories.stream()
                .anyMatch(f -> f.getAbsolutePath().equals(dir.getAbsolutePath()));
    }

    /**
     * Lists the pinned directories that still exist.
     *
     * @return a new list, in pinned order
     */
    public List<File> getPinnedDirectories()
    {
        List<File> result = new ArrayList<>();
        for (File dir : pinnedDirectories)
        {
            if (dir.exists() && dir.isDirectory())
            {
                result.add(dir);
            }
        }
        return result;
    }

    /**
     * Moves a directory to the front of the recent list, dropping the oldest past ten; ignores null, non-directories and pinned directories.
     *
     * @param dir the directory just used
     */
    public void addRecent(File dir)
    {
        if (dir == null || !dir.isDirectory())
        {
            return;
        }

        if (isPinned(dir))
        {
            return;
        }

        recentDirectories.removeIf(f -> f.getAbsolutePath().equals(dir.getAbsolutePath()));

        recentDirectories.add(0, dir);

        while (recentDirectories.size() > MAX_RECENT)
        {
            recentDirectories.remove(recentDirectories.size() - 1);
        }

        saveRecentToPreferences();
        notifyRecentChanged();
    }

    /**
     * Removes a directory from the recent list, matched by absolute path.
     *
     * @param dir the directory; null does nothing
     */
    public void removeRecent(File dir)
    {
        if (dir == null)
        {
            return;
        }

        boolean removed = recentDirectories.removeIf(f -> f.getAbsolutePath().equals(dir.getAbsolutePath()));

        if (removed)
        {
            saveRecentToPreferences();
            notifyRecentChanged();
        }
    }

    /** Empties the recent list. */
    public void clearRecent()
    {
        recentDirectories.clear();
        saveRecentToPreferences();
        notifyRecentChanged();
    }

    /**
     * Lists the recent directories that still exist.
     *
     * @return a new list, newest first
     */
    public List<File> getRecentDirectories()
    {
        List<File> result = new ArrayList<>();
        for (File dir : recentDirectories)
        {
            if (dir.exists() && dir.isDirectory())
            {
                result.add(dir);
            }
        }
        return result;
    }

    /**
     * Registers a listener; registering the same one twice has no effect.
     *
     * @param listener the listener to add
     */
    public void addListener(QuickAccessListener listener)
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
    public void removeListener(QuickAccessListener listener)
    {
        listeners.remove(listener);
    }

    private void loadFromPreferences()
    {
        pinnedDirectories.clear();
        int pinnedCount = prefs.getInt(PINNED_COUNT, 0);
        for (int i = 0; i < pinnedCount; i++)
        {
            String path = prefs.get(PINNED_PREFIX + i, null);
            if (path != null)
            {
                File dir = new File(path);
                if (dir.exists() && dir.isDirectory())
                {
                    pinnedDirectories.add(dir);
                }
            }
        }

        recentDirectories.clear();
        int recentCount = prefs.getInt(RECENT_COUNT, 0);
        for (int i = 0; i < recentCount && i < MAX_RECENT; i++)
        {
            String path = prefs.get(RECENT_PREFIX + i, null);
            if (path != null)
            {
                File dir = new File(path);
                if (dir.exists() && dir.isDirectory())
                {
                    recentDirectories.add(dir);
                }
            }
        }
    }

    private void savePinnedToPreferences()
    {
        int oldCount = prefs.getInt(PINNED_COUNT, 0);
        for (int i = 0; i < oldCount; i++)
        {
            prefs.remove(PINNED_PREFIX + i);
        }

        prefs.putInt(PINNED_COUNT, pinnedDirectories.size());
        for (int i = 0; i < pinnedDirectories.size(); i++)
        {
            prefs.put(PINNED_PREFIX + i, pinnedDirectories.get(i).getAbsolutePath());
        }
    }

    private void saveRecentToPreferences()
    {
        int oldCount = prefs.getInt(RECENT_COUNT, 0);
        for (int i = 0; i < oldCount; i++)
        {
            prefs.remove(RECENT_PREFIX + i);
        }

        prefs.putInt(RECENT_COUNT, recentDirectories.size());
        for (int i = 0; i < recentDirectories.size(); i++)
        {
            prefs.put(RECENT_PREFIX + i, recentDirectories.get(i).getAbsolutePath());
        }
    }

    private void notifyPinnedChanged()
    {
        List<File> pinned = getPinnedDirectories();
        for (QuickAccessListener listener : listeners)
        {
            listener.onPinnedChanged(pinned);
        }
    }

    private void notifyRecentChanged()
    {
        List<File> recent = getRecentDirectories();
        for (QuickAccessListener listener : listeners)
        {
            listener.onRecentChanged(recent);
        }
    }
}
