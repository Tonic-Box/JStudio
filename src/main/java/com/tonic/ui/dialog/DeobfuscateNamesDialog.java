package com.tonic.ui.dialog;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.renamer.Renamer;
import com.tonic.renamer.exception.RenameException;
import com.tonic.ui.core.component.ThemedJDialog;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.service.ProjectService;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DeobfuscateNamesDialog extends ThemedJDialog {

    private final JCheckBox renameClassesBox;
    private final JCheckBox renameMethodsBox;
    private final JCheckBox renameFieldsBox;
    private final JCheckBox skipJdkBox;
    private final JTextArea logArea;
    private final JButton applyButton;
    private final JButton closeButton;
    private final com.tonic.ui.MainFrame mainFrame;

    private int classCounter = 1;
    private int methodCounter = 1;
    private int fieldCounter = 1;

    public DeobfuscateNamesDialog(com.tonic.ui.MainFrame mainFrame) {
        super(mainFrame, "Deobfuscate Names", ModalityType.APPLICATION_MODAL);
        this.mainFrame = mainFrame;

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        content.setBackground(JStudioTheme.getBgPrimary());

        JPanel optionsPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        optionsPanel.setBackground(JStudioTheme.getBgPrimary());
        optionsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                "Options"));

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
        scrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                "Log"));
        content.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(JStudioTheme.getBgPrimary());

        closeButton = new JButton("Close");
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

    private JCheckBox createCheckBox(String text, boolean selected) {
        JCheckBox box = new JCheckBox(text, selected);
        box.setBackground(JStudioTheme.getBgPrimary());
        box.setForeground(JStudioTheme.getTextPrimary());
        return box;
    }

    private void styleButton(JButton button, boolean primary) {
        if (primary) {
            button.setBackground(JStudioTheme.getAccent());
            button.setForeground(Color.WHITE);
        } else {
            button.setBackground(JStudioTheme.getBgSecondary());
            button.setForeground(JStudioTheme.getTextPrimary());
        }
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                BorderFactory.createEmptyBorder(6, 16, 6, 16)
        ));
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void applyDeobfuscation() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null || project.getClassPool() == null) {
            log("ERROR: No project loaded");
            return;
        }

        applyButton.setEnabled(false);
        logArea.setText("");
        mainFrame.setNavigatorLoading(true);

        new SwingWorker<Void, String>() {
            private int classesRenamed = 0;
            private int methodsRenamed = 0;
            private int fieldsRenamed = 0;
            private Set<String> renamedOldClassNames = new HashSet<>();
            private boolean success = false;

            @Override
            protected Void doInBackground() {
                try {
                    ClassPool classPool = project.getClassPool();
                    Renamer renamer = new Renamer(classPool);

                    Set<String> userClasses = new HashSet<>(project.getUserClassNames());
                    Set<String> processedMethods = new HashSet<>();
                    Set<String> processedFields = new HashSet<>();
                    Map<String, String> classNameMappings = new HashMap<>();

                    publish("Starting deobfuscation...");
                    publish("User classes: " + userClasses.size());

                    for (String className : userClasses) {
                        ClassFile cf = classPool.get(className);
                        if (cf == null) continue;

                        if (skipJdkBox.isSelected() && isLibraryClass(className)) {
                            continue;
                        }

                        if (renameClassesBox.isSelected()) {
                            String newClassName = generateClassName(className);
                            renamer.mapClass(className, newClassName);
                            classNameMappings.put(className, newClassName);
                            renamedOldClassNames.add(className);
                            publish("Class: " + className + " -> " + newClassName);
                            classesRenamed++;
                        }

                        if (renameMethodsBox.isSelected()) {
                            for (MethodEntry method : cf.getMethods()) {
                                String name = method.getName();
                                if (isSpecialMethod(name)) continue;

                                String key = className + "." + name + method.getDesc();
                                if (processedMethods.contains(key)) continue;
                                processedMethods.add(key);

                                String newName = "method" + (methodCounter++);
                                renamer.mapMethod(className, name, method.getDesc(), newName);
                                publish("  Method: " + name + " -> " + newName);
                                methodsRenamed++;
                            }
                        }

                        if (renameFieldsBox.isSelected()) {
                            for (FieldEntry field : cf.getFields()) {
                                String name = field.getName();

                                String key = className + "." + name + field.getDesc();
                                if (processedFields.contains(key)) continue;
                                processedFields.add(key);

                                String newName = "field" + (fieldCounter++);
                                renamer.mapField(className, name, field.getDesc(), newName);
                                publish("  Field: " + name + " -> " + newName);
                                fieldsRenamed++;
                            }
                        }
                    }

                    publish("");
                    publish("Applying " + renamer.getMappings().size() + " mappings...");

                    renamer.apply();

                    if (!classNameMappings.isEmpty()) {
                        project.applyClassNameMappings(classNameMappings);
                    }

                    publish("");
                    publish("=== Summary ===");
                    publish("Classes renamed: " + classesRenamed);
                    publish("Methods renamed: " + methodsRenamed);
                    publish("Fields renamed: " + fieldsRenamed);
                    publish("");
                    publish("Done!");

                    success = true;

                } catch (RenameException e) {
                    publish("ERROR: Rename failed - " + e.getMessage());
                } catch (Exception e) {
                    publish("ERROR: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                }
                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String msg : chunks) {
                    log(msg);
                }
            }

            @Override
            protected void done() {
                applyButton.setEnabled(true);
                if (success && mainFrame != null) {
                    int total = classesRenamed + methodsRenamed + fieldsRenamed;
                    mainFrame.refreshAfterBulkRename(renamedOldClassNames, total);
                } else if (mainFrame != null) {
                    mainFrame.setNavigatorLoading(false);
                }
            }
        }.execute();
    }

    private String generateClassName(String oldName) {
        int lastSlash = oldName.lastIndexOf('/');
        String pkg = lastSlash >= 0 ? oldName.substring(0, lastSlash + 1) : "";
        return pkg + "Class" + (classCounter++);
    }

    private boolean isSpecialMethod(String name) {
        return name.equals("<init>") ||
               name.equals("<clinit>") ||
               name.equals("main") ||
               name.equals("toString") ||
               name.equals("hashCode") ||
               name.equals("equals") ||
               name.equals("clone") ||
               name.equals("finalize");
    }

    private boolean isLibraryClass(String className) {
        return className.startsWith("java/") ||
               className.startsWith("javax/") ||
               className.startsWith("sun/") ||
               className.startsWith("com/sun/") ||
               className.startsWith("jdk/") ||
               className.startsWith("org/w3c/") ||
               className.startsWith("org/xml/") ||
               className.startsWith("org/ietf/");
    }
}
