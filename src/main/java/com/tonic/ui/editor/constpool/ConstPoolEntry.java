package com.tonic.ui.editor.constpool;

import lombok.Getter;

@Getter
public class ConstPoolEntry {
    private final int index;
    private final String type;
    private final String value;
    private final String rawValue;

    public ConstPoolEntry(int index, String type, String value, String rawValue) {
        this.index = index;
        this.type = type;
        this.value = value;
        this.rawValue = rawValue;
    }

    public ConstPoolEntry(int index, String type, String value) {
        this(index, type, value, value);
    }
}
