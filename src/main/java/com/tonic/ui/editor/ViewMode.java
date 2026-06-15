package com.tonic.ui.editor;

import lombok.Getter;

/**
 * View modes for the code editor.
 */
@Getter
public enum ViewMode {
    SOURCE("Source", "Decompiled Java source code"),
    BYTECODE("Bytecode", "Raw JVM bytecode"),
    DUAL("Dual", "Bytecode and source side by side, linked by double-click"),
    CONSTPOOL("Const Pool", "Constant pool entries"),
    IR("SSA IR", "SSA Intermediate Representation"),
    AST("AST IR", "Abstract Syntax Tree representation"),
    LLVM("LLVM IR", "LLVM IR lowering of the SSA form"),
    PDG("Program Dependence", "Program Dependence Graph"),
    SDG("System Dependence", "System Dependence Graph"),
    CPG("Code Property", "Code Property Graph"),
    CALLGRAPH("Call Graph", "Method call relationship graph"),
    CFG("Control Flow", "Control Flow Graph"),
    HEX("Hex", "Raw class file bytes"),
    ATTRIBUTES("Attributes", "Class, field, and method attributes"),
    STATISTICS("Statistics", "Class statistics and metrics"),
    LIVE_INSTANCES("Live Instances", "Object instances of this class in the attached JVM"),
    LIVE_STATICS("Live Statics", "Static fields and methods of this class in the attached JVM");

    private final String displayName;
    private final String description;

    ViewMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

}
