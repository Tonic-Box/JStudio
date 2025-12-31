package com.tonic.ui.dialog;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.ClassSelectedEvent;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Dialog for searching text across all decompiled source files.
 */
public class FindInFilesDialog extends JDialog {

    private final ProjectModel project;
    private final JTextField searchField;
    private final JCheckBox caseSensitiveBox;
    private final JCheckBox regexBox;
    private final JCheckBox wholeWordBox;
    private final JTable resultsTable;
    private final ResultsTableModel tableModel;
    private final JLabel statusLabel;
    private final JButton searchButton;

    private List<SearchMatch> allMatches = new ArrayList<>();
    private SwingWorker<List<SearchMatch>, SearchMatch> currentWorker;

    public FindInFilesDialog(Frame owner, ProjectModel project) {
        super(owner, "Find in Files", false);
        this.project = project;

        setLayout(new BorderLayout());
        getContentPane().setBackground(JStudioTheme.getBgSecondary());

        // Search panel
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        searchPanel.setBackground(JStudioTheme.getBgSecondary());

        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setForeground(JStudioTheme.getTextPrimary());
        searchPanel.add(searchLabel);

        searchField = new JTextField(30);
        searchField.setBackground(JStudioTheme.getBgTertiary());
        searchField.setForeground(JStudioTheme.getTextPrimary());
        searchField.setCaretColor(JStudioTheme.getTextPrimary());
        searchField.addActionListener(e -> performSearch());
        searchPanel.add(searchField);

        searchButton = new JButton("Search");
        searchButton.setBackground(JStudioTheme.getBgTertiary());
        searchButton.setForeground(JStudioTheme.getTextPrimary());
        searchButton.addActionListener(e -> performSearch());
        searchPanel.add(searchButton);

        // Options panel
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        optionsPanel.setBackground(JStudioTheme.getBgSecondary());

        caseSensitiveBox = new JCheckBox("Case sensitive");
        caseSensitiveBox.setBackground(JStudioTheme.getBgSecondary());
        caseSensitiveBox.setForeground(JStudioTheme.getTextPrimary());
        optionsPanel.add(caseSensitiveBox);

        wholeWordBox = new JCheckBox("Whole word");
        wholeWordBox.setBackground(JStudioTheme.getBgSecondary());
        wholeWordBox.setForeground(JStudioTheme.getTextPrimary());
        optionsPanel.add(wholeWordBox);

        regexBox = new JCheckBox("Regex");
        regexBox.setBackground(JStudioTheme.getBgSecondary());
        regexBox.setForeground(JStudioTheme.getTextPrimary());
        optionsPanel.add(regexBox);

        // Top panel combining search and options
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(JStudioTheme.getBgSecondary());
        topPanel.add(searchPanel, BorderLayout.NORTH);
        topPanel.add(optionsPanel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        // Results table
        tableModel = new ResultsTableModel();
        resultsTable = new JTable(tableModel);
        TableRowSorter<ResultsTableModel> sorter = new TableRowSorter<>(tableModel);
        resultsTable.setRowSorter(sorter);

        resultsTable.setBackground(JStudioTheme.getBgTertiary());
        resultsTable.setForeground(JStudioTheme.getTextPrimary());
        resultsTable.setSelectionBackground(JStudioTheme.getSelection());
        resultsTable.setSelectionForeground(JStudioTheme.getTextPrimary());
        resultsTable.setGridColor(JStudioTheme.getBorder());
        resultsTable.setFont(JStudioTheme.getCodeFont(11));
        resultsTable.setRowHeight(20);
        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Column widths
        resultsTable.getColumnModel().getColumn(0).setPreferredWidth(200); // Class
        resultsTable.getColumnModel().getColumn(1).setPreferredWidth(50);  // Line
        resultsTable.getColumnModel().getColumn(2).setPreferredWidth(400); // Match

        // Header styling
        resultsTable.getTableHeader().setBackground(JStudioTheme.getBgSecondary());
        resultsTable.getTableHeader().setForeground(JStudioTheme.getTextPrimary());
        resultsTable.getTableHeader().setFont(JStudioTheme.getUIFont(11));

        // Double-click to navigate
        resultsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateToSelectedMatch();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(resultsTable);
        scrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, JStudioTheme.getBorder()));
        scrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());
        add(scrollPane, BorderLayout.CENTER);

        // Bottom panel
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(JStudioTheme.getBgSecondary());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        statusLabel = new JLabel("Enter search text and click Search.");
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        bottomPanel.add(statusLabel, BorderLayout.WEST);

        JButton closeButton = new JButton("Close");
        closeButton.setBackground(JStudioTheme.getBgTertiary());
        closeButton.setForeground(JStudioTheme.getTextPrimary());
        closeButton.addActionListener(e -> dispose());
        bottomPanel.add(closeButton, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);

        // Dialog settings
        setSize(700, 500);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    /**
     * Perform the search.
     */
    private void performSearch() {
        String searchText = searchField.getText();
        if (searchText.isEmpty()) {
            statusLabel.setText("Enter search text.");
            return;
        }

        if (project.getClassPool() == null) {
            statusLabel.setText("No project loaded.");
            return;
        }

        // Cancel any running search
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
        }

        searchButton.setEnabled(false);
        statusLabel.setText("Searching...");
        allMatches.clear();
        tableModel.setMatches(allMatches);

        // Build pattern
        Pattern pattern;
        try {
            pattern = buildPattern(searchText);
        } catch (PatternSyntaxException e) {
            statusLabel.setText("Invalid regex: " + e.getMessage());
            searchButton.setEnabled(true);
            return;
        }

        currentWorker = new SwingWorker<>() {
            @Override
            protected List<SearchMatch> doInBackground() {
                List<SearchMatch> matches = new ArrayList<>();
                List<ClassEntryModel> classes = project.getAllClasses();
                int processed = 0;

                for (ClassEntryModel classEntry : classes) {
                    if (isCancelled()) break;

                    try {
                        String source = classEntry.getDecompilationCache();
                        if (source == null) {
                            ClassDecompiler decompiler = new ClassDecompiler(classEntry.getClassFile());
                            source = decompiler.decompile();
                            classEntry.setDecompilationCache(source);
                        }

                        // Search for matches
                        String[] lines = source.split("\n");
                        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
                            String line = lines[lineNum];
                            Matcher matcher = pattern.matcher(line);
                            if (matcher.find()) {
                                SearchMatch match = new SearchMatch(
                                        classEntry,
                                        lineNum + 1,
                                        line.trim()
                                );
                                matches.add(match);
                                publish(match);
                            }
                        }
                    } catch (Exception e) {
                        // Skip classes that fail to decompile
                    }

                    processed++;
                    if (processed % 10 == 0) {
                        setProgress((processed * 100) / classes.size());
                    }
                }

                return matches;
            }

            @Override
            protected void process(List<SearchMatch> chunks) {
                allMatches.addAll(chunks);
                tableModel.setMatches(allMatches);
                statusLabel.setText("Found " + allMatches.size() + " matches...");
            }

            @Override
            protected void done() {
                try {
                    if (!isCancelled()) {
                        allMatches = get();
                        tableModel.setMatches(allMatches);
                        statusLabel.setText("Found " + allMatches.size() + " matches. Double-click to navigate.");
                    }
                } catch (Exception e) {
                    statusLabel.setText("Search error: " + e.getMessage());
                }
                searchButton.setEnabled(true);
            }
        };

        currentWorker.execute();
    }

    private Pattern buildPattern(String searchText) {
        String patternText = searchText;

        if (!regexBox.isSelected()) {
            // Escape regex special characters
            patternText = Pattern.quote(patternText);
        }

        if (wholeWordBox.isSelected()) {
            patternText = "\\b" + patternText + "\\b";
        }

        int flags = caseSensitiveBox.isSelected() ? 0 : Pattern.CASE_INSENSITIVE;
        return Pattern.compile(patternText, flags);
    }

    private void navigateToSelectedMatch() {
        int viewRow = resultsTable.getSelectedRow();
        if (viewRow < 0) return;

        int modelRow = resultsTable.convertRowIndexToModel(viewRow);
        SearchMatch match = tableModel.getMatchAt(modelRow);
        if (match != null && match.classEntry != null) {
            EventBus.getInstance().post(new ClassSelectedEvent(this, match.classEntry));
            // Could also navigate to specific line if we had that capability
        }
    }

    /**
     * Show the dialog and focus the search field.
     */
    public void showDialog() {
        searchField.requestFocus();
        setVisible(true);
    }

    /**
     * Show with pre-filled search text.
     */
    public void showDialog(String searchText) {
        searchField.setText(searchText);
        searchField.selectAll();
        showDialog();
    }

    // Data classes
    private static class SearchMatch {
        final ClassEntryModel classEntry;
        final int lineNumber;
        final String lineText;

        SearchMatch(ClassEntryModel classEntry, int lineNumber, String lineText) {
            this.classEntry = classEntry;
            this.lineNumber = lineNumber;
            this.lineText = lineText;
        }
    }

    // Table model
    private static class ResultsTableModel extends AbstractTableModel {
        private final String[] COLUMNS = {"Class", "Line", "Match"};
        private List<SearchMatch> matches = new ArrayList<>();

        void setMatches(List<SearchMatch> matches) {
            this.matches = new ArrayList<>(matches);
            fireTableDataChanged();
        }

        SearchMatch getMatchAt(int row) {
            if (row >= 0 && row < matches.size()) {
                return matches.get(row);
            }
            return null;
        }

        @Override
        public int getRowCount() {
            return matches.size();
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
        public Object getValueAt(int rowIndex, int columnIndex) {
            SearchMatch match = matches.get(rowIndex);
            switch (columnIndex) {
                case 0: return match.classEntry.getClassName().replace('/', '.');
                case 1: return match.lineNumber;
                case 2: return match.lineText;
                default: return "";
            }
        }
    }
}
