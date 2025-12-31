package com.tonic.ui.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BookmarkStore {

    private final Map<String, Bookmark> bookmarksById;
    private final Bookmark[] quickSlots;

    public BookmarkStore() {
        this.bookmarksById = new ConcurrentHashMap<>();
        this.quickSlots = new Bookmark[10];
    }

    public void addBookmark(Bookmark bookmark) {
        if (bookmark == null || bookmark.getClassName() == null) {
            return;
        }
        bookmarksById.put(bookmark.getId(), bookmark);
        if (bookmark.hasSlot()) {
            quickSlots[bookmark.getSlot()] = bookmark;
        }
    }

    public void removeBookmark(String id) {
        Bookmark bookmark = bookmarksById.remove(id);
        if (bookmark != null && bookmark.hasSlot()) {
            if (quickSlots[bookmark.getSlot()] == bookmark) {
                quickSlots[bookmark.getSlot()] = null;
            }
        }
    }

    public void updateBookmark(String id, String newName, String newNotes) {
        Bookmark bookmark = bookmarksById.get(id);
        if (bookmark != null) {
            if (newName != null) {
                bookmark.setName(newName);
            }
            if (newNotes != null) {
                bookmark.setNotes(newNotes);
            }
        }
    }

    public Bookmark getBookmark(String id) {
        return bookmarksById.get(id);
    }

    public void setQuickSlot(int slot, Bookmark bookmark) {
        if (slot < 0 || slot > 9) {
            return;
        }
        Bookmark old = quickSlots[slot];
        if (old != null) {
            old.setSlot(Bookmark.NO_SLOT);
        }
        quickSlots[slot] = bookmark;
        if (bookmark != null) {
            int oldSlot = bookmark.getSlot();
            if (oldSlot >= 0 && oldSlot <= 9 && oldSlot != slot) {
                quickSlots[oldSlot] = null;
            }
            bookmark.setSlot(slot);
        }
    }

    public void clearQuickSlot(int slot) {
        if (slot < 0 || slot > 9) {
            return;
        }
        Bookmark bookmark = quickSlots[slot];
        if (bookmark != null) {
            bookmark.setSlot(Bookmark.NO_SLOT);
        }
        quickSlots[slot] = null;
    }

    public Bookmark getQuickSlot(int slot) {
        if (slot < 0 || slot > 9) {
            return null;
        }
        return quickSlots[slot];
    }

    public Bookmark[] getQuickSlots() {
        return quickSlots.clone();
    }

    public List<Bookmark> getAll() {
        List<Bookmark> list = new ArrayList<>(bookmarksById.values());
        list.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return list;
    }

    public List<Bookmark> getForClass(String className) {
        return bookmarksById.values().stream()
            .filter(b -> className.equals(b.getClassName()))
            .sorted(Comparator.comparingInt(Bookmark::getLineNumber))
            .collect(Collectors.toList());
    }

    public Bookmark findByLocation(String className, String memberName, int lineNumber) {
        for (Bookmark b : bookmarksById.values()) {
            if (className.equals(b.getClassName())) {
                boolean memberMatch = (memberName == null && b.getMemberName() == null) ||
                    (memberName != null && memberName.equals(b.getMemberName()));
                if (memberMatch && b.getLineNumber() == lineNumber) {
                    return b;
                }
            }
        }
        return null;
    }

    public int getBookmarkCount() {
        return bookmarksById.size();
    }

    public void clear() {
        bookmarksById.clear();
        for (int i = 0; i < 10; i++) {
            quickSlots[i] = null;
        }
    }

    public void setBookmarks(List<Bookmark> bookmarks) {
        clear();
        if (bookmarks != null) {
            for (Bookmark bookmark : bookmarks) {
                addBookmark(bookmark);
            }
        }
    }

    public Map<Integer, String> getQuickSlotIds() {
        Map<Integer, String> result = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            if (quickSlots[i] != null) {
                result.put(i, quickSlots[i].getId());
            }
        }
        return result;
    }

    public void restoreQuickSlots(Map<Integer, String> slotIds) {
        for (int i = 0; i < 10; i++) {
            quickSlots[i] = null;
        }
        if (slotIds != null) {
            for (Map.Entry<Integer, String> entry : slotIds.entrySet()) {
                int slot = entry.getKey();
                String id = entry.getValue();
                if (slot >= 0 && slot <= 9) {
                    Bookmark bookmark = bookmarksById.get(id);
                    if (bookmark != null) {
                        quickSlots[slot] = bookmark;
                        bookmark.setSlot(slot);
                    }
                }
            }
        }
    }
}
