package com.tonic.ui.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Manages pinned and recent directories for the file chooser using Java Preferences API.
 */
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

    public interface QuickAccessListener
    {
        void onPinnedChanged(List<File> pinned);

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

    public static synchronized QuickAccessManager getInstance()
    {
        if (instance == null)
        {
            instance = new QuickAccessManager();
        }
        return instance;
    }

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

    public void movePinnedUp(File dir)
    {
        int index = indexOfPinned(dir);
        if (index > 0)
        {
            reorderPinned(index, index - 1);
        }
    }

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

    public boolean isPinned(File dir)
    {
        if (dir == null) return false;
        return pinnedDirectories.stream()
                .anyMatch(f -> f.getAbsolutePath().equals(dir.getAbsolutePath()));
    }

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

    public void clearRecent()
    {
        recentDirectories.clear();
        saveRecentToPreferences();
        notifyRecentChanged();
    }

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

    public void addListener(QuickAccessListener listener)
    {
        if (!listeners.contains(listener))
        {
            listeners.add(listener);
        }
    }

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
