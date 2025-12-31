package com.tonic.ui.query;

import com.tonic.analysis.xref.XrefBuilder;
import com.tonic.analysis.xref.XrefDatabase;
import com.tonic.parser.ClassPool;
import com.tonic.ui.MainFrame;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.query.exec.QueryBatchRunner;
import com.tonic.ui.query.exec.QueryService;
import com.tonic.ui.query.planner.ClickTarget;
import com.tonic.ui.query.planner.ResultRow;
import com.tonic.ui.query.planner.filter.XrefMethodFilter;
import com.tonic.ui.service.ProjectService;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class QueryDialog extends JDialog {

    private JTabbedPane inputTabbedPane;
    private JTextArea queryInput;
    private QueryBuilderPanel builderPanel;
    private JButton runButton;
    private JButton stopButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;

    private JSpinner maxMethodsSpinner;
    private JSpinner seedsSpinner;
    private JSpinner timeBudgetSpinner;

    private JTable resultsTable;
    private TreeResultTableModel tableModel;

    private final QueryService queryService;
    private volatile boolean running;
    private MainFrame mainFrame;

    public QueryDialog(Window owner, ClassPool classPool, XrefDatabase xrefDatabase) {
        this(owner, classPool, () -> xrefDatabase);
    }

    public QueryDialog(Window owner, ClassPool classPool, Supplier<XrefDatabase> xrefDatabaseSupplier) {
        super(owner, "Query Explorer", ModalityType.MODELESS);
        this.queryService = new QueryService(classPool, xrefDatabaseSupplier);
        if (owner instanceof MainFrame) {
            this.mainFrame = (MainFrame) owner;
        }
        initComponents();
        applyTheme();
        pack();
        setMinimumSize(new Dimension(900, 700));
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel inputPanel = createInputPanel();
        add(inputPanel, BorderLayout.NORTH);

        JPanel resultsPanel = createResultsPanel();
        add(resultsPanel, BorderLayout.CENTER);

        JPanel buttonPanel = createButtonPanel();
        add(buttonPanel, BorderLayout.SOUTH);

        setupKeyboardShortcuts();
    }

    private void applyTheme() {
        Color bgPrimary = JStudioTheme.getBgPrimary();
        Color bgSecondary = JStudioTheme.getBgSecondary();
        Color bgSurface = JStudioTheme.getBgSurface();
        Color textPrimary = JStudioTheme.getTextPrimary();
        Color textSecondary = JStudioTheme.getTextSecondary();
        Color accent = JStudioTheme.getAccent();
        Color border = JStudioTheme.getBorder();
        Color selection = JStudioTheme.getSelection();

        getContentPane().setBackground(bgPrimary);

        inputTabbedPane.setBackground(bgPrimary);
        inputTabbedPane.setForeground(textPrimary);

        queryInput.setBackground(bgSurface);
        queryInput.setForeground(textPrimary);
        queryInput.setCaretColor(textPrimary);
        queryInput.setSelectionColor(selection);
        queryInput.setSelectedTextColor(textPrimary);
        queryInput.setFont(JStudioTheme.getCodeFont(13));

        builderPanel.applyTheme();

        resultsTable.setBackground(bgSurface);
        resultsTable.setForeground(textPrimary);
        resultsTable.setSelectionBackground(selection);
        resultsTable.setSelectionForeground(textPrimary);
        resultsTable.setGridColor(border);
        resultsTable.setFont(JStudioTheme.getUIFont(12));

        JTableHeader header = resultsTable.getTableHeader();
        header.setBackground(bgSecondary);
        header.setForeground(textPrimary);
        header.setFont(JStudioTheme.getUIFont(12).deriveFont(Font.BOLD));

        resultsTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                ResultRow result = tableModel.getResultAt(row);
                boolean isChildRow = result != null && result.isChild();
                setBackground(isSelected ? selection : bgSurface);
                if (isChildRow) {
                    setForeground(isSelected ? textPrimary : textSecondary);
                    setFont(getFont().deriveFont(Font.PLAIN));
                } else {
                    setForeground(isSelected ? textPrimary : accent);
                    setFont(getFont().deriveFont(result != null && result.hasChildren() ? Font.BOLD : Font.PLAIN));
                }
                return this;
            }
        });

        DefaultTableCellRenderer defaultRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setBackground(isSelected ? selection : bgSurface);
                setForeground(isSelected ? textPrimary : textSecondary);
                return this;
            }
        };
        resultsTable.getColumnModel().getColumn(1).setCellRenderer(defaultRenderer);
        resultsTable.getColumnModel().getColumn(2).setCellRenderer(defaultRenderer);

        statusLabel.setForeground(textSecondary);
        statusLabel.setFont(JStudioTheme.getUIFont(12));

        applyThemeToPanel((JPanel) getContentPane(), bgPrimary, textPrimary, border);
    }

    private void applyThemeToPanel(JPanel panel, Color bg, Color fg, Color borderColor) {
        panel.setBackground(bg);

        if (panel.getBorder() instanceof TitledBorder) {
            TitledBorder tb = (TitledBorder) panel.getBorder();
            tb.setTitleColor(fg);
            panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(borderColor),
                tb.getTitle(),
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                JStudioTheme.getUIFont(12).deriveFont(Font.BOLD),
                fg
            ));
        }

        for (Component c : panel.getComponents()) {
            if (c instanceof JPanel) {
                JPanel jp = (JPanel) c;
                applyThemeToPanel(jp, bg, fg, borderColor);
            } else if (c instanceof JLabel) {
                JLabel label = (JLabel) c;
                label.setForeground(fg);
                label.setFont(JStudioTheme.getUIFont(12));
            } else if (c instanceof JButton) {
                JButton button = (JButton) c;
                button.setBackground(JStudioTheme.getBgSecondary());
                button.setForeground(fg);
                button.setFont(JStudioTheme.getUIFont(12));
                button.setFocusPainted(false);
                button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(borderColor),
                    BorderFactory.createEmptyBorder(4, 12, 4, 12)
                ));
            } else if (c instanceof JSpinner) {
                JSpinner spinner = (JSpinner) c;
                spinner.setBackground(JStudioTheme.getBgSurface());
                spinner.setForeground(fg);
                spinner.setFont(JStudioTheme.getUIFont(12));
                JComponent editor = spinner.getEditor();
                if (editor instanceof JSpinner.DefaultEditor) {
                    JSpinner.DefaultEditor de = (JSpinner.DefaultEditor) editor;
                    de.getTextField().setBackground(JStudioTheme.getBgSurface());
                    de.getTextField().setForeground(fg);
                    de.getTextField().setCaretColor(fg);
                }
            } else if (c instanceof JScrollPane) {
                JScrollPane sp = (JScrollPane) c;
                sp.setBackground(bg);
                sp.getViewport().setBackground(JStudioTheme.getBgSurface());
                sp.setBorder(BorderFactory.createLineBorder(borderColor));
            } else if (c instanceof JProgressBar) {
                JProgressBar pb = (JProgressBar) c;
                pb.setBackground(JStudioTheme.getBgSecondary());
                pb.setForeground(JStudioTheme.getAccent());
                pb.setBorder(BorderFactory.createLineBorder(borderColor));
            }
        }
    }

    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Query Input"));

        inputTabbedPane = new JTabbedPane();

        JPanel dslPanel = new JPanel(new BorderLayout());
        queryInput = new JTextArea(6, 60);
        queryInput.setText("FIND methods WHERE calls(\"java/io/PrintStream.println\")");
        queryInput.setTabSize(4);
        JScrollPane dslScrollPane = new JScrollPane(queryInput);
        dslPanel.add(dslScrollPane, BorderLayout.CENTER);
        inputTabbedPane.addTab("DSL", dslPanel);

        builderPanel = new QueryBuilderPanel();
        builderPanel.setQueryChangeListener(query -> {
            queryInput.setText(query);
        });
        inputTabbedPane.addTab("Builder", builderPanel);

        inputTabbedPane.addChangeListener(e -> {
            if (inputTabbedPane.getSelectedIndex() == 1) {
                builderPanel.applyTheme();
            }
        });

        panel.add(inputTabbedPane, BorderLayout.CENTER);

        JPanel configPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        configPanel.add(new JLabel("Max methods:"));
        maxMethodsSpinner = new JSpinner(new SpinnerNumberModel(300, 1, 1000, 10));
        configPanel.add(maxMethodsSpinner);

        configPanel.add(new JLabel("Seeds/method:"));
        seedsSpinner = new JSpinner(new SpinnerNumberModel(20, 1, 50, 1));
        configPanel.add(seedsSpinner);

        configPanel.add(new JLabel("Time budget (s):"));
        timeBudgetSpinner = new JSpinner(new SpinnerNumberModel(30, 5, 300, 10));
        configPanel.add(timeBudgetSpinner);

        configPanel.add(Box.createHorizontalStrut(20));

        runButton = new JButton("Run Query");
        runButton.setMnemonic(KeyEvent.VK_R);
        runButton.addActionListener(e -> runQuery());
        configPanel.add(runButton);

        stopButton = new JButton("Stop");
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopQuery());
        configPanel.add(stopButton);

        progressBar = new JProgressBar();
        progressBar.setPreferredSize(new Dimension(150, 22));
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        configPanel.add(progressBar);

        panel.add(configPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Results"));

        tableModel = new TreeResultTableModel();
        resultsTable = new JTable(tableModel);
        resultsTable.setRowHeight(24);
        resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        resultsTable.getColumnModel().getColumn(0).setPreferredWidth(450);
        resultsTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        resultsTable.getColumnModel().getColumn(2).setPreferredWidth(100);

        resultsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = resultsTable.rowAtPoint(e.getPoint());
                int col = resultsTable.columnAtPoint(e.getPoint());

                if (row >= 0 && col == 0) {
                    ResultRow result = tableModel.getResultAt(row);
                    if (result != null && result.hasChildren() && !result.isChild()) {
                        tableModel.toggleExpand(row);
                        return;
                    }
                }

                if (e.getClickCount() == 2) {
                    navigateToSelectedResult();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(resultsTable);
        scrollPane.setPreferredSize(new Dimension(850, 350));
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("Enter a query and click Run");
        statusPanel.add(statusLabel);
        panel.add(statusPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton helpButton = new JButton("Help");
        helpButton.addActionListener(e -> showHelp());
        panel.add(helpButton);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        panel.add(closeButton);

        return panel;
    }

    private void setupKeyboardShortcuts() {
        getRootPane().registerKeyboardAction(
            e -> runQuery(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        getRootPane().registerKeyboardAction(
            e -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    private void runQuery() {
        if (running) return;

        String queryText = queryInput.getText().trim();
        if (queryText.isEmpty()) {
            statusLabel.setText("Please enter a query");
            return;
        }

        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project != null) {
            queryService.setUserClassNames(project.getUserClassNames());

            if (project.getXrefDatabase() == null) {
                buildXrefsAndRun(project, queryText);
                return;
            }
        }

        executeQuery(queryText);
    }

    private void buildXrefsAndRun(ProjectModel project, String queryText) {
        running = true;
        runButton.setEnabled(false);
        stopButton.setEnabled(true);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        statusLabel.setText("Building cross-reference database...");
        tableModel.clear();

        SwingWorker<XrefDatabase, String> worker = new SwingWorker<>() {
            @Override
            protected XrefDatabase doInBackground() {
                XrefBuilder builder = new XrefBuilder(project.getClassPool());
                builder.setProgressCallback(this::publish);
                return builder.build();
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    statusLabel.setText("Building xrefs: " + chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                try {
                    XrefDatabase xrefDb = get();
                    project.setXrefDatabase(xrefDb);
                    progressBar.setIndeterminate(false);
                    executeQuery(queryText);
                } catch (Exception e) {
                    running = false;
                    runButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    progressBar.setVisible(false);
                    statusLabel.setText("Error building xrefs: " + e.getMessage());
                    statusLabel.setForeground(JStudioTheme.getError());
                }
            }
        };

        worker.execute();
    }

    private void executeQuery(String queryText) {
        running = true;
        runButton.setEnabled(false);
        stopButton.setEnabled(true);
        progressBar.setVisible(true);
        progressBar.setValue(0);
        progressBar.setIndeterminate(false);
        statusLabel.setText("Parsing query...");
        tableModel.clear();

        QueryService.QueryConfig config = QueryService.QueryConfig.builder()
            .maxMethods((Integer) maxMethodsSpinner.getValue())
            .seedsPerMethod((Integer) seedsSpinner.getValue())
            .timeBudgetMs((Integer) timeBudgetSpinner.getValue() * 1000L)
            .build();

        queryService.executeAsync(queryText, config, new QueryBatchRunner.ProgressListener() {
            @Override
            public void onPhaseStart(String phase, int total) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(phase + " (" + total + " items)...");
                    progressBar.setValue(0);
                    progressBar.setMaximum(total);
                });
            }

            @Override
            public void onProgress(int current, int total, String message) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(current);
                    statusLabel.setText(message);
                });
            }

            @Override
            public void onComplete(int matchCount) {
            }
        }).thenAccept(result -> {
            SwingUtilities.invokeLater(() -> {
                running = false;
                runButton.setEnabled(true);
                stopButton.setEnabled(false);
                progressBar.setVisible(false);

                if (result.hasError()) {
                    statusLabel.setText("Error: " + result.error());
                    statusLabel.setForeground(JStudioTheme.getError());
                } else {
                    tableModel.setResults(result.results());
                    int methodCount = result.resultCount();
                    int siteCount = result.results().stream()
                        .mapToInt(r -> r.hasChildren() ? r.getChildren().size() : 0)
                        .sum();
                    String statusText = siteCount > 0
                        ? "Found " + methodCount + " methods with " + siteCount + " call sites in " +
                          result.executionTimeMs() + "ms (click [+] to expand, click status for details)"
                        : "Found " + methodCount + " matches in " + result.executionTimeMs() + "ms (click status for details)";
                    statusLabel.setText(statusText);
                    statusLabel.setForeground(JStudioTheme.getSuccess());

                    String diag = XrefMethodFilter.getLastDiagnostics();
                    if (!diag.isEmpty()) {
                        statusLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
                        statusLabel.setToolTipText("Click to see filter diagnostics");
                        for (java.awt.event.MouseListener ml : statusLabel.getMouseListeners()) {
                            statusLabel.removeMouseListener(ml);
                        }
                        statusLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                            @Override
                            public void mouseClicked(java.awt.event.MouseEvent e) {
                                JTextArea textArea = new JTextArea(diag);
                                textArea.setEditable(false);
                                textArea.setFont(JStudioTheme.getCodeFont(12));
                                JScrollPane scroll = new JScrollPane(textArea);
                                scroll.setPreferredSize(new Dimension(600, 400));
                                JOptionPane.showMessageDialog(QueryDialog.this, scroll,
                                    "Query Filter Diagnostics", JOptionPane.INFORMATION_MESSAGE);
                            }
                        });
                    }
                }
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                running = false;
                runButton.setEnabled(true);
                stopButton.setEnabled(false);
                progressBar.setVisible(false);
                statusLabel.setText("Error: " + ex.getMessage());
                statusLabel.setForeground(JStudioTheme.getError());
            });
            return null;
        });
    }

    private void stopQuery() {
        queryService.cancel();
        statusLabel.setText("Stopping...");
        statusLabel.setForeground(JStudioTheme.getWarning());
    }

    private void navigateToSelectedResult() {
        int row = resultsTable.getSelectedRow();
        if (row < 0) return;

        ResultRow result = tableModel.getResultAt(row);
        if (result == null) return;

        ClickTarget target = result.getPrimaryTarget();
        if (target == null) {
            statusLabel.setText("No navigation target for: " + result.getPrimaryLabel());
            statusLabel.setForeground(JStudioTheme.getWarning());
            return;
        }

        if (mainFrame != null) {
            boolean success = mainFrame.navigateToTarget(target);
            if (success) {
                statusLabel.setText("Navigated to: " + result.getPrimaryLabel());
                statusLabel.setForeground(JStudioTheme.getSuccess());
            } else {
                statusLabel.setText("Failed to navigate to: " + result.getPrimaryLabel());
                statusLabel.setForeground(JStudioTheme.getError());
            }
        } else {
            statusLabel.setText("Navigate to: " + result.getPrimaryLabel());
            statusLabel.setForeground(JStudioTheme.getInfo());
        }
    }

    private void showHelp() {
        String help = "Query DSL Help\n\n" +
            "Basic syntax:\n" +
            "  FIND <target> [IN <scope>] [WHERE <predicate>] [LIMIT n]\n\n" +
            "Targets:\n" +
            "  methods, classes, events, strings, objects\n\n" +
            "Scopes:\n" +
            "  IN all                    - search all loaded classes\n" +
            "  IN class \"pattern\"        - classes matching pattern\n" +
            "  IN method \"pattern\"       - methods matching pattern\n" +
            "  DURING <clinit>           - during static initialization\n\n" +
            "Predicates:\n" +
            "  calls(\"owner.method\")     - methods that call a specific method\n" +
            "  allocCount(\"type\") > N    - allocation count threshold\n" +
            "  containsString(\"str\")     - methods with string constant\n" +
            "  writesField(\"owner.name\") - methods that write to a field\n" +
            "  readsField(\"owner.name\")  - methods that read a field (use \"*\" for any)\n" +
            "  throws(\"type\")            - methods that throw an exception\n\n" +
            "Combining predicates:\n" +
            "  pred1 AND pred2\n" +
            "  pred1 OR pred2\n" +
            "  NOT pred\n\n" +
            "Examples:\n" +
            "  FIND methods WHERE calls(\"java/io/PrintStream.println\")\n" +
            "  FIND methods WHERE allocCount(\"java/lang/StringBuilder\") > 5\n" +
            "  FIND methods IN class \"com/example/.*\" WHERE containsString(\"password\")\n" +
            "  FIND methods WHERE calls(\"PrintStream.println\") AND readsField(\"*\")\n" +
            "  FIND methods WHERE throws(\"java/lang/NullPointerException\") LIMIT 20\n";

        JTextArea textArea = new JTextArea(help);
        textArea.setFont(JStudioTheme.getCodeFont(12));
        textArea.setBackground(JStudioTheme.getBgSurface());
        textArea.setForeground(JStudioTheme.getTextPrimary());
        textArea.setCaretColor(JStudioTheme.getTextPrimary());
        textArea.setEditable(false);

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(580, 480));
        scrollPane.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));

        JOptionPane.showMessageDialog(this, scrollPane, "Query DSL Help",
                JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void dispose() {
        queryService.shutdown();
        super.dispose();
    }

    private static class TreeResultTableModel extends AbstractTableModel {
        private List<ResultRow> rootResults = new ArrayList<>();
        private final List<ResultRow> flatRows = new ArrayList<>();
        private final Set<ResultRow> expandedRows = new HashSet<>();

        public void setResults(List<ResultRow> results) {
            this.rootResults = results != null ? new ArrayList<>(results) : new ArrayList<>();
            this.expandedRows.clear();
            rebuildFlatList();
        }

        public void clear() {
            rootResults.clear();
            expandedRows.clear();
            flatRows.clear();
            fireTableDataChanged();
        }

        private void rebuildFlatList() {
            flatRows.clear();
            for (ResultRow root : rootResults) {
                flatRows.add(root);
                if (expandedRows.contains(root) && root.hasChildren()) {
                    flatRows.addAll(root.getChildren());
                }
            }
            fireTableDataChanged();
        }

        public void toggleExpand(int row) {
            if (row < 0 || row >= flatRows.size()) return;
            ResultRow result = flatRows.get(row);
            if (result.isChild() || !result.hasChildren()) return;

            if (expandedRows.contains(result)) {
                expandedRows.remove(result);
            } else {
                expandedRows.add(result);
            }
            rebuildFlatList();
        }

        public boolean isExpanded(ResultRow row) {
            return expandedRows.contains(row);
        }

        public ResultRow getResultAt(int row) {
            if (row >= 0 && row < flatRows.size()) {
                return flatRows.get(row);
            }
            return null;
        }

        @Override
        public int getRowCount() {
            return flatRows.size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public String getColumnName(int column) {
            switch (column) {
                case 0: return "Match";
                case 1: return "Details";
                case 2: return "Sites";
                default: return "";
            }
        }

        @Override
        public Object getValueAt(int row, int column) {
            ResultRow result = flatRows.get(row);
            switch (column) {
                case 0:
                    String prefix = "";
                    if (result.isChild()) {
                        prefix = "    ";
                    } else if (result.hasChildren()) {
                        prefix = isExpanded(result) ? "[-] " : "[+] ";
                    }
                    return prefix + result.getPrimaryLabel();
                case 1:
                    return formatColumns(result.getColumns());
                case 2:
                    if (result.isChild()) {
                        Object pc = result.getColumn("pc");
                        return pc != null ? "pc=" + pc : "";
                    }
                    return result.hasChildren() ? result.getChildren().size() + " sites" : formatEvidence(result.getEvidence());
                default:
                    return "";
            }
        }

        private String formatColumns(Map<String, Object> columns) {
            if (columns == null || columns.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Object> entry : columns.entrySet()) {
                String key = entry.getKey();
                if (key.equals("class") || key.equals("method") || key.equals("sites") || key.equals("pc")) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append(key).append("=").append(entry.getValue());
            }
            return sb.toString();
        }

        private String formatEvidence(List<?> evidence) {
            if (evidence == null || evidence.isEmpty()) return "";
            if (evidence.size() == 1) {
                return evidence.get(0).toString();
            }
            return evidence.size() + " events";
        }
    }
}
