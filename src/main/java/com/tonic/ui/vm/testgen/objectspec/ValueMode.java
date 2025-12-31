package com.tonic.ui.vm.testgen.objectspec;

import lombok.Getter;

@Getter
public enum ValueMode {
    FIXED("Fixed Value"),
    FUZZ("Fuzz (Generate Variants)"),
    OBJECT_SPEC("Configure Object..."),
    NULL("Null");

    private final String displayName;

    ValueMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
