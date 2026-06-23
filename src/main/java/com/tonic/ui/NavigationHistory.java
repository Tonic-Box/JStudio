package com.tonic.ui;

import com.tonic.model.ClassEntryModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Linear back/forward navigation history of opened classes. Pushing a class while not at the
 * end of the history truncates the forward entries, mirroring browser-style navigation.
 */
final class NavigationHistory {

    private List<ClassEntryModel> entries = new ArrayList<>();
    private int index = -1;

    /** Records a newly-opened class as the current position, dropping any forward history. */
    void push(ClassEntryModel classEntry) {
        if (index < entries.size() - 1) {
            entries = new ArrayList<>(entries.subList(0, index + 1));
        }
        entries.add(classEntry);
        index = entries.size() - 1;
    }

    /** Steps back one entry and returns it, or {@code null} if already at the start. */
    ClassEntryModel back() {
        if (index > 0) {
            index--;
            return entries.get(index);
        }
        return null;
    }

    /** Steps forward one entry and returns it, or {@code null} if already at the end. */
    ClassEntryModel forward() {
        if (index < entries.size() - 1) {
            index++;
            return entries.get(index);
        }
        return null;
    }

    /** Resets to an empty history. */
    void clear() {
        entries.clear();
        index = -1;
    }
}
