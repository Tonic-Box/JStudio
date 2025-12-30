package com.tonic.ui.analysis;

import com.tonic.analysis.similarity.MethodSimilarityAnalyzer;
import com.tonic.analysis.similarity.MethodSignature;
import com.tonic.analysis.similarity.SimilarityMetric;
import com.tonic.analysis.similarity.SimilarityResult;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.editor.bytecode.BytecodeFormatter;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.util.JdkClassFilter;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class SimilarityPanel extends ThemedJPanel {

    private final ProjectModel project;
    private MethodSimilarityAnalyzer analyzer;

    private JComboBox<SimilarityMetric> metricCombo;
    private JSlider thresholdSlider;
    private JLabel thresholdLabel;
    private JButton analyzeButton;
    private JButton findDuplicatesButton;
    private JButton exportButton;

    private JTable resultsTable;
    private SimilarityTableModel tableModel;

    private JTextArea leftCode;
    private JTextArea rightCode;
    private JLabel leftLabel;
    private JLabel rightLabel;
    private JTextArea detailsArea;

    private JLabel statusLabel;

    public SimilarityPanel(ProjectModel project) {
        super(BackgroundStyle.SECONDARY, new BorderLayout());
        this.project = project;

        // Build UI
        add(createControlPanel(), BorderLayout.NORTH);
        add(createMainContent(), BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);

        updateStatus("Click 'Build Index' to analyze methods for similarity.");
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_SMALL));
        panel.setBackground(JStudioTheme.getBgSecondary());
        panel.setBorder(BorderFactory.createEmptyBorder(UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL));

        // Build index button
        JButton buildButton = new JButton("Build Index");
        buildButton.setBackground(JStudioTheme.getBgTertiary());
        buildButton.setForeground(JStudioTheme.getTextPrimary());
        buildButton.addActionListener(e -> buildIndex());
        panel.add(buildButton);

        panel.add(Box.createHorizontalStrut(16));

        // Metric selection
        panel.add(createLabel("Metric:"));
        metricCombo = new JComboBox<>(SimilarityMetric.values());
        metricCombo.setSelectedItem(SimilarityMetric.COMBINED);
        metricCombo.setBackground(JStudioTheme.getBgTertiary());
        metricCombo.setForeground(JStudioTheme.getTextPrimary());
        metricCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof SimilarityMetric) {
                    setText(((SimilarityMetric) value).getDisplayName());
                }
                return this;
            }
        });
        panel.add(metricCombo);

        panel.add(Box.createHorizontalStrut(16));

        // Threshold slider
        panel.add(createLabel("Min Score:"));
        thresholdSlider = new JSlider(50, 100, 80);
        thresholdSlider.setBackground(JStudioTheme.getBgSecondary());
        thresholdSlider.setPreferredSize(new Dimension(100, 20));
        thresholdSlider.addChangeListener(e -> {
            thresholdLabel.setText(thresholdSlider.getValue() + "%");
        });
        panel.add(thresholdSlider);
        thresholdLabel = createLabel("80%");
        panel.add(thresholdLabel);

        panel.add(Box.createHorizontalStrut(16));

        // Analysis buttons
        analyzeButton = new JButton("Find Similar");
        analyzeButton.setBackground(JStudioTheme.getBgTertiary());
        analyzeButton.setForeground(JStudioTheme.getTextPrimary());
        analyzeButton.setEnabled(false);
        analyzeButton.addActionListener(e -> findSimilar());
        panel.add(analyzeButton);

        findDuplicatesButton = new JButton("Find Duplicates");
        findDuplicatesButton.setBackground(JStudioTheme.getBgTertiary());
        findDuplicatesButton.setForeground(JStudioTheme.getTextPrimary());
        findDuplicatesButton.setEnabled(false);
        findDuplicatesButton.addActionListener(e -> findDuplicates());
        panel.add(findDuplicatesButton);

        JButton renamedButton = new JButton("Find Renamed");
        renamedButton.setBackground(JStudioTheme.getBgTertiary());
        renamedButton.setForeground(JStudioTheme.getTextPrimary());
        renamedButton.setEnabled(false);
        renamedButton.addActionListener(e -> findRenamed());
        panel.add(renamedButton);

        panel.add(Box.createHorizontalStrut(16));

        // Export button
        exportButton = new JButton("Export CSV");
        exportButton.setBackground(JStudioTheme.getBgTertiary());
        exportButton.setForeground(JStudioTheme.getTextPrimary());
        exportButton.setEnabled(false);
        exportButton.addActionListener(e -> exportResults());
        panel.add(exportButton);

        return panel;
    }

    private JSplitPane createMainContent() {
        // Results table
        tableModel = new SimilarityTableModel();
        resultsTable = new JTable(tableModel);
        resultsTable.setBackground(JStudioTheme.getBgTertiary());
        resultsTable.setForeground(JStudioTheme.getTextPrimary());
        resultsTable.setSelectionBackground(JStudioTheme.getAccent());
        resultsTable.setSelectionForeground(JStudioTheme.getTextPrimary());
        resultsTable.setGridColor(JStudioTheme.getBorder());
        resultsTable.setRowHeight(UIConstants.TABLE_ROW_HEIGHT + 4);
        resultsTable.getTableHeader().setBackground(JStudioTheme.getBgSecondary());
        resultsTable.getTableHeader().setForeground(JStudioTheme.getTextPrimary());

        // Set column widths
        resultsTable.getColumnModel().getColumn(0).setPreferredWidth(60);  // Score
        resultsTable.getColumnModel().getColumn(1).setPreferredWidth(250); // Method 1
        resultsTable.getColumnModel().getColumn(2).setPreferredWidth(250); // Method 2
        resultsTable.getColumnModel().getColumn(3).setPreferredWidth(100); // Metric

        // Score column renderer with color coding
        resultsTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(CENTER);
                if (value instanceof Integer) {
                    int score = (Integer) value;
                    if (!isSelected) {
                        if (score >= 95) {
                            setBackground(deriveMatchBg(JStudioTheme.getSuccess()));
                        } else if (score >= 80) {
                            setBackground(deriveMatchBg(JStudioTheme.getWarning()));
                        } else {
                            setBackground(JStudioTheme.getBgTertiary());
                        }
                    }
                }
                return this;
            }
        });

        // Selection listener for comparison
        resultsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = resultsTable.getSelectedRow();
                if (row >= 0) {
                    showComparison(tableModel.getResult(row));
                }
            }
        });

        // Double-click to navigate
        resultsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = resultsTable.getSelectedRow();
                    if (row >= 0) {
                        // Could navigate to method - placeholder for now
                    }
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(resultsTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                "Similar Methods", 0, 0, null, JStudioTheme.getTextSecondary()));
        tableScroll.setPreferredSize(new Dimension(700, 200));

        // Comparison panel
        JPanel comparisonPanel = createComparisonPanel();

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, comparisonPanel);
        splitPane.setDividerLocation(200);
        splitPane.setResizeWeight(0.4);
        splitPane.setBackground(JStudioTheme.getBgSecondary());

        return splitPane;
    }

    private JPanel createComparisonPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(JStudioTheme.getBgSecondary());
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                "Side-by-Side Comparison", 0, 0, null, JStudioTheme.getTextSecondary()));

        // Code comparison split
        JPanel codePanel = new JPanel(new GridLayout(1, 2, 4, 0));
        codePanel.setBackground(JStudioTheme.getBgSecondary());

        // Left side
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(JStudioTheme.getBgSecondary());
        leftLabel = new JLabel("Method 1");
        leftLabel.setForeground(JStudioTheme.getAccent());
        leftLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        leftPanel.add(leftLabel, BorderLayout.NORTH);

        leftCode = new JTextArea();
        leftCode.setEditable(false);
        leftCode.setBackground(JStudioTheme.getBgTertiary());
        leftCode.setForeground(JStudioTheme.getTextPrimary());
        leftCode.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE));
        JScrollPane leftScroll = new JScrollPane(leftCode);
        leftScroll.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));
        leftPanel.add(leftScroll, BorderLayout.CENTER);
        codePanel.add(leftPanel);

        // Right side
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(JStudioTheme.getBgSecondary());
        rightLabel = new JLabel("Method 2");
        rightLabel.setForeground(JStudioTheme.getWarning());
        rightLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        rightPanel.add(rightLabel, BorderLayout.NORTH);

        rightCode = new JTextArea();
        rightCode.setEditable(false);
        rightCode.setBackground(JStudioTheme.getBgTertiary());
        rightCode.setForeground(JStudioTheme.getTextPrimary());
        rightCode.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE));
        JScrollPane rightScroll = new JScrollPane(rightCode);
        rightScroll.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));
        rightPanel.add(rightScroll, BorderLayout.CENTER);
        codePanel.add(rightPanel);

        panel.add(codePanel, BorderLayout.CENTER);

        // Details area at bottom
        detailsArea = new JTextArea(3, 40);
        detailsArea.setEditable(false);
        detailsArea.setBackground(JStudioTheme.getBgTertiary());
        detailsArea.setForeground(JStudioTheme.getTextSecondary());
        detailsArea.setFont(JStudioTheme.getCodeFont(10));
        detailsArea.setBorder(BorderFactory.createEmptyBorder(UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL));

        JScrollPane detailsScroll = new JScrollPane(detailsArea);
        detailsScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()));
        detailsScroll.setPreferredSize(new Dimension(700, 60));
        panel.add(detailsScroll, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createStatusBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(JStudioTheme.getBgPrimary());
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()));

        statusLabel = new JLabel(" ");
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, UIConstants.SPACING_MEDIUM, 2, UIConstants.SPACING_MEDIUM));
        panel.add(statusLabel, BorderLayout.WEST);

        return panel;
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(JStudioTheme.getTextSecondary());
        return label;
    }

    private static Color deriveMatchBg(Color base) {
        return new Color(base.getRed() / 5, base.getGreen() / 3, base.getBlue() / 5);
    }

    // ==================== Actions ====================

    private void buildIndex() {
        if (project.getClassPool() == null) {
            updateStatus("No project loaded. Open a JAR or class file first.");
            return;
        }

        updateStatus("Building method index...");

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                analyzer = new MethodSimilarityAnalyzer(project.getClassPool());
                analyzer.setProgressCallback(this::publish);
                analyzer.buildIndex();
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    updateStatus(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                analyzeButton.setEnabled(true);
                findDuplicatesButton.setEnabled(true);
                updateStatus("Index built: " + analyzer.getMethodCount() + " methods. Ready for analysis.");
            }
        };

        worker.execute();
    }

    private void findSimilar() {
        if (analyzer == null) {
            updateStatus("Build index first.");
            return;
        }

        SimilarityMetric metric = (SimilarityMetric) metricCombo.getSelectedItem();
        double threshold = thresholdSlider.getValue() / 100.0;

        updateStatus("Finding similar methods (" + metric.getDisplayName() + " >= " + thresholdSlider.getValue() + "%)...");

        SwingWorker<List<SimilarityResult>, String> worker = new SwingWorker<>() {
            @Override
            protected List<SimilarityResult> doInBackground() {
                analyzer.setProgressCallback(this::publish);
                return analyzer.findAllSimilar(metric, threshold);
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    updateStatus(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                try {
                    List<SimilarityResult> results = get();
                    tableModel.setResults(results);
                    exportButton.setEnabled(!results.isEmpty());
                    updateStatus("Found " + results.size() + " similar method pairs.");
                } catch (Exception e) {
                    updateStatus("Analysis failed: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    private void findDuplicates() {
        if (analyzer == null) {
            updateStatus("Build index first.");
            return;
        }

        updateStatus("Finding exact/near duplicates...");

        SwingWorker<List<SimilarityResult>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<SimilarityResult> doInBackground() {
                return analyzer.findDuplicates();
            }

            @Override
            protected void done() {
                try {
                    List<SimilarityResult> results = get();
                    tableModel.setResults(results);
                    exportButton.setEnabled(!results.isEmpty());
                    updateStatus("Found " + results.size() + " duplicate method pairs (>= 95% similarity).");
                } catch (Exception e) {
                    updateStatus("Analysis failed: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    private void findRenamed() {
        if (analyzer == null) {
            updateStatus("Build index first.");
            return;
        }

        updateStatus("Finding renamed copies (potential obfuscation)...");

        SwingWorker<List<SimilarityResult>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<SimilarityResult> doInBackground() {
                return analyzer.findRenamedCopies();
            }

            @Override
            protected void done() {
                try {
                    List<SimilarityResult> results = get();
                    tableModel.setResults(results);
                    exportButton.setEnabled(!results.isEmpty());
                    updateStatus("Found " + results.size() + " potentially renamed method pairs.");
                } catch (Exception e) {
                    updateStatus("Analysis failed: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    private void showComparison(SimilarityResult result) {
        if (result == null) return;

        MethodSignature sig1 = result.getMethod1();
        MethodSignature sig2 = result.getMethod2();

        leftLabel.setText(sig1.getDisplayName() + sig1.getDescriptor());
        rightLabel.setText(sig2.getDisplayName() + sig2.getDescriptor());

        // Get bytecode disassembly for both methods
        String code1 = getMethodBytecode(sig1);
        String code2 = getMethodBytecode(sig2);

        leftCode.setText(code1);
        rightCode.setText(code2);

        // Reset scroll positions
        leftCode.setCaretPosition(0);
        rightCode.setCaretPosition(0);

        // Show detailed scores
        StringBuilder details = new StringBuilder();
        details.append("Overall Score: ").append(result.getScorePercent()).append("% (")
                .append(result.getSummary()).append(")\n");
        details.append("Individual Scores: ");
        for (SimilarityMetric metric : SimilarityMetric.values()) {
            if (metric != SimilarityMetric.COMBINED) {
                double score = result.getScore(metric);
                details.append(metric.getDisplayName()).append(": ")
                        .append(String.format("%.0f%%", score * 100)).append("  ");
            }
        }
        details.append("\n");
        details.append("Method 1: ").append(sig1.getInstructionCount()).append(" instructions, ")
                .append(sig1.getBranchCount()).append(" branches, ")
                .append(sig1.getCallCount()).append(" calls\n");
        details.append("Method 2: ").append(sig2.getInstructionCount()).append(" instructions, ")
                .append(sig2.getBranchCount()).append(" branches, ")
                .append(sig2.getCallCount()).append(" calls");

        detailsArea.setText(details.toString());
    }

    private String getMethodBytecode(MethodSignature sig) {
        // Find the method in the class pool
        if (project.getClassPool() == null) {
            return "// Class pool not available";
        }

        for (ClassFile cf : project.getClassPool().getClasses()) {
            if (JdkClassFilter.isJdkClass(cf.getClassName())) {
                continue;
            }
            if (cf.getClassName().equals(sig.getClassName())) {
                for (MethodEntry method : cf.getMethods()) {
                    if (method.getName().equals(sig.getMethodName()) &&
                        method.getDesc().equals(sig.getDescriptor())) {
                        // Get bytecode disassembly
                        if (method.getCodeAttribute() != null) {
                            BytecodeFormatter formatter = new BytecodeFormatter(method);
                            return formatter.format();
                        } else {
                            return "// No code attribute (abstract/native)";
                        }
                    }
                }
            }
        }
        return "// Method not found";
    }

    private void exportResults() {
        if (tableModel.getRowCount() == 0) {
            updateStatus("No results to export.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Similarity Results");
        chooser.setSelectedFile(new File("similarity_results.csv"));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                // Header
                writer.println("Score,Method1,Method2,Metric,ExactBytecode,OpcodeSequence,Structural");

                // Data
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    SimilarityResult result = tableModel.getResult(i);
                    writer.printf("%d,%s,%s,%s,%.2f,%.2f,%.2f%n",
                            result.getScorePercent(),
                            result.getMethod1().getFullReference(),
                            result.getMethod2().getFullReference(),
                            result.getPrimaryMetric().getDisplayName(),
                            result.getScore(SimilarityMetric.EXACT_BYTECODE),
                            result.getScore(SimilarityMetric.OPCODE_SEQUENCE),
                            result.getScore(SimilarityMetric.STRUCTURAL));
                }

                updateStatus("Exported " + tableModel.getRowCount() + " results to: " + file.getName());
            } catch (IOException e) {
                updateStatus("Export failed: " + e.getMessage());
            }
        }
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    /**
     * Refresh the panel.
     */
    public void refresh() {
        if (analyzer != null) {
            updateStatus("Index contains " + analyzer.getMethodCount() + " methods.");
        }
    }

    // ==================== Table Model ====================

    private static class SimilarityTableModel extends AbstractTableModel {
        private final String[] COLUMNS = {"Score", "Method 1", "Method 2", "Metric"};
        private List<SimilarityResult> results = new ArrayList<>();

        public void setResults(List<SimilarityResult> results) {
            this.results = results != null ? results : new ArrayList<>();
            fireTableDataChanged();
        }

        public SimilarityResult getResult(int row) {
            if (row >= 0 && row < results.size()) {
                return results.get(row);
            }
            return null;
        }

        @Override
        public int getRowCount() {
            return results.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Integer.class : String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            SimilarityResult result = results.get(row);
            switch (column) {
                case 0: return result.getScorePercent();
                case 1: return result.getMethod1().getDisplayName();
                case 2: return result.getMethod2().getDisplayName();
                case 3: return result.getPrimaryMetric().getDisplayName();
                default: return "";
            }
        }
    }
}
