package com.tonic.ui.dialog;

import java.awt.Window;

/**
 * Rename dialog for a local variable (or parameter) in the decompiled source view. Mirrors the field/method
 * rename dialogs: a single name field, identifier validation, and Cancel/Rename.
 */
public final class RenameLocalDialog extends AbstractRenameDialog {

    public RenameLocalDialog(Window owner, String currentName) {
        super(owner, "Rename Local Variable", "Current: " + currentName, currentName);
    }

    @Override
    protected String entityWord() {
        return "local variable";
    }
}
