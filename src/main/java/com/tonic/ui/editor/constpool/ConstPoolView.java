package com.tonic.ui.editor.constpool;

import com.tonic.ui.core.component.LoadingOverlay;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class ConstPoolView extends JPanel implements ThemeChangeListener {

    private final ClassEntryModel classEntry;
    private final JTable table;
    private final ConstPoolTableModel tableModel;
    private final JComboBox<String> typeFilter;
    private final JTextField searchField;
    private final JLabel statusLabel;
    private final JScrollPane scrollPane;
    private final LoadingOverlay loadingOverlay;
    private SwingWorker<?, ?> currentWorker;

    private static final String[] TYPE_OPTIONS = {
            "All",
            "Utf8",
            "Integer", "Float", "Long", "Double",
            "Class", "String",
            "FieldRef", "MethodRef", "InterfaceRef",
            "NameAndType",
            "MethodHandle", "MethodType",
            "Dynamic", "InvokeDynamic",
            "Package", "Module"
    };

    public ConstPoolView(ClassEntryModel classEntry) {
        this.classEntry = classEntry;

        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgPrimary());

        JPanel toolbar = createToolbar();
        add(toolbar, BorderLayout.NORTH);

        tableModel = new ConstPoolTableModel();
        table = new JTable(tableModel);
        configureTable();

        scrollPane = new JScrollPane(table);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(JStudioTheme.getBgPrimary());

        loadingOverlay = new LoadingOverlay();

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new OverlayLayout(contentPanel));
        loadingOverlay.setAlignmentX(0.5f);
        loadingOverlay.setAlignmentY(0.5f);
        scrollPane.setAlignmentX(0.5f);
        scrollPane.setAlignmentY(0.5f);
        contentPanel.add(loadingOverlay);
        contentPanel.add(scrollPane);

        add(contentPanel, BorderLayout.CENTER);

        statusLabel = new JLabel("No entries");
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusLabel.setFont(JStudioTheme.getUIFont(11));
        add(statusLabel, BorderLayout.SOUTH);

        typeFilter = (JComboBox<String>) ((JPanel) toolbar.getComponent(0)).getComponent(1);
        searchField = (JTextField) ((JPanel) toolbar.getComponent(0)).getComponent(3);

        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(JStudioTheme.getBgSecondary());
        toolbar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftPanel.setOpaque(false);

        JLabel filterLabel = new JLabel("Type:");
        filterLabel.setForeground(JStudioTheme.getTextPrimary());
        filterLabel.setFont(JStudioTheme.getUIFont(12));
        leftPanel.add(filterLabel);

        JComboBox<String> filter = new JComboBox<>(TYPE_OPTIONS);
        filter.setBackground(JStudioTheme.getBgTertiary());
        filter.setForeground(JStudioTheme.getTextPrimary());
        filter.setFont(JStudioTheme.getUIFont(12));
        filter.setPreferredSize(new Dimension(120, 24));
        filter.addActionListener(e -> {
            String selected = (String) filter.getSelectedItem();
            if (selected != null) {
                tableModel.setTypeFilter(selected);
                updateStatus();
            }
        });
        leftPanel.add(filter);

        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setForeground(JStudioTheme.getTextPrimary());
        searchLabel.setFont(JStudioTheme.getUIFont(12));
        leftPanel.add(searchLabel);

        JTextField search = new JTextField(20);
        search.setBackground(JStudioTheme.getBgTertiary());
        search.setForeground(JStudioTheme.getTextPrimary());
        search.setCaretColor(JStudioTheme.getTextPrimary());
        search.setFont(JStudioTheme.getUIFont(12));
        search.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)));
        search.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                tableModel.setSearchText(search.getText());
                updateStatus();
            }
        });
        leftPanel.add(search);

        toolbar.add(leftPanel, BorderLayout.WEST);

        return toolbar;
    }

    private void configureTable() {
        table.setBackground(JStudioTheme.getBgPrimary());
        table.setForeground(JStudioTheme.getTextPrimary());
        table.setSelectionBackground(JStudioTheme.getSelection());
        table.setSelectionForeground(JStudioTheme.getTextPrimary());
        table.setGridColor(JStudioTheme.getBorder());
        table.setFont(JStudioTheme.getCodeFont(12));
        table.setRowHeight(22);
        table.setShowGrid(true);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.getTableHeader().setBackground(JStudioTheme.getBgSecondary());
        table.getTableHeader().setForeground(JStudioTheme.getTextPrimary());
        table.getTableHeader().setFont(JStudioTheme.getUIFont(12));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        TableColumn indexCol = table.getColumnModel().getColumn(0);
        indexCol.setPreferredWidth(50);
        indexCol.setMaxWidth(80);

        TableColumn typeCol = table.getColumnModel().getColumn(1);
        typeCol.setPreferredWidth(100);
        typeCol.setMaxWidth(150);

        DefaultTableCellRenderer indexRenderer = new DefaultTableCellRenderer();
        indexRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        indexCol.setCellRenderer(indexRenderer);

        DefaultTableCellRenderer typeRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    setForeground(getTypeColor((String) value));
                }
                return c;
            }
        };
        typeCol.setCellRenderer(typeRenderer);

        DefaultTableCellRenderer valueRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setFont(JStudioTheme.getCodeFont(12));
                if (value != null) {
                    setToolTipText(value.toString());
                }
                return c;
            }
        };
        table.getColumnModel().getColumn(2).setCellRenderer(valueRenderer);
    }

    private Color getTypeColor(String type) {
        if (type == null) return JStudioTheme.getTextPrimary();
        switch (type) {
            case "Utf8":
                return new Color(152, 195, 121);
            case "Integer":
            case "Float":
            case "Long":
            case "Double":
                return new Color(209, 154, 102);
            case "Class":
                return new Color(229, 192, 123);
            case "String":
                return new Color(152, 195, 121);
            case "FieldRef":
                return new Color(86, 182, 194);
            case "MethodRef":
            case "InterfaceRef":
                return new Color(198, 120, 221);
            case "NameAndType":
                return new Color(97, 175, 239);
            case "MethodHandle":
            case "MethodType":
                return new Color(224, 108, 117);
            case "Dynamic":
            case "InvokeDynamic":
                return new Color(255, 165, 0);
            default:
                return JStudioTheme.getTextPrimary();
        }
    }

    public void refresh() {
        cancelCurrentWorker();

        loadingOverlay.showLoading("Loading constant pool...");

        currentWorker = new SwingWorker<java.util.List<ConstPoolEntry>, Void>() {
            @Override
            protected java.util.List<ConstPoolEntry> doInBackground() {
                return ConstPoolTableModel.buildEntries(classEntry);
            }

            @Override
            protected void done() {
                loadingOverlay.hideLoading();
                if (isCancelled()) {
                    return;
                }
                try {
                    java.util.List<ConstPoolEntry> entries = get();
                    tableModel.setEntries(entries);
                    updateStatus();
                } catch (Exception e) {
                    // Ignore
                }
            }
        };

        currentWorker.execute();
    }

    private void cancelCurrentWorker() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
            loadingOverlay.hideLoading();
        }
    }

    private void updateStatus() {
        int total = tableModel.getTotalCount();
        int filtered = tableModel.getFilteredCount();
        if (total == 0) {
            statusLabel.setText("No entries");
        } else if (filtered == total) {
            statusLabel.setText(total + " entries");
        } else {
            statusLabel.setText(total + " entries (" + filtered + " shown)");
        }
    }

    public String getText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Constant Pool for ").append(classEntry.getClassName()).append("\n");
        sb.append("=".repeat(60)).append("\n\n");

        for (int i = 0; i < tableModel.getRowCount(); i++) {
            sb.append(String.format("#%-4d  %-15s  %s%n",
                    tableModel.getValueAt(i, 0),
                    tableModel.getValueAt(i, 1),
                    tableModel.getValueAt(i, 2)));
        }
        return sb.toString();
    }

    public void copySelection() {
        int[] rows = table.getSelectedRows();
        if (rows.length == 0) return;

        StringBuilder sb = new StringBuilder();
        for (int row : rows) {
            sb.append(String.format("#%-4d  %-15s  %s%n",
                    tableModel.getValueAt(row, 0),
                    tableModel.getValueAt(row, 1),
                    tableModel.getValueAt(row, 2)));
        }
        StringSelection selection = new StringSelection(sb.toString());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
    }

    public String getSelectedText() {
        int[] rows = table.getSelectedRows();
        if (rows.length == 0) return null;

        StringBuilder sb = new StringBuilder();
        for (int row : rows) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(tableModel.getValueAt(row, 2));
        }
        return sb.toString();
    }

    public void goToLine(int line) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Object val = tableModel.getValueAt(i, 0);
            if (val instanceof Integer && (Integer) val == line) {
                table.setRowSelectionInterval(i, i);
                table.scrollRectToVisible(table.getCellRect(i, 0, true));
                return;
            }
        }
    }

    public void showFindDialog() {
        String input = JOptionPane.showInputDialog(this, "Search:", "Find", JOptionPane.PLAIN_MESSAGE);
        if (input != null && !input.isEmpty()) {
            searchField.setText(input);
            tableModel.setSearchText(input);
            updateStatus();
        }
    }

    public void scrollToText(String text) {
        if (text == null || text.isEmpty()) return;

        searchField.setText(text);
        tableModel.setSearchText(text);
        updateStatus();

        if (tableModel.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
            table.scrollRectToVisible(table.getCellRect(0, 0, true));
        }
    }

    public void setFontSize(int size) {
        table.setFont(JStudioTheme.getCodeFont(size));
        table.setRowHeight(size + 10);
    }

    public void highlightLine(int index) {
        goToLine(index);
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        SwingUtilities.invokeLater(this::applyTheme);
    }

    private void applyTheme() {
        setBackground(JStudioTheme.getBgPrimary());

        table.setBackground(JStudioTheme.getBgPrimary());
        table.setForeground(JStudioTheme.getTextPrimary());
        table.setSelectionBackground(JStudioTheme.getSelection());
        table.setSelectionForeground(JStudioTheme.getTextPrimary());
        table.setGridColor(JStudioTheme.getBorder());
        table.getTableHeader().setBackground(JStudioTheme.getBgSecondary());
        table.getTableHeader().setForeground(JStudioTheme.getTextPrimary());

        scrollPane.getViewport().setBackground(JStudioTheme.getBgPrimary());

        statusLabel.setForeground(JStudioTheme.getTextSecondary());

        typeFilter.setBackground(JStudioTheme.getBgTertiary());
        typeFilter.setForeground(JStudioTheme.getTextPrimary());

        searchField.setBackground(JStudioTheme.getBgTertiary());
        searchField.setForeground(JStudioTheme.getTextPrimary());
        searchField.setCaretColor(JStudioTheme.getTextPrimary());

        repaint();
    }
}
