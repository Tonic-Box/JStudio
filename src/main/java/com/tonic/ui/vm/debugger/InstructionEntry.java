package com.tonic.ui.vm.debugger;

/**
 * One disassembled instruction row: its display index, byte offset, mnemonic, formatted operands, source line,
 * colouring category, and whether it is the currently executing instruction.
 */
class InstructionEntry {
    final int index;
    final int offset;
    final String mnemonic;
    final String operands;
    final int lineNumber;
    final InstructionCategory category;
    boolean current;

    InstructionEntry(int index, int offset, String mnemonic, String operands, int lineNumber, InstructionCategory category, boolean current) {
        this.index = index;
        this.offset = offset;
        this.mnemonic = mnemonic;
        this.operands = operands;
        this.lineNumber = lineNumber;
        this.category = category;
        this.current = current;
    }
}
