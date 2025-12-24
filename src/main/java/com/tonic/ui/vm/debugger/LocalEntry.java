package com.tonic.ui.vm.debugger;

public class LocalEntry {
    private final int slot;
    private final String name;
    private final String typeName;
    private final String value;
    private final boolean changed;

    public LocalEntry(int slot, String name, String typeName, String value, boolean changed) {
        this.slot = slot;
        this.name = name;
        this.typeName = typeName;
        this.value = value;
        this.changed = changed;
    }

    public int getSlot() {
        return slot;
    }

    public String getName() {
        return name;
    }

    public String getTypeName() {
        return typeName;
    }

    public String getValue() {
        return value;
    }

    public boolean isChanged() {
        return changed;
    }

    @Override
    public String toString() {
        return "[" + slot + "] " + name + " (" + typeName + "): " + value;
    }
}
