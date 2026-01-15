package com.tonic.ui.editor;

import lombok.Getter;

/**
 * View modes for the code editor.
 */
@Getter
public enum ViewMode {
    SOURCE("Source", "Decompiled Java source code"),
    BYTECODE("Bytecode", "Raw JVM bytecode"),
    CONSTPOOL("Const Pool", "Constant pool entries"),
    IR("SSA IR", "SSA Intermediate Representation"),
    AST("AST IR", "Abstract Syntax Tree representation"),
    PDG("PDG", "Program Dependence Graph"),
    SDG("SDG", "System Dependence Graph"),
    CPG("CPG", "Code Property Graph"),
    CFG("Control Flow", "Control flow graph visualization"),
    HEX("Hex", "Raw class file bytes");

    private final String displayName;
    private final String description;

    ViewMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

}
