package com.tonic.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** The project's bookmarks, keyed by id, with ten quick slots that each point at one of them. */
public class BookmarkStore
{

    private final Map<String, Bookmark> bookmarksById;
    private final Bookmark[] quickSlots;

    /** Creates an empty store. */
    public BookmarkStore()
    {
        this.bookmarksById = new ConcurrentHashMap<>();
        this.quickSlots = new Bookmark[10];
    }

    /**
     * Adds a bookmark and binds its quick slot if it has one; ignores null and bookmarks without a class.
     *
     * @param bookmark the bookmark to add
     */
    public void addBookmark(Bookmark bookmark)
    {
        if (bookmark == null || bookmark.getClassName() == null)
        {
            return;
        }
        bookmarksById.put(bookmark.getId(), bookmark);
        if (bookmark.hasSlot())
        {
            quickSlots[bookmark.getSlot()] = bookmark;
        }
    }

    /**
     * Removes a bookmark and frees its quick slot if it still holds it.
     *
     * @param id the bookmark's id
     */
    public void removeBookmark(String id)
    {
        Bookmark bookmark = bookmarksById.remove(id);
        if (bookmark != null && bookmark.hasSlot())
        {
            if (quickSlots[bookmark.getSlot()] == bookmark)
            {
                quickSlots[bookmark.getSlot()] = null;
            }
        }
    }

    /**
     * Binds a bookmark to a quick slot, unbinding the slot's previous bookmark and the bookmark's previous slot; ignores slots outside 0 to 9.
     *
     * @param slot the slot number, 0 to 9
     * @param bookmark the bookmark to bind, or null to empty the slot
     */
    public void setQuickSlot(int slot, Bookmark bookmark)
    {
        if (slot < 0 || slot > 9)
        {
            return;
        }
        Bookmark old = quickSlots[slot];
        if (old != null)
        {
            old.setSlot(Bookmark.NO_SLOT);
        }
        quickSlots[slot] = bookmark;
        if (bookmark != null)
        {
            int oldSlot = bookmark.getSlot();
            if (oldSlot >= 0 && oldSlot <= 9 && oldSlot != slot)
            {
                quickSlots[oldSlot] = null;
            }
            bookmark.setSlot(slot);
        }
    }

    /**
     * Empties a quick slot and unbinds its bookmark; ignores slots outside 0 to 9.
     *
     * @param slot the slot number, 0 to 9
     */
    public void clearQuickSlot(int slot)
    {
        if (slot < 0 || slot > 9)
        {
            return;
        }
        Bookmark bookmark = quickSlots[slot];
        if (bookmark != null)
        {
            bookmark.setSlot(Bookmark.NO_SLOT);
        }
        quickSlots[slot] = null;
    }

    /**
     * Looks up the bookmark in a quick slot.
     *
     * @param slot the slot number, 0 to 9
     * @return the bookmark, or null if the slot is empty or out of range
     */
    public Bookmark getQuickSlot(int slot)
    {
        if (slot < 0 || slot > 9)
        {
            return null;
        }
        return quickSlots[slot];
    }

    /**
     * Lists every bookmark, newest first.
     *
     * @return a new list of the bookmarks
     */
    public List<Bookmark> getAll()
    {
        List<Bookmark> list = new ArrayList<>(bookmarksById.values());
        list.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return list;
    }

    /**
     * Lists the bookmarks on one class, ordered by line.
     *
     * @param className the class's internal name, with slashes
     * @return a new list of the class's bookmarks, empty if it has none
     */
    public List<Bookmark> getForClass(String className)
    {
        return bookmarksById.values().stream()
                .filter(b -> className.equals(b.getClassName()))
                .sorted(Comparator.comparingInt(Bookmark::getLineNumber))
                .collect(Collectors.toList());
    }

    /**
     * Counts the bookmarks.
     *
     * @return the number of bookmarks
     */
    public int getBookmarkCount()
    {
        return bookmarksById.size();
    }

    /** Removes every bookmark and empties every quick slot. */
    public void clear()
    {
        bookmarksById.clear();
        for (int i = 0; i < 10; i++)
        {
            quickSlots[i] = null;
        }
    }

    /**
     * Replaces the contents with the given bookmarks, rebinding the quick slots they carry.
     *
     * @param bookmarks the bookmarks, or null to leave the store empty
     */
    public void setBookmarks(List<Bookmark> bookmarks)
    {
        clear();
        if (bookmarks != null)
        {
            for (Bookmark bookmark : bookmarks)
            {
                addBookmark(bookmark);
            }
        }
    }

    /**
     * Maps each occupied quick slot to its bookmark's id, for saving.
     *
     * @return a new map from slot number to bookmark id
     */
    public Map<Integer, String> getQuickSlotIds()
    {
        Map<Integer, String> result = new HashMap<>();
        for (int i = 0; i < 10; i++)
        {
            if (quickSlots[i] != null)
            {
                result.put(i, quickSlots[i].getId());
            }
        }
        return result;
    }

    /**
     * Rebinds the quick slots from saved ids, skipping slots out of range and ids no longer in the store.
     *
     * @param slotIds the slot number to bookmark id map, or null to leave every slot empty
     */
    public void restoreQuickSlots(Map<Integer, String> slotIds)
    {
        for (int i = 0; i < 10; i++)
        {
            quickSlots[i] = null;
        }
        if (slotIds != null)
        {
            for (Map.Entry<Integer, String> entry : slotIds.entrySet())
            {
                int slot = entry.getKey();
                String id = entry.getValue();
                if (slot >= 0 && slot <= 9)
                {
                    Bookmark bookmark = bookmarksById.get(id);
                    if (bookmark != null)
                    {
                        quickSlots[slot] = bookmark;
                        bookmark.setSlot(slot);
                    }
                }
            }
        }
    }
}
