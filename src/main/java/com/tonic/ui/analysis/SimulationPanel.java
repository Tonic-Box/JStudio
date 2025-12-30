package com.tonic.ui.analysis;

import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.ClassSelectedEvent;
import com.tonic.ui.event.events.MethodSelectedEvent;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.simulation.SimulationAnalysisResult;
import com.tonic.ui.simulation.SimulationService;
import com.tonic.ui.simulation.model.DeadCodeBlock;
import com.tonic.ui.simulation.model.DecryptedString;
import com.tonic.ui.simulation.model.OpaquePredicate;
import com.tonic.ui.simulation.model.SimulationFinding;
import com.tonic.ui.simulation.model.TaintFlow;
import com.tonic.ui.simulation.export.FindingsExporter;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SimulationPanel extends ThemedJPanel {

    private final ProjectModel project;
    private final JTable findingsTable;
    private final FindingsTableModel tableModel;
    private final JLabel statusLabel;
    private final JButton analyzeCurrentButton;
    private final JButton analyzeAllButton;
    private final JProgressBar progressBar;
    private final JTextArea detailsArea;
    private final JComboBox<String> filterCombo;

    private final List<FindingEntry> allFindings = new ArrayList<>();
    private final List<FindingEntry> filteredFindings = new ArrayList<>();
    private String currentFilter = "All";

    public SimulationPanel(ProjectModel project) {
        super(BackgroundStyle.SECONDARY, new BorderLayout());
        this.project = project;

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_SMALL));
        toolbar.setBackground(JStudioTheme.getBgSecondary());

        analyzeCurrentButton = new JButton("Analyze Current");
        analyzeCurrentButton.setBackground(JStudioTheme.getBgTertiary());
        analyzeCurrentButton.setForeground(JStudioTheme.getTextPrimary());
        analyzeCurrentButton.setToolTipText("Run simulation analysis on currently selected method/class");
        analyzeCurrentButton.addActionListener(e -> analyzeCurrentSelection());
        toolbar.add(analyzeCurrentButton);

        analyzeAllButton = new JButton("Analyze Project");
        analyzeAllButton.setBackground(JStudioTheme.getBgTertiary());
        analyzeAllButton.setForeground(JStudioTheme.getTextPrimary());
        analyzeAllButton.setToolTipText("Run simulation analysis on all methods in the project");
        analyzeAllButton.addActionListener(e -> analyzeProject());
        toolbar.add(analyzeAllButton);

        toolbar.add(Box.createHorizontalStrut(16));

        JLabel filterLabel = new JLabel("Filter:");
        filterLabel.setForeground(JStudioTheme.getTextSecondary());
        toolbar.add(filterLabel);

        filterCombo = new JComboBox<>(new String[]{"All", "Opaque Predicates", "Dead Code", "Decrypted Strings", "Taint Flows"});
        filterCombo.setBackground(JStudioTheme.getBgTertiary());
        filterCombo.setForeground(JStudioTheme.getTextPrimary());
        filterCombo.addActionListener(e -> applyFilter());
        toolbar.add(filterCombo);

        toolbar.add(Box.createHorizontalStrut(8));

        JButton clearButton = new JButton("Clear");
        clearButton.setBackground(JStudioTheme.getBgTertiary());
        clearButton.setForeground(JStudioTheme.getTextPrimary());
        clearButton.addActionListener(e -> clearFindings());
        toolbar.add(clearButton);

        JButton exportButton = new JButton("Export");
        exportButton.setBackground(JStudioTheme.getBgTertiary());
        exportButton.setForeground(JStudioTheme.getTextPrimary());
        exportButton.setToolTipText("Export findings to JSON or HTML file");
        exportButton.addActionListener(e -> exportFindings());
        toolbar.add(exportButton);

        progressBar = new JProgressBar();
        progressBar.setPreferredSize(new Dimension(150, 20));
        progressBar.setVisible(false);
        toolbar.add(progressBar);

        add(toolbar, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.6);
        splitPane.setBackground(JStudioTheme.getBgSecondary());
        splitPane.setBorder(null);

        tableModel = new FindingsTableModel();
        findingsTable = new JTable(tableModel);
        findingsTable.setBackground(JStudioTheme.getBgTertiary());
        findingsTable.setForeground(JStudioTheme.getTextPrimary());
        findingsTable.setSelectionBackground(JStudioTheme.getSelection());
        findingsTable.setSelectionForeground(JStudioTheme.getTextPrimary());
        findingsTable.setGridColor(JStudioTheme.getBorder());
        findingsTable.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE));
        findingsTable.setRowHeight(UIConstants.TABLE_ROW_HEIGHT);
        findingsTable.getTableHeader().setBackground(JStudioTheme.getBgSecondary());
        findingsTable.getTableHeader().setForeground(JStudioTheme.getTextPrimary());

        findingsTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        findingsTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        findingsTable.getColumnModel().getColumn(2).setPreferredWidth(200);
        findingsTable.getColumnModel().getColumn(3).setPreferredWidth(150);
        findingsTable.getColumnModel().getColumn(4).setPreferredWidth(60);

        findingsTable.setDefaultRenderer(Object.class, new FindingTableCellRenderer());

        findingsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelectedFindingDetails();
            }
        });

        findingsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateToSelectedFinding();
                }
            }
        });

        JScrollPane tableScrollPane = new JScrollPane(findingsTable);
        tableScrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, JStudioTheme.getBorder()));
        tableScrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());
        splitPane.setTopComponent(tableScrollPane);

        JPanel detailsPanel = new JPanel(new BorderLayout());
        detailsPanel.setBackground(JStudioTheme.getBgSecondary());

        JLabel detailsLabel = new JLabel(" Finding Details");
        detailsLabel.setForeground(JStudioTheme.getTextSecondary());
        detailsLabel.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE).deriveFont(Font.BOLD));
        detailsPanel.add(detailsLabel, BorderLayout.NORTH);

        detailsArea = new JTextArea();
        detailsArea.setBackground(JStudioTheme.getBgTertiary());
        detailsArea.setForeground(JStudioTheme.getTextPrimary());
        detailsArea.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE));
        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);

        JScrollPane detailsScrollPane = new JScrollPane(detailsArea);
        detailsScrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()));
        detailsScrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());
        detailsPanel.add(detailsScrollPane, BorderLayout.CENTER);

        splitPane.setBottomComponent(detailsPanel);
        add(splitPane, BorderLayout.CENTER);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBackground(JStudioTheme.getBgSecondary());
        statusLabel = new JLabel("Ready. Select a method and click 'Analyze Current' or 'Analyze Project'.");
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        statusPanel.add(statusLabel);
        add(statusPanel, BorderLayout.SOUTH);
    }

    public void refresh() {
        tableModel.fireTableDataChanged();
    }

    private void analyzeCurrentSelection() {
        statusLabel.setText("Analysis feature - select a method first");
    }

    public void analyzeMethod(MethodEntryModel methodModel) {
        if (methodModel == null) {
            statusLabel.setText("No method selected.");
            return;
        }

        setAnalyzing(true);
        statusLabel.setText("Analyzing " + methodModel.getDisplaySignature() + "...");

        SwingWorker<SimulationAnalysisResult, Void> worker = new SwingWorker<>() {
            @Override
            protected SimulationAnalysisResult doInBackground() {
                return SimulationService.getInstance().runAnalysis(methodModel);
            }

            @Override
            protected void done() {
                setAnalyzing(false);
                try {
                    SimulationAnalysisResult result = get();
                    if (result != null) {
                        addFindingsFromResult(result);
                        updateStatusWithCounts();
                    } else {
                        statusLabel.setText("Analysis returned no results.");
                    }
                } catch (Exception e) {
                    statusLabel.setText("Analysis error: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    public void analyzeClass(ClassEntryModel classModel) {
        if (classModel == null) {
            statusLabel.setText("No class selected.");
            return;
        }

        setAnalyzing(true);
        statusLabel.setText("Analyzing " + classModel.getSimpleName() + "...");
        progressBar.setMaximum(classModel.getMethods().size());
        progressBar.setValue(0);

        SwingWorker<Void, Integer> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                int count = 0;
                for (MethodEntryModel methodModel : classModel.getMethods()) {
                    SimulationAnalysisResult result = SimulationService.getInstance().runAnalysis(methodModel);
                    if (result != null) {
                        SwingUtilities.invokeLater(() -> addFindingsFromResult(result));
                    }
                    count++;
                    publish(count);
                }
                return null;
            }

            @Override
            protected void process(List<Integer> chunks) {
                if (!chunks.isEmpty()) {
                    progressBar.setValue(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                setAnalyzing(false);
                updateStatusWithCounts();
            }
        };
        worker.execute();
    }

    private void analyzeProject() {
        if (project.getClassPool() == null) {
            statusLabel.setText("No project loaded.");
            return;
        }

        setAnalyzing(true);
        clearFindings();
        statusLabel.setText("Analyzing project...");

        List<ClassEntryModel> classes = project.getUserClasses();
        int totalMethods = classes.stream().mapToInt(c -> c.getMethods().size()).sum();
        progressBar.setMaximum(totalMethods);
        progressBar.setValue(0);

        SwingWorker<Void, Integer> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                int count = 0;
                for (ClassEntryModel classModel : classes) {
                    for (MethodEntryModel methodModel : classModel.getMethods()) {
                        SimulationAnalysisResult result = SimulationService.getInstance().runAnalysis(methodModel);
                        if (result != null && result.hasFindings()) {
                            SwingUtilities.invokeLater(() -> addFindingsFromResult(result));
                        }
                        count++;
                        publish(count);
                    }
                }
                return null;
            }

            @Override
            protected void process(List<Integer> chunks) {
                if (!chunks.isEmpty()) {
                    int value = chunks.get(chunks.size() - 1);
                    progressBar.setValue(value);
                    statusLabel.setText("Analyzing... (" + value + "/" + progressBar.getMaximum() + ")");
                }
            }

            @Override
            protected void done() {
                setAnalyzing(false);
                updateStatusWithCounts();
            }
        };
        worker.execute();
    }

    private void addFindingsFromResult(SimulationAnalysisResult result) {
        for (SimulationFinding finding : result.getFindings()) {
            FindingEntry entry = new FindingEntry(finding, result.getMethod());
            allFindings.add(entry);
        }
        applyFilter();
    }

    private void applyFilter() {
        currentFilter = (String) filterCombo.getSelectedItem();
        filteredFindings.clear();

        for (FindingEntry entry : allFindings) {
            if (matchesFilter(entry.finding)) {
                filteredFindings.add(entry);
            }
        }

        tableModel.fireTableDataChanged();
        updateStatusWithCounts();
    }

    private boolean matchesFilter(SimulationFinding finding) {
        if (currentFilter == null || "All".equals(currentFilter)) {
            return true;
        }
        switch (currentFilter) {
            case "Opaque Predicates":
                return finding.getType() == SimulationFinding.FindingType.OPAQUE_PREDICATE;
            case "Dead Code":
                return finding.getType() == SimulationFinding.FindingType.DEAD_CODE;
            case "Decrypted Strings":
                return finding.getType() == SimulationFinding.FindingType.DECRYPTED_STRING;
            case "Taint Flows":
                return finding.getType() == SimulationFinding.FindingType.TAINTED_VALUE;
            default:
                return true;
        }
    }

    private void updateStatusWithCounts() {
        int opaqueCount = 0;
        int deadCount = 0;
        int decryptedCount = 0;
        int taintCount = 0;
        for (FindingEntry entry : allFindings) {
            if (entry.finding.getType() == SimulationFinding.FindingType.OPAQUE_PREDICATE) {
                opaqueCount++;
            } else if (entry.finding.getType() == SimulationFinding.FindingType.DEAD_CODE) {
                deadCount++;
            } else if (entry.finding.getType() == SimulationFinding.FindingType.DECRYPTED_STRING) {
                decryptedCount++;
            } else if (entry.finding.getType() == SimulationFinding.FindingType.TAINTED_VALUE) {
                taintCount++;
            }
        }
        statusLabel.setText("Findings: " + allFindings.size() + " (" +
                opaqueCount + " opaque, " + deadCount + " dead, " +
                decryptedCount + " decrypted, " + taintCount + " taint) | " +
                "Showing: " + filteredFindings.size());
    }

    private void clearFindings() {
        allFindings.clear();
        filteredFindings.clear();
        detailsArea.setText("");
        tableModel.fireTableDataChanged();
        statusLabel.setText("Findings cleared.");
    }

    private void exportFindings() {
        if (allFindings.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No findings to export.",
                    "Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Findings");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "JSON or HTML files", "json", "html"));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            String path = file.getAbsolutePath();

            try {
                List<SimulationFinding> findings = new ArrayList<>();
                for (FindingEntry entry : allFindings) {
                    findings.add(entry.finding);
                }

                if (path.endsWith(".html") || path.endsWith(".htm")) {
                    FindingsExporter.exportToHtml(findings, path);
                } else {
                    if (!path.endsWith(".json")) {
                        path += ".json";
                    }
                    FindingsExporter.exportToJson(findings, path);
                }
                statusLabel.setText("Exported " + findings.size() + " findings to " + file.getName());
            } catch (java.io.IOException ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
                        "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void setAnalyzing(boolean analyzing) {
        analyzeCurrentButton.setEnabled(!analyzing);
        analyzeAllButton.setEnabled(!analyzing);
        progressBar.setVisible(analyzing);
        if (!analyzing) {
            progressBar.setValue(0);
        }
    }

    private void showSelectedFindingDetails() {
        int row = findingsTable.getSelectedRow();
        if (row < 0 || row >= filteredFindings.size()) {
            detailsArea.setText("");
            return;
        }

        FindingEntry entry = filteredFindings.get(row);
        SimulationFinding finding = entry.finding;

        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(finding.getTitle()).append(" ===\n\n");
        sb.append("Type: ").append(finding.getType()).append("\n");
        sb.append("Severity: ").append(finding.getSeverity()).append("\n");
        sb.append("Location: ").append(finding.getMethodSignature()).append("\n\n");
        sb.append("--- Description ---\n");
        sb.append(finding.getDescription()).append("\n\n");
        sb.append("--- Recommendation ---\n");
        sb.append(finding.getRecommendation());

        detailsArea.setText(sb.toString());
        detailsArea.setCaretPosition(0);
    }

    private void navigateToSelectedFinding() {
        int row = findingsTable.getSelectedRow();
        if (row < 0 || row >= filteredFindings.size()) {
            return;
        }

        FindingEntry entry = filteredFindings.get(row);
        if (entry.method != null) {
            ClassEntryModel classModel = entry.method.getOwner();
            if (classModel != null) {
                EventBus.getInstance().post(new ClassSelectedEvent(this, classModel));
                EventBus.getInstance().post(new MethodSelectedEvent(this, entry.method));
            }
        }
    }

    private class FindingsTableModel extends AbstractTableModel {
        private final String[] columns = {"Type", "Severity", "Class", "Method", "Block"};

        @Override
        public int getRowCount() {
            return filteredFindings.size();
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
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex < 0 || rowIndex >= filteredFindings.size()) {
                return null;
            }

            FindingEntry entry = filteredFindings.get(rowIndex);
            SimulationFinding finding = entry.finding;

            switch (columnIndex) {
                case 0:
                    return finding.getType().name();
                case 1:
                    return finding.getSeverity().name();
                case 2:
                    String className = finding.getClassName();
                    int lastSlash = className.lastIndexOf('/');
                    return lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
                case 3:
                    return finding.getMethodName();
                case 4:
                    if (finding instanceof OpaquePredicate) {
                        return "B" + ((OpaquePredicate) finding).getBlockId();
                    } else if (finding instanceof DeadCodeBlock) {
                        return "B" + ((DeadCodeBlock) finding).getBlockId();
                    } else if (finding instanceof DecryptedString) {
                        return "B" + ((DecryptedString) finding).getBlockId();
                    } else if (finding instanceof TaintFlow) {
                        return "B" + ((TaintFlow) finding).getBlockId();
                    }
                    return "-";
                default:
                    return null;
            }
        }
    }

    private static class FindingTableCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (!isSelected) {
                c.setBackground(JStudioTheme.getBgTertiary());
                c.setForeground(JStudioTheme.getTextPrimary());

                if (column == 1 && value != null) {
                    String severity = value.toString();
                    switch (severity) {
                        case "CRITICAL":
                        case "HIGH":
                            c.setForeground(JStudioTheme.getError());
                            break;
                        case "MEDIUM":
                            c.setForeground(JStudioTheme.getWarning());
                            break;
                        case "LOW":
                            c.setForeground(JStudioTheme.getInfo());
                            break;
                        default:
                            c.setForeground(JStudioTheme.getTextSecondary());
                            break;
                    }
                }
            }

            return c;
        }
    }

    private static class FindingEntry {
        final SimulationFinding finding;
        final MethodEntryModel method;

        FindingEntry(SimulationFinding finding, MethodEntryModel method) {
            this.finding = finding;
            this.method = method;
        }
    }
}
