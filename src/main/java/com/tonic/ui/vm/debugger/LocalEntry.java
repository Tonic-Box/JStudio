package com.tonic.ui.vm.debugger;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class LocalEntry {
    private final int slot;
    private final String name;
    private final String typeName;
    private final String value;
    private final boolean changed;

    @Override
    public String toString() {
        return "[" + slot + "] " + name + " (" + typeName + "): " + value;
    }
}
