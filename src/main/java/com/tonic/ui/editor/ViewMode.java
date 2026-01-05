package com.tonic.ui.editor;

import lombok.Getter;

/**
 * View modes for the code editor.
 */
@Getter
public enum ViewMode {
    SOURCE("Source", "Decompiled Java source code"),
    BYTECODE("Bytecode", "Raw JVM bytecode"),
    IR("IR", "SSA Intermediate Representation"),
    AST("AST", "Abstract Syntax Tree representation"),
    HEX("Hex", "Raw class file bytes");

    private final String displayName;
    private final String description;

    ViewMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

}
