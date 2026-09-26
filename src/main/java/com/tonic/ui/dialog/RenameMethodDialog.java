package com.tonic.ui.dialog;

import java.awt.Window;

/** Rename dialog for a method. */
public class RenameMethodDialog extends AbstractRenameDialog
{

    /**
     * Creates the dialog with the current name preselected.
     *
     * @param owner the window to center on and block
     * @param currentMethodName the method's current name
     * @param methodDesc the method's descriptor, shown after the name
     */
    public RenameMethodDialog(Window owner, String currentMethodName, String methodDesc)
    {
        super(owner, "Rename Method", "Current: " + currentMethodName + methodDesc, currentMethodName);
    }

    @Override
    protected String entityWord()
    {
        return "method";
    }

    /**
     * The method name as currently typed.
     *
     * @return the name, trimmed
     */
    public String getNewMethodName()
    {
        return getNewName();
    }
}
