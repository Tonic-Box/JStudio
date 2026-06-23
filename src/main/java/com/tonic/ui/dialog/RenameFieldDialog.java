package com.tonic.ui.dialog;

import java.awt.Window;

/** Rename dialog for a field. */
public class RenameFieldDialog extends AbstractRenameDialog {

    public RenameFieldDialog(Window owner, String currentFieldName, String fieldDesc) {
        super(owner, "Rename Field", "Current: " + currentFieldName + " : " + fieldDesc, currentFieldName);
    }

    @Override
    protected String entityWord() {
        return "field";
    }

    public String getNewFieldName() {
        return getNewName();
    }
}
