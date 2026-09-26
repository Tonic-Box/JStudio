package com.tonic.ui.dialog;

import java.awt.Window;

/** Rename dialog for a local variable or parameter in the decompiled source view. */
public final class RenameLocalDialog extends AbstractRenameDialog
{

    /**
     * Creates the dialog with the current name preselected.
     *
     * @param owner the window to center on and block
     * @param currentName the variable's current name
     */
    public RenameLocalDialog(Window owner, String currentName)
    {
        super(owner, "Rename Local Variable", "Current: " + currentName, currentName);
    }

    @Override
    protected String entityWord()
    {
        return "local variable";
    }
}
