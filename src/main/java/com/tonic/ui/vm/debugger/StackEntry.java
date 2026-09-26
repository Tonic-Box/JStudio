package com.tonic.ui.vm.debugger;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** One operand stack row in the debugger, already formatted for display. */
@Getter
@RequiredArgsConstructor
public class StackEntry
{
    private final int index;
    private final String value;
    private final String typeName;
    private final String address;
    private final boolean wide;

    @Override
    public String toString()
    {
        return "[" + index + "] " + typeName + ": " + value;
    }
}
