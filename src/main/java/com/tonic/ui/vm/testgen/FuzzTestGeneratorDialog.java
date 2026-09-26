package com.tonic.ui.vm.testgen;

import com.tonic.parser.MethodEntry;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.vm.model.MethodCall;
import com.tonic.ui.vm.testgen.MethodFuzzer.FuzzConfig;
import com.tonic.ui.vm.testgen.MethodFuzzer.FuzzResult;
import com.tonic.ui.vm.testgen.objectspec.ObjectBuilderDialog;
import com.tonic.ui.vm.testgen.objectspec.ObjectSpec;
import com.tonic.ui.vm.testgen.objectspec.ParamSpec;
import com.tonic.ui.vm.testgen.objectspec.ValueMode;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

/** A modal dialog that fuzzes a method in the VM, instance methods on receivers built from a configurable constructor, groups the results by branch path, and generates a JUnit test class from the selected results. */
public class FuzzTestGeneratorDialog extends JDialog
{

    private final TestCaseGenerator generator = new TestCaseGenerator();

    private JSpinner iterationsSpinner;
    private JCheckBox edgeCasesCheckbox;
    private JCheckBox nullsCheckbox;
    private JCheckBox randomCheckbox;
    private JButton runFuzzButton;
    private JButton configParamsButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JLabel paramsConfigLabel;
    private JButton configReceiverButton;
    private JLabel receiverLabel;

    private FuzzResultTableModel tableModel;

    private JComboBox<TestCaseGenerator.JUnitVersion> versionCombo;
    private JTextField classNameField;
    private JTextArea previewArea;

    private JButton copyButton;
    private JButton saveButton;

    private String className;
    private String methodName;
    private String descriptor;
    private boolean staticMethod;
    private ParamSpec receiverSpec;

    private List<FuzzResult> fuzzResults = new ArrayList<>();
    private List<ParamSpec> paramSpecs = new ArrayList<>();

    /**
     * Creates the dialog; call setMethod before showing it.
     *
     * @param owner the window to center over and block
     */
    public FuzzTestGeneratorDialog(Window owner)
    {
        super(owner, "Fuzz & Generate Tests", ModalityType.APPLICATION_MODAL);
        initComponents();
        pack();
        setMinimumSize(new Dimension(800, 650));
        setLocationRelativeTo(owner);
    }

    private void initComponents()
    {
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel configPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        configPanel.setBorder(BorderFactory.createTitledBorder("Fuzz Configuration"));

        configPanel.add(new JLabel("Iterations per type:"));
        iterationsSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 50, 1));
        configPanel.add(iterationsSpinner);

        edgeCasesCheckbox = new JCheckBox("Edge cases", true);
        configPanel.add(edgeCasesCheckbox);

        nullsCheckbox = new JCheckBox("Nulls", true);
        configPanel.add(nullsCheckbox);

        randomCheckbox = new JCheckBox("Random", true);
        configPanel.add(randomCheckbox);

        configPanel.add(Box.createHorizontalStrut(10));

        configParamsButton = new JButton("Configure Parameters...");
        configParamsButton.setToolTipText("Configure how each parameter should be generated");
        configParamsButton.addActionListener(e -> openParamsConfig());
        configPanel.add(configParamsButton);

        paramsConfigLabel = new JLabel("");
        paramsConfigLabel.setForeground(JStudioTheme.getInfo());
        paramsConfigLabel.setFont(paramsConfigLabel.getFont().deriveFont(Font.ITALIC, 11f));
        configPanel.add(paramsConfigLabel);

        configReceiverButton = new JButton("Configure Receiver...");
        configReceiverButton.setToolTipText("Choose how the instance the method is called on is constructed");
        configReceiverButton.addActionListener(e -> openReceiverConfig());
        configPanel.add(configReceiverButton);

        receiverLabel = new JLabel("");
        receiverLabel.setForeground(JStudioTheme.getInfo());
        receiverLabel.setFont(receiverLabel.getFont().deriveFont(Font.ITALIC, 11f));
        configPanel.add(receiverLabel);

        configPanel.add(Box.createHorizontalStrut(10));

        runFuzzButton = new JButton("Run Fuzz");
        runFuzzButton.addActionListener(e -> runFuzz());
        configPanel.add(runFuzzButton);

        progressBar = new JProgressBar();
        progressBar.setPreferredSize(new Dimension(150, 20));
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        configPanel.add(progressBar);

        add(configPanel, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.4);

        JPanel resultsPanel = new JPanel(new BorderLayout());
        resultsPanel.setBorder(BorderFactory.createTitledBorder("Fuzz Results (select to include in tests)"));

        tableModel = new FuzzResultTableModel();
        JTable resultsTable = new JTable(tableModel);
        resultsTable.setRowHeight(22);
        resultsTable.getColumnModel().getColumn(0).setMaxWidth(40);
        resultsTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        resultsTable.getColumnModel().getColumn(2).setPreferredWidth(160);
        resultsTable.getColumnModel().getColumn(3).setPreferredWidth(180);
        resultsTable.getSelectionModel().addListSelectionListener(e -> updatePreview());

        resultsTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer()
        {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
            {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                FuzzResult result = tableModel.getResultAt(row);
                if (result != null && result.getResult().getException() != null)
                {
                    setForeground(JStudioTheme.getError());
                }
                else
                {
                    setForeground(isSelected ? JStudioTheme.getTextPrimary() : JStudioTheme.getSuccess());
                }
                return this;
            }
        });

        resultsTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer()
        {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
            {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setForeground(isSelected ? JStudioTheme.getTextPrimary() : JStudioTheme.getInfo());
                return this;
            }
        });

        JScrollPane tableScroll = new JScrollPane(resultsTable);
        tableScroll.setPreferredSize(new Dimension(750, 150));
        resultsPanel.add(tableScroll, BorderLayout.CENTER);

        JPanel tableButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton selectAllBtn = new JButton("Select All");
        selectAllBtn.addActionListener(e -> tableModel.setAllSelected(true));
        tableButtons.add(selectAllBtn);

        JButton selectNoneBtn = new JButton("Select None");
        selectNoneBtn.addActionListener(e -> tableModel.setAllSelected(false));
        tableButtons.add(selectNoneBtn);

        JButton selectDiverseBtn = new JButton("Select Diverse (1 per path)");
        selectDiverseBtn.addActionListener(e -> selectDiverse());
        tableButtons.add(selectDiverseBtn);

        statusLabel = new JLabel("Run fuzz to generate test inputs");
        tableButtons.add(Box.createHorizontalStrut(20));
        tableButtons.add(statusLabel);

        resultsPanel.add(tableButtons, BorderLayout.SOUTH);
        splitPane.setTopComponent(resultsPanel);

        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.setBorder(BorderFactory.createTitledBorder("Generated Test Preview"));

        JPanel previewConfig = new JPanel(new FlowLayout(FlowLayout.LEFT));
        previewConfig.add(new JLabel("JUnit Version:"));
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
        versionCombo.addActionListener(e -> updatePreview());
        previewConfig.add(versionCombo);

        previewConfig.add(Box.createHorizontalStrut(20));
        previewConfig.add(new JLabel("Test Class:"));
        classNameField = new JTextField(20);
        classNameField.getDocument().addDocumentListener(new DocumentListener()
        {
            public void insertUpdate(DocumentEvent e)
            {
                updatePreview();
            }

            public void removeUpdate(DocumentEvent e)
            {
                updatePreview();
            }

            public void changedUpdate(DocumentEvent e)
            {
                updatePreview();
            }
        });
        previewConfig.add(classNameField);

        previewPanel.add(previewConfig, BorderLayout.NORTH);

        previewArea = new JTextArea();
        previewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        previewArea.setEditable(false);
        previewArea.setTabSize(4);
        JScrollPane previewScroll = new JScrollPane(previewArea);
        previewScroll.setPreferredSize(new Dimension(750, 200));
        previewPanel.add(previewScroll, BorderLayout.CENTER);

        splitPane.setBottomComponent(previewPanel);
        add(splitPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        copyButton = new JButton("Copy to Clipboard");
        copyButton.setEnabled(false);
        copyButton.addActionListener(e -> copyToClipboard());
        buttonPanel.add(copyButton);

        saveButton = new JButton("Save to File...");
        saveButton.setEnabled(false);
        saveButton.addActionListener(e -> saveToFile());
        buttonPanel.add(saveButton);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(cancelButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    /**
     * Sets the method to fuzz, suggests a test class name, resets the parameter specs to fuzz every parameter, and for an instance method picks a receiver constructor.
     *
     * @param method the method to fuzz
     */
    public void setMethod(MethodEntry method)
    {
        this.className = method.getOwnerName();
        this.methodName = method.getName();
        this.descriptor = method.getDesc();
        this.staticMethod = (method.getAccess() & 0x0008) != 0;
        this.receiverSpec = staticMethod || method.getClassFile() == null ? null : MethodFuzzer.defaultReceiverSpec(method.getClassFile());

        String simpleClass = className.replace('/', '.');
        int lastDot = simpleClass.lastIndexOf('.');
        String simpleName = lastDot >= 0 ? simpleClass.substring(lastDot + 1) : simpleClass;
        classNameField.setText(simpleName + "Test");

        setTitle("Fuzz & Generate Tests - " + simpleName + "." + methodName);

        MethodFuzzer tempFuzzer = new MethodFuzzer(className, methodName, descriptor, staticMethod, null);
        paramSpecs = tempFuzzer.getDefaultParamSpecs();
        updateParamsConfigLabel();
        updateReceiverLabel();
    }

    private void updateReceiverLabel()
    {
        configReceiverButton.setVisible(!staticMethod);
        receiverLabel.setVisible(!staticMethod);
        if (!staticMethod)
        {
            receiverLabel.setText(receiverSpec != null && receiverSpec.getNestedObjectSpec() != null ? "this = " + receiverSpec.getNestedObjectSpec().getSummary() : "no receiver configured");
        }
    }

    private void openReceiverConfig()
    {
        ObjectSpec existing = receiverSpec != null ? receiverSpec.getNestedObjectSpec() : null;
        ObjectSpec result = ObjectBuilderDialog.showDialog(this, className, existing);
        if (result != null)
        {
            receiverSpec = ParamSpec.object("this", "L" + className + ";", result);
            updateReceiverLabel();
        }
    }

    private void updateParamsConfigLabel()
    {
        if (paramSpecs.isEmpty())
        {
            paramsConfigLabel.setText("(no parameters)");
            configParamsButton.setEnabled(false);
        }
        else
        {
            int configuredCount = 0;
            for (ParamSpec spec : paramSpecs)
            {
                if (spec.getMode() == ValueMode.OBJECT_SPEC ||
                        spec.getMode() == ValueMode.FIXED)
                {
                    configuredCount++;
                }
            }
            if (configuredCount > 0)
            {
                paramsConfigLabel.setText(configuredCount + "/" + paramSpecs.size() + " configured");
            }
            else
            {
                paramsConfigLabel.setText(paramSpecs.size() + " params (auto)");
            }
            configParamsButton.setEnabled(true);
        }
    }

    private void openParamsConfig()
    {
        if (paramSpecs.isEmpty()) return;

        ParameterConfigDialog dialog = new ParameterConfigDialog(this, paramSpecs);
        dialog.setVisible(true);

        List<ParamSpec> result = dialog.getResult();
        if (result != null)
        {
            paramSpecs = result;
            updateParamsConfigLabel();
        }
    }

    private void runFuzz()
    {
        if (className == null || methodName == null || descriptor == null)
        {
            JOptionPane.showMessageDialog(this, "No method configured", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        FuzzConfig config = new FuzzConfig();
        config.setIterationsPerType((Integer) iterationsSpinner.getValue());
        config.setIncludeEdgeCases(edgeCasesCheckbox.isSelected());
        config.setIncludeNulls(nullsCheckbox.isSelected());
        config.setIncludeRandom(randomCheckbox.isSelected());

        if (!staticMethod && receiverSpec == null)
        {
            JOptionPane.showMessageDialog(this, "Configure how to construct the receiver first", "No Receiver", JOptionPane.WARNING_MESSAGE);
            return;
        }

        MethodFuzzer fuzzer = new MethodFuzzer(className, methodName, descriptor, staticMethod, config);
        if (!paramSpecs.isEmpty())
        {
            fuzzer.setParameterSpecs(paramSpecs);
        }
        fuzzer.setReceiverSpec(receiverSpec);

        runFuzzButton.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setValue(0);
        statusLabel.setText("Fuzzing...");

        SwingWorker<List<FuzzResult>, Integer> worker = new SwingWorker<>()
        {
            @Override
            protected List<FuzzResult> doInBackground()
            {
                return fuzzer.runFuzz(new MethodFuzzer.ProgressCallback()
                {
                    @Override
                    public void onProgress(int current, int total, String message)
                    {
                        int percent = (int) ((current / (double) total) * 100);
                        publish(percent);
                    }

                    @Override
                    public void onComplete(int totalResults)
                    {
                        publish(100);
                    }
                });
            }

            @Override
            protected void process(List<Integer> chunks)
            {
                if (!chunks.isEmpty())
                {
                    progressBar.setValue(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done()
            {
                try
                {
                    fuzzResults = get();
                    tableModel.setResults(fuzzResults);

                    int uniquePaths = fuzzer.countUniqueBranchPaths(fuzzResults);
                    Map<String, List<FuzzResult>> grouped = fuzzer.groupByOutcome(fuzzResults);
                    statusLabel.setText(fuzzResults.size() + " executions, " + uniquePaths + " unique paths, " + grouped.size() + " outcomes");

                    selectDiverse();
                    updatePreview();

                }
                catch (Exception e)
                {
                    statusLabel.setText("Fuzz failed: " + e.getMessage());
                }
                finally
                {
                    runFuzzButton.setEnabled(true);
                    progressBar.setVisible(false);
                }
            }
        };

        worker.execute();
    }

    private void selectDiverse()
    {
        if (fuzzResults.isEmpty()) return;

        tableModel.setAllSelected(false);

        Set<String> seenOutcomes = new HashSet<>();
        for (int i = 0; i < fuzzResults.size(); i++)
        {
            FuzzResult r = fuzzResults.get(i);
            if (seenOutcomes.add(r.getOutcomeKey()))
            {
                tableModel.setSelected(i, true);
            }
        }

        updatePreview();
    }

    private void updatePreview()
    {
        List<FuzzResult> selected = tableModel.getSelectedResults();
        if (selected.isEmpty())
        {
            previewArea.setText("// Select results to generate tests");
            copyButton.setEnabled(false);
            saveButton.setEnabled(false);
            return;
        }

        String testClassName = classNameField.getText().trim();
        if (testClassName.isEmpty()) testClassName = "GeneratedTest";

        TestCaseGenerator.JUnitVersion version = (TestCaseGenerator.JUnitVersion) versionCombo.getSelectedItem();

        try
        {
            List<TestCaseGenerator.TestCase> cases = new ArrayList<>();
            for (FuzzResult result : selected)
            {
                cases.add(TestCaseGenerator.TestCase.fromResult(result.getResult(), className, methodName, descriptor, staticMethod, result.getReceiver(), result.getInputs()));
            }
            String code = generator.generate(cases, version, testClassName, generator.suggestTestMethodName(methodName)).getCode();
            previewArea.setText(code);
            previewArea.setCaretPosition(0);
            copyButton.setEnabled(true);
            saveButton.setEnabled(true);
        }
        catch (IllegalArgumentException e)
        {
            previewArea.setText("// Cannot generate tests: " + e.getMessage());
            copyButton.setEnabled(false);
            saveButton.setEnabled(false);
        }
    }

    private void copyToClipboard()
    {
        String code = previewArea.getText();
        if (!code.isEmpty())
        {
            StringSelection selection = new StringSelection(code);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            JOptionPane.showMessageDialog(this, "Test code copied to clipboard!", "Copied", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void saveToFile()
    {
        String code = previewArea.getText();
        if (code.isEmpty()) return;

        String suggestedName = classNameField.getText().trim();
        if (suggestedName.isEmpty()) suggestedName = "GeneratedTest";

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(suggestedName + ".java"));
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
                if (result != JOptionPane.YES_OPTION) return;
            }

            try
            {
                Files.writeString(file.toPath(), code, StandardCharsets.UTF_8);
                JOptionPane.showMessageDialog(this, "Test saved to: " + file.getAbsolutePath(), "Saved", JOptionPane.INFORMATION_MESSAGE);
            }
            catch (IOException e)
            {
                JOptionPane.showMessageDialog(this, "Failed to save: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private class FuzzResultTableModel extends AbstractTableModel
    {
        private List<FuzzResult> results = new ArrayList<>();
        private List<Boolean> selected = new ArrayList<>();

        public void setResults(List<FuzzResult> results)
        {
            this.results = new ArrayList<>(results);
            this.selected = new ArrayList<>();
            for (int i = 0; i < results.size(); i++)
            {
                selected.add(false);
            }
            fireTableDataChanged();
        }

        public void setAllSelected(boolean value)
        {
            Collections.fill(selected, value);
            fireTableDataChanged();
            updatePreview();
        }

        public void setSelected(int row, boolean value)
        {
            if (row >= 0 && row < selected.size())
            {
                selected.set(row, value);
                fireTableRowsUpdated(row, row);
            }
        }

        public List<FuzzResult> getSelectedResults()
        {
            List<FuzzResult> sel = new ArrayList<>();
            for (int i = 0; i < results.size(); i++)
            {
                if (selected.get(i))
                {
                    sel.add(results.get(i));
                }
            }
            return sel;
        }

        public FuzzResult getResultAt(int row)
        {
            if (row >= 0 && row < results.size())
            {
                return results.get(row);
            }
            return null;
        }

        @Override
        public int getRowCount()
        {
            return results.size();
        }

        @Override
        public int getColumnCount()
        {
            return 4;
        }

        @Override
        public String getColumnName(int column)
        {
            switch (column)
            {
                case 1:
                    return "Inputs";
                case 2:
                    return "Outcome";
                case 3:
                    return "Branch Path";
                default:
                    return "";
            }
        }

        @Override
        public Class<?> getColumnClass(int column)
        {
            if (column == 0) return Boolean.class;
            return String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column)
        {
            return column == 0;
        }

        @Override
        public Object getValueAt(int row, int column)
        {
            FuzzResult r = results.get(row);
            switch (column)
            {
                case 0:
                    return selected.get(row);
                case 1:
                    return formatInputs(r.getReceiver(), r.getInputs());
                case 2:
                    return r.getOutcomeDescription();
                case 3:
                    return r.getBranchSummary();
                default:
                    return "";
            }
        }

        @Override
        public void setValueAt(Object value, int row, int column)
        {
            if (column == 0)
            {
                selected.set(row, (Boolean) value);
                updatePreview();
            }
        }

        private String formatInputs(Object receiver, Object[] inputs)
        {
            StringBuilder sb = new StringBuilder();
            if (receiver != null)
            {
                sb.append(receiver).append(": ");
            }
            sb.append("(");
            for (int i = 0; i < inputs.length; i++)
            {
                if (i > 0) sb.append(", ");
                sb.append(formatArg(inputs[i]));
            }
            sb.append(")");
            return sb.toString();
        }

        private String formatArg(Object arg)
        {
            if (arg == null) return "null";
            if (arg instanceof String)
            {
                String s = (String) arg;
                if (s.length() > 20) return "\"" + s.substring(0, 17) + "...\"";
                return "\"" + s + "\"";
            }
            if (arg instanceof Character) return "'" + arg + "'";
            if (arg.getClass().isArray()) return MethodCall.formatValue(arg);
            return String.valueOf(arg);
        }
    }
}
