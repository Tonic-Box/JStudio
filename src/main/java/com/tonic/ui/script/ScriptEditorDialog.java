package com.tonic.ui.script;

import com.tonic.ui.MainFrame;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.JStudioTheme;
import lombok.Getter;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Dialog wrapper for the script editor panel.
 */
@Getter
public class ScriptEditorDialog extends JDialog {

    /**
     * -- GETTER --
     *  Gets the editor panel.
     */
    private final ScriptEditorPanel editorPanel;

    public ScriptEditorDialog(MainFrame parent) {
        super(parent, "JStudio Script Editor", false);

        editorPanel = new ScriptEditorPanel(parent);

        setContentPane(editorPanel);
        setSize(1200, 800);
        setLocationRelativeTo(parent);

        // Apply dark theme to dialog
        getContentPane().setBackground(JStudioTheme.getBgTertiary());

        // Handle close
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Just hide, don't dispose - keeps state
            }
        });
    }

    /**
     * Sets the project model for the editor.
     */
    public void setProjectModel(ProjectModel model) {
        editorPanel.setProjectModel(model);
    }

    /**
     * Sets the current class for targeting.
     */
    public void setClass(ClassEntryModel classEntry) {
        editorPanel.setClass(classEntry);
    }

    /**
     * Sets a callback to run when transforms complete.
     */
    public void setOnTransformComplete(Runnable callback) {
        editorPanel.setOnTransformComplete(callback);
    }

}
