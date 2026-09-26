package com.tonic.ui.vm.debugger;

import com.tonic.analysis.execution.state.ValueTag;
import com.tonic.ui.vm.debugger.edit.ValueParser;
import lombok.Getter;

/** An operand stack row that carries its raw value and tag so the user can edit it. */
@Getter
public class EditableStackEntry extends StackEntry
{

    private final ValueTag valueTag;
    private final Object rawValue;
    private boolean userModified;

    /**
     * Creates an operand stack row, not yet marked as user-modified.
     *
     * @param index the position on the operand stack
     * @param value the displayed value
     * @param typeName the displayed type
     * @param address the displayed object address, if any
     * @param wide whether the value takes two stack slots
     * @param valueTag the kind of value, or null if unknown
     * @param rawValue the value as the VM holds it
     */
    public EditableStackEntry(int index, String value, String typeName, String address, boolean wide, ValueTag valueTag, Object rawValue)
    {
        super(index, value, typeName, address, wide);
        this.valueTag = valueTag;
        this.rawValue = rawValue;
        this.userModified = false;
    }

    /**
     * Marks whether the user has edited this value.
     *
     * @param userModified true once the user has edited it
     */
    public void setUserModified(boolean userModified)
    {
        this.userModified = userModified;
    }

    /**
     * Returns whether the value's kind can be edited in place.
     *
     * @return true if the tag is known and editable
     */
    public boolean isEditable()
    {
        return valueTag != null && ValueParser.isEditable(valueTag);
    }
}
