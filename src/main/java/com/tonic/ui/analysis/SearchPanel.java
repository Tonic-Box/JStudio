package com.tonic.ui.analysis;

import com.tonic.analysis.pattern.PatternSearch;
import com.tonic.analysis.pattern.Patterns;
import com.tonic.analysis.pattern.SearchResult;
import com.tonic.parser.ClassFile;
import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.ClassSelectedEvent;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Pattern search panel for finding code patterns.
 */
public class SearchPanel extends JPanel {

    private final ProjectModel project;
    private final JTextField searchField;
    private final JComboBox<String> searchTypeCombo;
    private final DefaultListModel<String> resultsModel;
    private final JList<String> resultsList;
    private final JLabel statusLabel;

    private List<SearchResult> lastResults;

    public SearchPanel(ProjectModel project) {
        this.project = project;

        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgSecondary());

        // Search controls
        JPanel controlPanel = new JPanel(new GridBagLayout());
        controlPanel.setBackground(JStudioTheme.getBgSecondary());
        controlPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Search type
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        JLabel typeLabel = new JLabel("Search:");
        typeLabel.setForeground(JStudioTheme.getTextPrimary());
        controlPanel.add(typeLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.3;
        searchTypeCombo = new JComboBox<>(new String[]{
                "Method Calls",
                "Field Accesses",
                "Class Allocations",
                "Type Casts",
                "Instanceof Checks",
                "Null Checks",
                "Throws"
        });
        searchTypeCombo.setBackground(JStudioTheme.getBgTertiary());
        searchTypeCombo.setForeground(JStudioTheme.getTextPrimary());
        controlPanel.add(searchTypeCombo, gbc);

        // Pattern field
        gbc.gridx = 2;
        gbc.weightx = 0;
        JLabel patternLabel = new JLabel("Pattern:");
        patternLabel.setForeground(JStudioTheme.getTextPrimary());
        controlPanel.add(patternLabel, gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.7;
        searchField = new JTextField(30);
        searchField.setBackground(JStudioTheme.getBgTertiary());
        searchField.setForeground(JStudioTheme.getTextPrimary());
        searchField.setCaretColor(JStudioTheme.getTextPrimary());
        searchField.addActionListener(e -> search());
        controlPanel.add(searchField, gbc);

        // Search button
        gbc.gridx = 4;
        gbc.weightx = 0;
        JButton searchButton = new JButton("Search");
        searchButton.setBackground(JStudioTheme.getBgTertiary());
        searchButton.setForeground(JStudioTheme.getTextPrimary());
        searchButton.addActionListener(e -> search());
        controlPanel.add(searchButton, gbc);

        add(controlPanel, BorderLayout.NORTH);

        // Results list
        resultsModel = new DefaultListModel<>();
        resultsList = new JList<>(resultsModel);
        resultsList.setBackground(JStudioTheme.getBgTertiary());
        resultsList.setForeground(JStudioTheme.getTextPrimary());
        resultsList.setSelectionBackground(JStudioTheme.getSelection());
        resultsList.setFont(JStudioTheme.getCodeFont(11));

        // Double-click to navigate to result
        resultsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateToSelectedResult();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(resultsList);
        scrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, JStudioTheme.getBorder()));
        add(scrollPane, BorderLayout.CENTER);

        // Status bar
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBackground(JStudioTheme.getBgSecondary());
        statusLabel = new JLabel("Enter a search pattern and click Search.");
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        statusPanel.add(statusLabel);
        add(statusPanel, BorderLayout.SOUTH);
    }

    /**
     * Perform search.
     */
    public void search() {
        if (project.getClassPool() == null) {
            statusLabel.setText("No project loaded. Open a JAR or class file first.");
            return;
        }

        String pattern = searchField.getText().trim();
        String searchType = (String) searchTypeCombo.getSelectedItem();

        statusLabel.setText("Searching...");
        resultsModel.clear();

        SwingWorker<List<SearchResult>, Void> worker = new SwingWorker<List<SearchResult>, Void>() {
            @Override
            protected List<SearchResult> doInBackground() throws Exception {
                PatternSearch search = new PatternSearch(project.getClassPool())
                        .inAllClasses()
                        .limit(100);

                switch (searchType) {
                    case "Method Calls":
                        if (pattern.isEmpty()) {
                            return search.findMethodCalls(Patterns.anyMethodCall());
                        } else if (pattern.contains(".")) {
                            String[] parts = pattern.split("\\.", 2);
                            return search.findMethodCalls(parts[0], parts[1]);
                        } else {
                            return search.findMethodCalls(pattern);
                        }

                    case "Field Accesses":
                        if (pattern.isEmpty()) {
                            return search.findFieldsByName(".*");
                        } else {
                            return search.findFieldsByName(pattern);
                        }

                    case "Class Allocations":
                        if (pattern.isEmpty()) {
                            return search.findAllocations();
                        } else {
                            return search.findAllocations(pattern);
                        }

                    case "Type Casts":
                        if (pattern.isEmpty()) {
                            return search.findCasts();
                        } else {
                            return search.findCastsTo(pattern);
                        }

                    case "Instanceof Checks":
                        if (pattern.isEmpty()) {
                            return search.findInstanceOfChecks();
                        } else {
                            return search.findInstanceOfChecks(pattern);
                        }

                    case "Null Checks":
                        return search.findNullChecks();

                    case "Throws":
                        return search.findThrows();

                    default:
                        return java.util.Collections.emptyList();
                }
            }

            @Override
            protected void done() {
                try {
                    lastResults = get();
                    displayResults(lastResults);
                    statusLabel.setText("Found " + lastResults.size() + " results");
                } catch (Exception e) {
                    statusLabel.setText("Search failed: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    private void displayResults(List<SearchResult> results) {
        resultsModel.clear();
        for (SearchResult result : results) {
            String location = "";
            if (result.getClassFile() != null) {
                location = formatClassName(result.getClassFile().getClassName());
            }
            if (result.getMethod() != null) {
                location += "." + result.getMethod().getName();
            }
            String description = result.getDescription() != null ? result.getDescription() : "";
            resultsModel.addElement(location + " - " + description);
        }
    }

    private String formatClassName(String className) {
        if (className == null) return "?";
        int lastSlash = className.lastIndexOf('/');
        if (lastSlash >= 0) {
            return className.substring(lastSlash + 1);
        }
        int lastDot = className.lastIndexOf('.');
        if (lastDot >= 0) {
            return className.substring(lastDot + 1);
        }
        return className;
    }

    /**
     * Refresh the panel.
     */
    public void refresh() {
        // Nothing to refresh
    }

    /**
     * Get the selected search result.
     */
    public SearchResult getSelectedResult() {
        int index = resultsList.getSelectedIndex();
        if (index >= 0 && lastResults != null && index < lastResults.size()) {
            return lastResults.get(index);
        }
        return null;
    }

    /**
     * Set focus to the search field.
     */
    public void focusSearch() {
        searchField.requestFocus();
        searchField.selectAll();
    }

    /**
     * Navigate to the selected search result.
     */
    private void navigateToSelectedResult() {
        SearchResult result = getSelectedResult();
        if (result == null) return;

        ClassFile classFile = result.getClassFile();
        if (classFile == null) return;

        // Find the class entry in the project
        String className = classFile.getClassName();
        for (ClassEntryModel classEntry : project.getUserClasses()) {
            if (classEntry.getClassName().equals(className)) {
                // Fire event to navigate to class
                EventBus.getInstance().post(new ClassSelectedEvent(this, classEntry));
                statusLabel.setText("Navigated to: " + className);
                return;
            }
        }

        statusLabel.setText("Class not found in project: " + className);
    }
}
