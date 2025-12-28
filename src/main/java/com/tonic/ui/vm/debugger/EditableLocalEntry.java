package com.tonic.ui.vm.debugger;

import com.tonic.analysis.execution.state.ValueTag;
import com.tonic.ui.vm.debugger.edit.ValueParser;

public class EditableLocalEntry extends LocalEntry {

    private final ValueTag valueTag;
    private final Object rawValue;
    private boolean userModified;

    public EditableLocalEntry(int slot, String name, String typeName, String value,
                              boolean changed, ValueTag valueTag, Object rawValue) {
        super(slot, name, typeName, value, changed);
        this.valueTag = valueTag;
        this.rawValue = rawValue;
        this.userModified = false;
    }

    public ValueTag getValueTag() {
        return valueTag;
    }

    public Object getRawValue() {
        return rawValue;
    }

    public boolean isUserModified() {
        return userModified;
    }

    public void setUserModified(boolean userModified) {
        this.userModified = userModified;
    }

    public boolean isEditable() {
        return valueTag != null && ValueParser.isEditable(valueTag);
    }
}
