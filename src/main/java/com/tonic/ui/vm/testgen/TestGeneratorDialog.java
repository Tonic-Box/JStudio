package com.tonic.ui.vm.testgen;

import com.tonic.ui.vm.model.ExecutionResult;
import com.tonic.ui.vm.model.MethodCall;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class TestGeneratorDialog extends JDialog {

    private final TestCaseGenerator generator = new TestCaseGenerator();

    private JComboBox<TestCaseGenerator.JUnitVersion> versionCombo;
    private JTextField classNameField;
    private JTextField methodNameField;
    private JTextArea previewArea;

    private MethodCall methodCall;
    private ExecutionResult executionResult;
    private String entryClass;
    private String entryMethod;
    private String entryDescriptor;
    private Object[] entryArgs;

    private TestCaseGenerator.GeneratedTest currentTest;

    public TestGeneratorDialog(Window owner) {
        super(owner, "Generate JUnit Test", ModalityType.APPLICATION_MODAL);
        initComponents();
        pack();
        setMinimumSize(new Dimension(600, 500));
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        topPanel.add(new JLabel("JUnit Version:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        versionCombo = new JComboBox<>(TestCaseGenerator.JUnitVersion.values());
        versionCombo.setSelectedItem(TestCaseGenerator.JUnitVersion.JUNIT5);
        versionCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof TestCaseGenerator.JUnitVersion) {
                    setText(((TestCaseGenerator.JUnitVersion) value).getDisplayName());
                }
                return this;
            }
        });
        versionCombo.addActionListener(e -> regeneratePreview());
        topPanel.add(versionCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        topPanel.add(new JLabel("Test Class:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        classNameField = new JTextField(30);
        classNameField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { regeneratePreview(); }
            public void removeUpdate(DocumentEvent e) { regeneratePreview(); }
            public void changedUpdate(DocumentEvent e) { regeneratePreview(); }
        });
        topPanel.add(classNameField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        topPanel.add(new JLabel("Test Method:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        methodNameField = new JTextField(30);
        methodNameField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { regeneratePreview(); }
            public void removeUpdate(DocumentEvent e) { regeneratePreview(); }
            public void changedUpdate(DocumentEvent e) { regeneratePreview(); }
        });
        topPanel.add(methodNameField, gbc);

        add(topPanel, BorderLayout.NORTH);

        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.setBorder(BorderFactory.createTitledBorder("Preview"));
        previewArea = new JTextArea();
        previewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        previewArea.setEditable(false);
        previewArea.setTabSize(4);
        JScrollPane scrollPane = new JScrollPane(previewArea);
        scrollPane.setPreferredSize(new Dimension(550, 300));
        previewPanel.add(scrollPane, BorderLayout.CENTER);
        add(previewPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton copyButton = new JButton("Copy to Clipboard");
        copyButton.addActionListener(e -> copyToClipboard());
        buttonPanel.add(copyButton);

        JButton saveButton = new JButton("Save to File...");
        saveButton.addActionListener(e -> saveToFile());
        buttonPanel.add(saveButton);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(cancelButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    public void setMethodCall(MethodCall call) {
        this.methodCall = call;
        this.executionResult = null;
        this.entryClass = null;
        this.entryMethod = null;
        this.entryDescriptor = null;
        this.entryArgs = null;

        String targetClass = call.getOwnerClass().replace('/', '.');
        classNameField.setText(generator.suggestTestClassName(targetClass));
        methodNameField.setText(generator.suggestTestMethodName(call.getMethodName()));

        regeneratePreview();
    }

    public void setExecutionResult(ExecutionResult result, String className,
                                    String methodName, String descriptor, Object[] args) {
        this.methodCall = null;
        this.executionResult = result;
        this.entryClass = className;
        this.entryMethod = methodName;
        this.entryDescriptor = descriptor;
        this.entryArgs = args;

        String targetClass = className.replace('/', '.');
        classNameField.setText(generator.suggestTestClassName(targetClass));
        methodNameField.setText(generator.suggestTestMethodName(methodName));

        regeneratePreview();
    }

    private void regeneratePreview() {
        String testClassName = classNameField.getText().trim();
        String testMethodName = methodNameField.getText().trim();
        TestCaseGenerator.JUnitVersion version =
                (TestCaseGenerator.JUnitVersion) versionCombo.getSelectedItem();

        if (testClassName.isEmpty()) testClassName = "GeneratedTest";
        if (testMethodName.isEmpty()) testMethodName = "testMethod";

        try {
            if (methodCall != null) {
                currentTest = generator.generate(methodCall, version, testClassName, testMethodName);
            } else if (executionResult != null) {
                currentTest = generator.generate(executionResult, entryClass, entryMethod,
                                                  entryDescriptor, entryArgs, version,
                                                  testClassName, testMethodName);
            } else {
                previewArea.setText("// No execution data available");
                return;
            }

            previewArea.setText(currentTest.getCode());
            previewArea.setCaretPosition(0);
        } catch (Exception e) {
            previewArea.setText("// Error generating test: " + e.getMessage());
        }
    }

    private void copyToClipboard() {
        if (currentTest != null) {
            StringSelection selection = new StringSelection(currentTest.getCode());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            JOptionPane.showMessageDialog(this,
                    "Test code copied to clipboard!",
                    "Copied",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void saveToFile() {
        if (currentTest == null) return;

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(currentTest.getSuggestedFileName()));
        chooser.setDialogTitle("Save Test File");

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().endsWith(".java")) {
                file = new File(file.getAbsolutePath() + ".java");
            }

            if (file.exists()) {
                int result = JOptionPane.showConfirmDialog(this,
                        "File already exists. Overwrite?",
                        "Confirm Overwrite",
                        JOptionPane.YES_NO_OPTION);
                if (result != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            try (FileWriter writer = new FileWriter(file)) {
                writer.write(currentTest.getCode());
                JOptionPane.showMessageDialog(this,
                        "Test saved to: " + file.getAbsolutePath(),
                        "Saved",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                        "Failed to save file: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
