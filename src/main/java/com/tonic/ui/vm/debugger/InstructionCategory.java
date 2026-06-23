package com.tonic.ui.vm.debugger;

/**
 * Coarse opcode grouping used to colour disassembled instructions in the debugger's bytecode table.
 */
enum InstructionCategory {
    LOAD_STORE,
    ARITHMETIC,
    CONTROL_FLOW,
    INVOKE,
    FIELD_ACCESS,
    OBJECT,
    STACK,
    CONSTANT,
    OTHER
}
