package com.tonic.ui.vm.debugger;

import com.tonic.analysis.execution.state.ValueTag;
import com.tonic.ui.vm.debugger.edit.ValueParser;
import lombok.Getter;

@Getter
public class EditableStackEntry extends StackEntry {

    private final ValueTag valueTag;
    private final Object rawValue;
    private boolean userModified;

    public EditableStackEntry(int index, String value, String typeName, String address,
                              boolean wide, ValueTag valueTag, Object rawValue) {
        super(index, value, typeName, address, wide);
        this.valueTag = valueTag;
        this.rawValue = rawValue;
        this.userModified = false;
    }

    public void setUserModified(boolean userModified) {
        this.userModified = userModified;
    }

    public boolean isEditable() {
        return valueTag != null && ValueParser.isEditable(valueTag);
    }
}
