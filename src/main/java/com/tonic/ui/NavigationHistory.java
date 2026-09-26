package com.tonic.ui;

import com.tonic.model.ClassEntryModel;

import java.util.ArrayList;
import java.util.List;

final class NavigationHistory
{

    private List<ClassEntryModel> entries = new ArrayList<>();
    private int index = -1;

    void push(ClassEntryModel classEntry)
    {
        if (index < entries.size() - 1)
        {
            entries = new ArrayList<>(entries.subList(0, index + 1));
        }
        entries.add(classEntry);
        index = entries.size() - 1;
    }

    ClassEntryModel back()
    {
        if (index > 0)
        {
            index--;
            return entries.get(index);
        }
        return null;
    }

    ClassEntryModel forward()
    {
        if (index < entries.size() - 1)
        {
            index++;
            return entries.get(index);
        }
        return null;
    }

    void clear()
    {
        entries.clear();
        index = -1;
    }
}
