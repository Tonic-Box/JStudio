package com.tonic.ui.script;

import com.tonic.ui.MainFrame;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.ui.theme.JStudioTheme;
import lombok.Getter;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Dialog wrapper for the script editor panel.
 */
@Getter
public class ScriptEditorDialog extends JDialog
{

    /**
     * -- GETTER --
     *  Gets the editor panel.
     */
    private final ScriptEditorPanel editorPanel;

    /**
     * Builds the modeless editor window, which hides rather than closes.
     *
     * @param parent the window it belongs to and centers on
     */
    public ScriptEditorDialog(MainFrame parent)
    {
        super(parent, "JStudio Script Editor", false);

        editorPanel = new ScriptEditorPanel();

        setContentPane(editorPanel);
        setSize(1200, 800);
        setLocationRelativeTo(parent);

        getContentPane().setBackground(JStudioTheme.getBgTertiary());

        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
            }
        });
    }

    /**
     * Fills the editor's class picker from a project.
     *
     * @param model the project; null empties the picker
     */
    public void setProjectModel(ProjectModel model)
    {
        editorPanel.setProjectModel(model);
    }

    /**
     * Selects the class scripts run against.
     *
     * @param classEntry the class; null leaves the selection unchanged
     */
    public void setClass(ClassEntryModel classEntry)
    {
        editorPanel.setClass(classEntry);
    }

    /**
     * Sets what runs after a script transforms code.
     *
     * @param callback the callback, or null for none
     */
    public void setOnTransformComplete(Runnable callback)
    {
        editorPanel.setOnTransformComplete(callback);
    }

}
