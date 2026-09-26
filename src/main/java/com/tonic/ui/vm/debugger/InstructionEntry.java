package com.tonic.ui.vm.debugger;

class InstructionEntry
{
    final int index;
    final int offset;
    final String mnemonic;
    final String operands;
    final int lineNumber;
    final InstructionCategory category;
    boolean current;

    InstructionEntry(int index, int offset, String mnemonic, String operands, int lineNumber, InstructionCategory category, boolean current)
    {
        this.index = index;
        this.offset = offset;
        this.mnemonic = mnemonic;
        this.operands = operands;
        this.lineNumber = lineNumber;
        this.category = category;
        this.current = current;
    }
}
