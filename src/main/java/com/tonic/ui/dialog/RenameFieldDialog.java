package com.tonic.ui.dialog;

import java.awt.Window;

/** Rename dialog for a field. */
public class RenameFieldDialog extends AbstractRenameDialog
{

    /**
     * Creates the dialog with the current name preselected.
     *
     * @param owner the window to center on and block
     * @param currentFieldName the field's current name
     * @param fieldDesc the field's descriptor, shown after the name
     */
    public RenameFieldDialog(Window owner, String currentFieldName, String fieldDesc)
    {
        super(owner, "Rename Field", "Current: " + currentFieldName + " : " + fieldDesc, currentFieldName);
    }

    @Override
    protected String entityWord()
    {
        return "field";
    }

    /**
     * The field name as currently typed.
     *
     * @return the name, trimmed
     */
    public String getNewFieldName()
    {
        return getNewName();
    }
}
