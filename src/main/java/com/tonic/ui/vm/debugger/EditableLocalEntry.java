package com.tonic.ui.vm.debugger;

import com.tonic.analysis.execution.state.ValueTag;
import com.tonic.ui.vm.debugger.edit.ValueParser;
import lombok.Getter;

/** A local variable row that carries its raw value and tag so the user can edit it. */
@Getter
public class EditableLocalEntry extends LocalEntry
{

    private final ValueTag valueTag;
    private final Object rawValue;
    private boolean userModified;

    /**
     * Creates a local variable row, not yet marked as user-modified.
     *
     * @param slot the local variable slot
     * @param name the variable's name
     * @param typeName the displayed type
     * @param value the displayed value
     * @param changed whether the value changed since the last step
     * @param valueTag the kind of value in the slot, or null if unknown
     * @param rawValue the value as the VM holds it
     */
    public EditableLocalEntry(int slot, String name, String typeName, String value, boolean changed, ValueTag valueTag, Object rawValue)
    {
        super(slot, name, typeName, value, changed);
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
