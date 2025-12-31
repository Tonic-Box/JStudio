package com.tonic.ui.deobfuscation;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.ui.deobfuscation.detection.DecryptorDetector;
import com.tonic.ui.deobfuscation.detection.EncryptedStringDetector;
import com.tonic.ui.deobfuscation.model.DecryptorCandidate;
import com.tonic.ui.deobfuscation.model.DeobfuscationResult;
import com.tonic.ui.deobfuscation.model.SuspiciousString;
import com.tonic.ui.deobfuscation.patch.ConstantPoolPatcher;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class DeobfuscationPanel extends ThemedJPanel {

    private final EncryptedStringDetector stringDetector;
    private final DecryptorDetector decryptorDetector;
    private final DeobfuscationService deobfuscationService;
    private final ConstantPoolPatcher patcher;

    private ProjectModel projectModel;
    private JComboBox<ClassFile> classSelector;
    private JComboBox<DecryptorCandidate> decryptorSelector;
    private JCheckBox autoDetectCheckbox;
    private JTable resultsTable;
    private ResultsTableModel tableModel;
    private JButton scanButton;
    private JButton decryptSelectedButton;
    private JButton decryptAllButton;
    private JButton applyButton;
    private JLabel statusLabel;
    private JTextArea previewArea;

    private List<SuspiciousString> suspiciousStrings = new ArrayList<>();
    private List<DecryptorCandidate> decryptorCandidates = new ArrayList<>();
    private final List<DeobfuscationResult> results = new ArrayList<>();

    public DeobfuscationPanel(ProjectModel project) {
        super(BackgroundStyle.PRIMARY);
        this.projectModel = project;
        this.stringDetector = new EncryptedStringDetector();
        this.decryptorDetector = new DecryptorDetector();
        this.deobfuscationService = DeobfuscationService.getInstance();
        this.patcher = new ConstantPoolPatcher();

        initializeUI();
        refreshClassList();
    }

    public void setProject(ProjectModel project) {
        this.projectModel = project;
        refreshClassList();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL));
        setBorder(BorderFactory.createEmptyBorder(UIConstants.SPACING_MEDIUM, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_MEDIUM));

        add(createToolbar(), BorderLayout.NORTH);
        add(createMainContent(), BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_SMALL));
        toolbar.setBackground(JStudioTheme.getBgSecondary());
        toolbar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));

        scanButton = createButton("Scan", JStudioTheme.getAccent());
        scanButton.addActionListener(e -> performScan());

        decryptSelectedButton = createButton("Decrypt Selected", JStudioTheme.getBgTertiary());
        decryptSelectedButton.addActionListener(e -> decryptSelected());
        decryptSelectedButton.setEnabled(false);

        decryptAllButton = createButton("Decrypt All", JStudioTheme.getBgTertiary());
        decryptAllButton.addActionListener(e -> decryptAll());
        decryptAllButton.setEnabled(false);

        applyButton = createButton("Apply Changes", JStudioTheme.getSuccess());
        applyButton.addActionListener(e -> applyChanges());
        applyButton.setEnabled(false);

        toolbar.add(scanButton);
        toolbar.add(decryptSelectedButton);
        toolbar.add(decryptAllButton);
        toolbar.add(Box.createHorizontalStrut(20));
        toolbar.add(applyButton);

        return toolbar;
    }

    private JButton createButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setBackground(bgColor);
        button.setForeground(JStudioTheme.getTextPrimary());
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(bgColor.darker()),
            BorderFactory.createEmptyBorder(5, 12, 5, 12)
        ));
        return button;
    }

    private JSplitPane createMainContent() {
        JPanel leftPanel = createConfigPanel();
        JPanel rightPanel = createResultsPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(350);
        splitPane.setResizeWeight(0.3);
        splitPane.setBackground(JStudioTheme.getBgPrimary());

        return splitPane;
    }

    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(JStudioTheme.getBgPrimary());

        JPanel selectionPanel = new JPanel(new GridBagLayout());
        selectionPanel.setBackground(JStudioTheme.getBgPrimary());
        selectionPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            "Configuration",
            TitledBorder.LEFT, TitledBorder.TOP, null, JStudioTheme.getTextPrimary()
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        JLabel classLabel = new JLabel("Target Class:");
        classLabel.setForeground(JStudioTheme.getTextPrimary());
        selectionPanel.add(classLabel, gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        classSelector = new JComboBox<>();
        classSelector.setBackground(JStudioTheme.getBgSecondary());
        classSelector.setForeground(JStudioTheme.getTextPrimary());
        classSelector.addActionListener(e -> onClassSelected());
        selectionPanel.add(classSelector, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        autoDetectCheckbox = new JCheckBox("Auto-detect decryptor");
        autoDetectCheckbox.setSelected(true);
        autoDetectCheckbox.setBackground(JStudioTheme.getBgPrimary());
        autoDetectCheckbox.setForeground(JStudioTheme.getTextPrimary());
        autoDetectCheckbox.addActionListener(e -> decryptorSelector.setEnabled(!autoDetectCheckbox.isSelected()));
        selectionPanel.add(autoDetectCheckbox, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        JLabel decryptorLabel = new JLabel("Decryptor:");
        decryptorLabel.setForeground(JStudioTheme.getTextPrimary());
        selectionPanel.add(decryptorLabel, gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        decryptorSelector = new JComboBox<>();
        decryptorSelector.setBackground(JStudioTheme.getBgSecondary());
        decryptorSelector.setForeground(JStudioTheme.getTextPrimary());
        decryptorSelector.setEnabled(false);
        selectionPanel.add(decryptorSelector, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.weighty = 1;
        selectionPanel.add(Box.createVerticalGlue(), gbc);

        panel.add(selectionPanel, BorderLayout.NORTH);

        previewArea = new JTextArea();
        previewArea.setEditable(false);
        previewArea.setBackground(JStudioTheme.getBgSecondary());
        previewArea.setForeground(JStudioTheme.getTextPrimary());
        previewArea.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_NORMAL));
        previewArea.setBorder(BorderFactory.createEmptyBorder(UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL));

        JScrollPane previewScroll = new JScrollPane(previewArea);
        previewScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            "Preview",
            TitledBorder.LEFT, TitledBorder.TOP, null, JStudioTheme.getTextPrimary()
        ));

        panel.add(previewScroll, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(JStudioTheme.getBgPrimary());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            "Results",
            TitledBorder.LEFT, TitledBorder.TOP, null, JStudioTheme.getTextPrimary()
        ));

        tableModel = new ResultsTableModel();
        resultsTable = new JTable(tableModel);
        resultsTable.setBackground(JStudioTheme.getBgSecondary());
        resultsTable.setForeground(JStudioTheme.getTextPrimary());
        resultsTable.setGridColor(JStudioTheme.getBorder());
        resultsTable.setSelectionBackground(JStudioTheme.getAccent());
        resultsTable.setSelectionForeground(JStudioTheme.getTextPrimary());
        resultsTable.setRowHeight(UIConstants.TABLE_ROW_HEIGHT + 4);
        resultsTable.getTableHeader().setBackground(JStudioTheme.getBgTertiary());
        resultsTable.getTableHeader().setForeground(JStudioTheme.getTextPrimary());

        resultsTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        resultsTable.getColumnModel().getColumn(0).setMaxWidth(40);
        resultsTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        resultsTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        resultsTable.getColumnModel().getColumn(3).setPreferredWidth(150);
        resultsTable.getColumnModel().getColumn(4).setPreferredWidth(80);

        resultsTable.getColumnModel().getColumn(4).setCellRenderer(new StatusCellRenderer());

        resultsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updatePreview();
                updateButtonStates();
            }
        });

        JScrollPane scrollPane = new JScrollPane(resultsTable);
        scrollPane.setBackground(JStudioTheme.getBgPrimary());
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(JStudioTheme.getBgSecondary());
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));

        statusLabel = new JLabel("Ready");
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        statusBar.add(statusLabel, BorderLayout.WEST);

        return statusBar;
    }

    public void refreshClassList() {
        classSelector.removeAllItems();

        if (projectModel == null || projectModel.getClassPool() == null) {
            return;
        }

        ClassPool pool = projectModel.getClassPool();
        for (ClassFile cf : pool.getClasses()) {
            classSelector.addItem(cf);
        }
    }

    private void onClassSelected() {
        ClassFile selected = (ClassFile) classSelector.getSelectedItem();
        if (selected == null) return;

        decryptorSelector.removeAllItems();
        decryptorCandidates = decryptorDetector.scan(selected);

        for (DecryptorCandidate candidate : decryptorCandidates) {
            decryptorSelector.addItem(candidate);
        }

        statusLabel.setText("Found " + decryptorCandidates.size() + " potential decryptors");
    }

    private void performScan() {
        ClassFile selected = (ClassFile) classSelector.getSelectedItem();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Please select a class first",
                "No Class Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        scanButton.setEnabled(false);
        statusLabel.setText("Scanning...");

        SwingWorker<List<SuspiciousString>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<SuspiciousString> doInBackground() {
                return stringDetector.scan(selected);
            }

            @Override
            protected void done() {
                try {
                    suspiciousStrings = get();
                    results.clear();

                    for (SuspiciousString ss : suspiciousStrings) {
                        results.add(new DeobfuscationResult(
                            ss.getClassName(), ss.getConstantPoolIndex(), ss.getValue()));
                    }

                    tableModel.fireTableDataChanged();
                    statusLabel.setText("Found " + suspiciousStrings.size() + " suspicious strings");
                    updateButtonStates();

                } catch (InterruptedException | ExecutionException e) {
                    statusLabel.setText("Scan failed: " + e.getMessage());
                } finally {
                    scanButton.setEnabled(true);
                }
            }
        };

        worker.execute();
    }

    private void decryptSelected() {
        int[] selectedRows = resultsTable.getSelectedRows();
        if (selectedRows.length == 0) return;

        DecryptorCandidate decryptor = getSelectedDecryptor();
        if (decryptor == null) {
            JOptionPane.showMessageDialog(this, "No decryptor available",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        decryptRows(selectedRows, decryptor);
    }

    private void decryptAll() {
        if (results.isEmpty()) return;

        DecryptorCandidate decryptor = getSelectedDecryptor();
        if (decryptor == null) {
            JOptionPane.showMessageDialog(this, "No decryptor available",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int[] allRows = new int[results.size()];
        for (int i = 0; i < results.size(); i++) {
            allRows[i] = i;
        }

        decryptRows(allRows, decryptor);
    }

    private void decryptRows(int[] rows, DecryptorCandidate decryptor) {
        decryptSelectedButton.setEnabled(false);
        decryptAllButton.setEnabled(false);
        statusLabel.setText("Decrypting...");

        SwingWorker<Void, Integer> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                deobfuscationService.initialize();

                for (int row : rows) {
                    DeobfuscationResult result = results.get(row);
                    if (result.isSuccess()) continue;

                    DeobfuscationResult newResult = deobfuscationService.decryptString(
                        result.getClassName(),
                        result.getConstantPoolIndex(),
                        result.getOriginalValue(),
                        decryptor
                    );

                    result.setSuccess(newResult.isSuccess());
                    result.setDecryptedValue(newResult.getDecryptedValue());
                    result.setDecryptorUsed(newResult.getDecryptorUsed());
                    result.setErrorMessage(newResult.getErrorMessage());
                    result.setExecutionTimeMs(newResult.getExecutionTimeMs());

                    publish(row);
                }
                return null;
            }

            @Override
            protected void process(List<Integer> chunks) {
                for (int row : chunks) {
                    tableModel.fireTableRowsUpdated(row, row);
                }
            }

            @Override
            protected void done() {
                long successCount = results.stream().filter(DeobfuscationResult::isSuccess).count();
                statusLabel.setText("Decrypted " + successCount + "/" + results.size() + " strings");
                updateButtonStates();
            }
        };

        worker.execute();
    }

    private void applyChanges() {
        ClassFile selected = (ClassFile) classSelector.getSelectedItem();
        if (selected == null) return;

        List<DeobfuscationResult> toApply = new ArrayList<>();
        for (DeobfuscationResult result : results) {
            if (result.isSuccess() && !result.isApplied()) {
                toApply.add(result);
            }
        }

        if (toApply.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No successful decryptions to apply",
                "Nothing to Apply", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
            "Apply " + toApply.size() + " decrypted strings to bytecode?\n" +
            "This will modify the class file.",
            "Confirm Apply", JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) return;

        int applied = patcher.applyResults(selected, toApply);
        tableModel.fireTableDataChanged();

        statusLabel.setText("Applied " + applied + " changes to bytecode");
        updateButtonStates();

        JOptionPane.showMessageDialog(this,
            "Applied " + applied + " changes.\n" +
            "Use File > Export to save the modified class.",
            "Changes Applied", JOptionPane.INFORMATION_MESSAGE);
    }

    private DecryptorCandidate getSelectedDecryptor() {
        if (autoDetectCheckbox.isSelected() && !decryptorCandidates.isEmpty()) {
            return decryptorCandidates.get(0);
        }
        return (DecryptorCandidate) decryptorSelector.getSelectedItem();
    }

    private void updatePreview() {
        int selectedRow = resultsTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= results.size()) {
            previewArea.setText("");
            return;
        }

        DeobfuscationResult result = results.get(selectedRow);
        StringBuilder sb = new StringBuilder();
        sb.append("Location: ").append(result.getLocation()).append("\n\n");
        sb.append("Original:\n").append(result.getOriginalValue()).append("\n\n");

        if (result.isSuccess()) {
            sb.append("Decrypted:\n").append(result.getDecryptedValue()).append("\n\n");
            sb.append("Time: ").append(result.getExecutionTimeMs()).append("ms");
        } else if (result.getErrorMessage() != null) {
            sb.append("Error: ").append(result.getErrorMessage());
        }

        previewArea.setText(sb.toString());
        previewArea.setCaretPosition(0);
    }

    private void updateButtonStates() {
        boolean hasResults = !results.isEmpty();
        boolean hasSelection = resultsTable.getSelectedRowCount() > 0;
        boolean hasSuccessful = results.stream().anyMatch(r -> r.isSuccess() && !r.isApplied());

        decryptSelectedButton.setEnabled(hasSelection);
        decryptAllButton.setEnabled(hasResults);
        applyButton.setEnabled(hasSuccessful);
    }

    private class ResultsTableModel extends AbstractTableModel {
        private final String[] columns = {"", "Location", "Encrypted", "Decrypted", "Status"};

        @Override
        public int getRowCount() {
            return results.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) return Boolean.class;
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            DeobfuscationResult result = results.get(rowIndex);
            switch (columnIndex) {
                case 0: return result.isSuccess();
                case 1: return result.getLocation();
                case 2: return result.getDisplayOriginal();
                case 3: return result.getDisplayDecrypted();
                case 4: return result.getStatusText();
                default: return "";
            }
        }
    }

    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            String status = (String) value;
            if (!isSelected) {
                if ("Decrypted".equals(status)) {
                    setForeground(JStudioTheme.getSuccess());
                } else if ("Applied".equals(status)) {
                    setForeground(JStudioTheme.getAccent());
                } else if ("Failed".equals(status)) {
                    setForeground(JStudioTheme.getError());
                } else {
                    setForeground(JStudioTheme.getTextSecondary());
                }
            }

            return this;
        }
    }
}
