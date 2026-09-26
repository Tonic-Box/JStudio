package com.tonic.ui.dialog;

import com.tonic.ui.MainFrame;
import com.tonic.ui.core.component.ThemedJDialog;
import com.tonic.model.ProjectModel;
import com.tonic.model.Snapshot;
import com.tonic.service.NameDeobfuscator;
import com.tonic.service.ProjectService;
import com.tonic.service.history.LocalHistoryService;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;

/** Modal dialog that renames the project's classes, methods and fields to sequential names such as Class1, method1 and field1, logging each rename. */
public class DeobfuscateNamesDialog extends ThemedJDialog
{

    private final JCheckBox renameClassesBox;
    private final JCheckBox renameMethodsBox;
    private final JCheckBox renameFieldsBox;
    private final JCheckBox skipJdkBox;
    private final JTextArea logArea;
    private final JButton applyButton;
    private final MainFrame mainFrame;


    /**
     * Creates the dialog with every option checked.
     *
     * @param mainFrame the main window, which owns the dialog and has its navigator refreshed after a rename
     */
    public DeobfuscateNamesDialog(MainFrame mainFrame)
    {
        super(mainFrame, "Deobfuscate Names", ModalityType.APPLICATION_MODAL);
        this.mainFrame = mainFrame;

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        content.setBackground(JStudioTheme.getBgPrimary());

        JPanel optionsPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        optionsPanel.setBackground(JStudioTheme.getBgPrimary());
        optionsPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()), "Options"));

        renameClassesBox = createCheckBox("Rename classes to Class1, Class2, ...", true);
        renameMethodsBox = createCheckBox("Rename methods to method1, method2, ...", true);
        renameFieldsBox = createCheckBox("Rename fields to field1, field2, ...", true);
        skipJdkBox = createCheckBox("Skip JDK/library classes (java/*, javax/*, etc.)", true);

        optionsPanel.add(renameClassesBox);
        optionsPanel.add(renameMethodsBox);
        optionsPanel.add(renameFieldsBox);
        optionsPanel.add(skipJdkBox);

        content.add(optionsPanel, BorderLayout.NORTH);

        logArea = new JTextArea(15, 50);
        logArea.setEditable(false);
        logArea.setFont(JStudioTheme.getCodeFont(11));
        logArea.setBackground(JStudioTheme.getBgSecondary());
        logArea.setForeground(JStudioTheme.getTextPrimary());

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()), "Log"));
        content.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(JStudioTheme.getBgPrimary());

        JButton closeButton = new JButton("Close");
        styleButton(closeButton, false);
        closeButton.addActionListener(e -> dispose());

        applyButton = new JButton("Apply");
        styleButton(applyButton, true);
        applyButton.addActionListener(e -> applyDeobfuscation());

        buttonPanel.add(closeButton);
        buttonPanel.add(applyButton);
        content.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setLocationRelativeTo(mainFrame);
        setMinimumSize(new Dimension(500, 400));
    }

    private JCheckBox createCheckBox(String text, boolean selected)
    {
        JCheckBox box = new JCheckBox(text, selected);
        box.setBackground(JStudioTheme.getBgPrimary());
        box.setForeground(JStudioTheme.getTextPrimary());
        return box;
    }

    private void styleButton(JButton button, boolean primary)
    {
        if (primary)
        {
            button.setBackground(JStudioTheme.getAccent());
            button.setForeground(Color.WHITE);
        }
        else
        {
            button.setBackground(JStudioTheme.getBgSecondary());
            button.setForeground(JStudioTheme.getTextPrimary());
        }
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()), BorderFactory.createEmptyBorder(6, 16, 6, 16)));
    }

    private void log(String message)
    {
        SwingUtilities.invokeLater(() ->
        {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void applyDeobfuscation()
    {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null || project.getClassPool() == null)
        {
            log("ERROR: No project loaded");
            return;
        }

        applyButton.setEnabled(false);
        logArea.setText("");
        mainFrame.setNavigatorLoading(true);
        NameDeobfuscator deobfuscator = new NameDeobfuscator(project, renameClassesBox.isSelected(), renameMethodsBox.isSelected(), renameFieldsBox.isSelected(), skipJdkBox.isSelected(), this::log);

        new SwingWorker<NameDeobfuscator.Result, Void>()
        {
            @Override
            protected NameDeobfuscator.Result doInBackground()
            {
                log("Starting deobfuscation...");
                LocalHistoryService.getInstance().snapshot("Deobfuscate names", Snapshot.Trigger.DEOBFUSCATE);
                NameDeobfuscator.Result result = deobfuscator.apply();
                log("");
                log("=== Summary ===");
                log("Classes renamed: " + result.getClasses());
                log("Methods renamed: " + result.getMethods());
                log("Fields renamed: " + result.getFields());
                log("");
                log("Done!");
                return result;
            }

            @Override
            protected void done()
            {
                applyButton.setEnabled(true);
                try
                {
                    NameDeobfuscator.Result result = get();
                    mainFrame.refreshAfterBulkRename(result.getOldClassNames(), result.getClasses() + result.getMethods() + result.getFields());
                }
                catch (Exception e)
                {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    log("ERROR: " + cause.getClass().getSimpleName() + " - " + cause.getMessage());
                    mainFrame.setNavigatorLoading(false);
                }
            }
        }.execute();
    }
}
