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
    PDG("Program Dependence", "Program Dependence Graph"),
    SDG("System Dependence", "System Dependence Graph"),
    CPG("Code Property", "Code Property Graph"),
    CFG("Control Flow", "Control Flow Graph"),
    HEX("Hex", "Raw class file bytes"),
    ATTRIBUTES("Attributes", "Class, field, and method attributes"),
    STATISTICS("Statistics", "Class statistics and metrics");

    private final String displayName;
    private final String description;

    ViewMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

}
