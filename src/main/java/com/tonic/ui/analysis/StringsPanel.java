package com.tonic.ui.analysis;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.StringRefItem;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.ColumnWidths;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.ClassSelectedEvent;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel showing all strings from constant pools across all loaded classes.
 */
public class StringsPanel extends ThemedJPanel {

    private final ProjectModel project;
    private final JTextField filterField;
    private final JTable stringsTable;
    private final StringsTableModel tableModel;
    private final TableRowSorter<StringsTableModel> sorter;
    private final JLabel statusLabel;
    private final JButton refreshButton;
    private final JPanel toolbar;
    private final JScrollPane scrollPane;
    private final JPanel statusPanel;

    private List<StringEntry> allStrings = new ArrayList<>();

    public StringsPanel(ProjectModel project) {
        super(BackgroundStyle.SECONDARY, new BorderLayout());
        this.project = project;

        toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_SMALL));
        toolbar.setBackground(JStudioTheme.getBgSecondary());

        JLabel filterLabel = new JLabel("Filter:");
        filterLabel.setForeground(JStudioTheme.getTextPrimary());
        toolbar.add(filterLabel);

        filterField = new JTextField(25);
        filterField.setBackground(JStudioTheme.getBgTertiary());
        filterField.setForeground(JStudioTheme.getTextPrimary());
        filterField.setCaretColor(JStudioTheme.getTextPrimary());
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        toolbar.add(filterField);

        refreshButton = new JButton("Refresh");
        refreshButton.setBackground(JStudioTheme.getBgTertiary());
        refreshButton.setForeground(JStudioTheme.getTextPrimary());
        refreshButton.addActionListener(e -> extractStrings());
        toolbar.add(refreshButton);

        add(toolbar, BorderLayout.NORTH);

        tableModel = new StringsTableModel();
        stringsTable = new JTable(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        stringsTable.setRowSorter(sorter);

        stringsTable.setBackground(JStudioTheme.getBgTertiary());
        stringsTable.setForeground(JStudioTheme.getTextPrimary());
        stringsTable.setSelectionBackground(JStudioTheme.getSelection());
        stringsTable.setSelectionForeground(JStudioTheme.getTextPrimary());
        stringsTable.setGridColor(JStudioTheme.getBorder());
        stringsTable.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE));
        stringsTable.setRowHeight(UIConstants.TABLE_ROW_HEIGHT);
        stringsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        stringsTable.getColumnModel().getColumn(0).setPreferredWidth(ColumnWidths.STRING_VALUE);
        stringsTable.getColumnModel().getColumn(1).setPreferredWidth(ColumnWidths.CLASS_NAME);

        stringsTable.getTableHeader().setBackground(JStudioTheme.getBgSecondary());
        stringsTable.getTableHeader().setForeground(JStudioTheme.getTextPrimary());
        stringsTable.getTableHeader().setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));

        stringsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateToSelectedString();
                }
            }
        });

        scrollPane = new JScrollPane(stringsTable);
        scrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, JStudioTheme.getBorder()));
        scrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());
        add(scrollPane, BorderLayout.CENTER);

        statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBackground(JStudioTheme.getBgSecondary());
        statusLabel = new JLabel("Click Refresh to extract strings from all classes.");
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        statusPanel.add(statusLabel);
        add(statusPanel, BorderLayout.SOUTH);
    }

    @Override
    protected void applyChildThemes() {
        toolbar.setBackground(JStudioTheme.getBgSecondary());
        filterField.setBackground(JStudioTheme.getBgTertiary());
        filterField.setForeground(JStudioTheme.getTextPrimary());
        refreshButton.setBackground(JStudioTheme.getBgTertiary());
        refreshButton.setForeground(JStudioTheme.getTextPrimary());
        stringsTable.setBackground(JStudioTheme.getBgTertiary());
        stringsTable.setForeground(JStudioTheme.getTextPrimary());
        stringsTable.setSelectionBackground(JStudioTheme.getSelection());
        stringsTable.setGridColor(JStudioTheme.getBorder());
        stringsTable.getTableHeader().setBackground(JStudioTheme.getBgSecondary());
        stringsTable.getTableHeader().setForeground(JStudioTheme.getTextPrimary());
        scrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());
        statusPanel.setBackground(JStudioTheme.getBgSecondary());
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
    }

    /**
     * Extract strings from all loaded classes.
     */
    public void extractStrings() {
        if (project.getClassPool() == null) {
            statusLabel.setText("No project loaded.");
            return;
        }

        refreshButton.setEnabled(false);
        statusLabel.setText("Extracting strings...");

        SwingWorker<List<StringEntry>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<StringEntry> doInBackground() {
                List<StringEntry> strings = new ArrayList<>();

                for (ClassEntryModel classEntry : project.getUserClasses()) {
                    ClassFile cf = classEntry.getClassFile();
                    ConstPool constPool = cf.getConstPool();
                    List<Item<?>> items = constPool.getItems();

                    for (int i = 1; i < items.size(); i++) {
                        try {
                            Item<?> item = items.get(i);
                            // Look for CONSTANT_String entries
                            if (item instanceof StringRefItem) {
                                StringRefItem stringRef = (StringRefItem) item;
                                int utf8Index = stringRef.getValue();
                                Item<?> utf8Item = items.get(utf8Index);
                                if (utf8Item instanceof Utf8Item) {
                                    String str = ((Utf8Item) utf8Item).getValue();
                                    if (str != null && !str.isEmpty()) {
                                        strings.add(new StringEntry(str, classEntry.getClassName(), classEntry));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Skip invalid entries
                        }
                    }
                }

                return strings;
            }

            @Override
            protected void done() {
                try {
                    allStrings = get();
                    tableModel.setStrings(allStrings);
                    statusLabel.setText("Found " + allStrings.size() + " strings across " +
                            project.getClassCount() + " classes. Double-click to navigate.");
                } catch (Exception e) {
                    statusLabel.setText("Error: " + e.getMessage());
                }
                refreshButton.setEnabled(true);
            }
        };

        worker.execute();
    }

    private void applyFilter() {
        String text = filterField.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
        }
        statusLabel.setText("Showing " + stringsTable.getRowCount() + " of " + allStrings.size() + " strings");
    }

    private void navigateToSelectedString() {
        int viewRow = stringsTable.getSelectedRow();
        if (viewRow < 0) return;

        int modelRow = stringsTable.convertRowIndexToModel(viewRow);
        StringEntry entry = tableModel.getEntryAt(modelRow);
        if (entry != null && entry.classEntry != null) {
            EventBus.getInstance().post(new ClassSelectedEvent(this, entry.classEntry));
        }
    }

    /**
     * Refresh the panel.
     */
    public void refresh() {
        // Auto-extract on first show if empty
        if (allStrings.isEmpty() && project.getClassPool() != null) {
            extractStrings();
        }
    }

    // Data model
    private static class StringEntry {
        final String value;
        final String className;
        final ClassEntryModel classEntry;

        StringEntry(String value, String className, ClassEntryModel classEntry) {
            this.value = value;
            this.className = className;
            this.classEntry = classEntry;
        }
    }

    // Table model
    private static class StringsTableModel extends AbstractTableModel {
        private final String[] COLUMNS = {"String", "Class"};
        private List<StringEntry> strings = new ArrayList<>();

        void setStrings(List<StringEntry> strings) {
            this.strings = strings;
            fireTableDataChanged();
        }

        StringEntry getEntryAt(int row) {
            if (row >= 0 && row < strings.size()) {
                return strings.get(row);
            }
            return null;
        }

        @Override
        public int getRowCount() {
            return strings.size();
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
            StringEntry entry = strings.get(rowIndex);
            switch (columnIndex) {
                case 0: return entry.value;
                case 1: return formatClassName(entry.className);
                default: return "";
            }
        }

        private String formatClassName(String className) {
            if (className == null) return "?";
            return className.replace('/', '.');
        }
    }
}
