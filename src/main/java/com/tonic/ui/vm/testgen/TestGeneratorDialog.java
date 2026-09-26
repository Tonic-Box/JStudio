package com.tonic.ui.vm.testgen;

import com.tonic.ui.vm.model.ExecutionResult;
import com.tonic.ui.vm.model.MethodCall;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** A modal dialog that previews a generated JUnit test for one recorded call or execution and lets the user copy or save it. */
public class TestGeneratorDialog extends JDialog
{

    private final TestCaseGenerator generator = new TestCaseGenerator();

    private JComboBox<TestCaseGenerator.JUnitVersion> versionCombo;
    private JTextField classNameField;
    private JTextField methodNameField;
    private JTextArea previewArea;

    private TestCaseGenerator.TestCase testCase;
    private String unavailableReason = "No execution data available";

    private TestCaseGenerator.GeneratedTest currentTest;

    /**
     * Creates the dialog; call setMethodCall or setExecutionResult before showing it.
     *
     * @param owner the window to center over and block
     */
    public TestGeneratorDialog(Window owner)
    {
        super(owner, "Generate JUnit Test", ModalityType.APPLICATION_MODAL);
        initComponents();
        pack();
        setMinimumSize(new Dimension(600, 500));
        setLocationRelativeTo(owner);
    }

    private void initComponents()
    {
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        topPanel.add(new JLabel("JUnit Version:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        versionCombo = new JComboBox<>(TestCaseGenerator.JUnitVersion.values());
        versionCombo.setSelectedItem(TestCaseGenerator.JUnitVersion.JUNIT5);
        versionCombo.setRenderer(new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof TestCaseGenerator.JUnitVersion)
                {
                    setText(((TestCaseGenerator.JUnitVersion) value).getDisplayName());
                }
                return this;
            }
        });
        versionCombo.addActionListener(e -> regeneratePreview());
        topPanel.add(versionCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        topPanel.add(new JLabel("Test Class:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        classNameField = new JTextField(30);
        classNameField.getDocument().addDocumentListener(new DocumentListener()
        {
            public void insertUpdate(DocumentEvent e)
            {
                regeneratePreview();
            }

            public void removeUpdate(DocumentEvent e)
            {
                regeneratePreview();
            }

            public void changedUpdate(DocumentEvent e)
            {
                regeneratePreview();
            }
        });
        topPanel.add(classNameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        topPanel.add(new JLabel("Test Method:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        methodNameField = new JTextField(30);
        methodNameField.getDocument().addDocumentListener(new DocumentListener()
        {
            public void insertUpdate(DocumentEvent e)
            {
                regeneratePreview();
            }

            public void removeUpdate(DocumentEvent e)
            {
                regeneratePreview();
            }

            public void changedUpdate(DocumentEvent e)
            {
                regeneratePreview();
            }
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

    /**
     * Sets a recorded method call as the test source, suggests names, and regenerates the preview.
     *
     * @param call the recorded call
     */
    public void setMethodCall(MethodCall call)
    {
        classNameField.setText(generator.suggestTestClassName(call.getOwnerClass()));
        methodNameField.setText(generator.suggestTestMethodName(call.getMethodName()));
        try
        {
            setTestCase(TestCaseGenerator.TestCase.fromCall(call));
        }
        catch (IllegalArgumentException e)
        {
            setUnavailable(e.getMessage());
        }
    }

    /**
     * Sets a static method's VM execution as the test source, suggests names, and regenerates the preview.
     *
     * @param result the execution result
     * @param className the class's internal name, with slashes
     * @param methodName the method's name
     * @param descriptor the method's descriptor
     * @param args the arguments the method was called with
     */
    public void setExecutionResult(ExecutionResult result, String className, String methodName, String descriptor, Object[] args)
    {
        classNameField.setText(generator.suggestTestClassName(className));
        methodNameField.setText(generator.suggestTestMethodName(methodName));
        try
        {
            setTestCase(TestCaseGenerator.TestCase.fromResult(result, className, methodName, descriptor, true, null, args));
        }
        catch (IllegalArgumentException e)
        {
            setUnavailable(e.getMessage());
        }
    }

    private void setTestCase(TestCaseGenerator.TestCase testCase)
    {
        this.testCase = testCase;
        this.unavailableReason = null;
        regeneratePreview();
    }

    private void setUnavailable(String reason)
    {
        this.testCase = null;
        this.unavailableReason = reason;
        regeneratePreview();
    }

    private void regeneratePreview()
    {
        currentTest = null;
        if (testCase == null)
        {
            previewArea.setText("// " + unavailableReason);
            return;
        }

        String testClassName = classNameField.getText().trim();
        String testMethodName = methodNameField.getText().trim();
        TestCaseGenerator.JUnitVersion version = (TestCaseGenerator.JUnitVersion) versionCombo.getSelectedItem();
        if (version == null)
        {
            version = TestCaseGenerator.JUnitVersion.JUNIT5;
        }
        if (testClassName.isEmpty())
        {
            testClassName = "GeneratedTest";
        }
        if (testMethodName.isEmpty())
        {
            testMethodName = "testMethod";
        }

        try
        {
            currentTest = generator.generate(List.of(testCase), version, testClassName, testMethodName);
            previewArea.setText(currentTest.getCode());
            previewArea.setCaretPosition(0);
        }
        catch (IllegalArgumentException e)
        {
            previewArea.setText("// Cannot generate a test: " + e.getMessage());
        }
    }

    private void copyToClipboard()
    {
        if (currentTest != null)
        {
            StringSelection selection = new StringSelection(currentTest.getCode());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            JOptionPane.showMessageDialog(this, "Test code copied to clipboard!", "Copied", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void saveToFile()
    {
        if (currentTest == null) return;

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(currentTest.getSuggestedFileName()));
        chooser.setDialogTitle("Save Test File");

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
        {
            File file = chooser.getSelectedFile();
            if (!file.getName().endsWith(".java"))
            {
                file = new File(file.getAbsolutePath() + ".java");
            }

            if (file.exists())
            {
                int result = JOptionPane.showConfirmDialog(this, "File already exists. Overwrite?", "Confirm Overwrite", JOptionPane.YES_NO_OPTION);
                if (result != JOptionPane.YES_OPTION)
                {
                    return;
                }
            }

            try
            {
                Files.writeString(file.toPath(), currentTest.getCode(), StandardCharsets.UTF_8);
                JOptionPane.showMessageDialog(this, "Test saved to: " + file.getAbsolutePath(), "Saved", JOptionPane.INFORMATION_MESSAGE);
            }
            catch (IOException e)
            {
                JOptionPane.showMessageDialog(this, "Failed to save file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
