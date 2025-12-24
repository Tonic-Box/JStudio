package com.tonic.ui.vm.debugger;

public class StackEntry {
    private final int index;
    private final String value;
    private final String typeName;
    private final String address;
    private final boolean wide;

    public StackEntry(int index, String value, String typeName, String address, boolean wide) {
        this.index = index;
        this.value = value;
        this.typeName = typeName;
        this.address = address;
        this.wide = wide;
    }

    public int getIndex() {
        return index;
    }

    public String getValue() {
        return value;
    }

    public String getTypeName() {
        return typeName;
    }

    public String getAddress() {
        return address;
    }

    public boolean isWide() {
        return wide;
    }

    @Override
    public String toString() {
        return "[" + index + "] " + typeName + ": " + value;
    }
}
