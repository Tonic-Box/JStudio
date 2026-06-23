package com.tonic.ui.dialog;

import java.awt.Window;

/** Rename dialog for a method. */
public class RenameMethodDialog extends AbstractRenameDialog {

    public RenameMethodDialog(Window owner, String currentMethodName, String methodDesc) {
        super(owner, "Rename Method", "Current: " + currentMethodName + methodDesc, currentMethodName);
    }

    @Override
    protected String entityWord() {
        return "method";
    }

    public String getNewMethodName() {
        return getNewName();
    }
}
