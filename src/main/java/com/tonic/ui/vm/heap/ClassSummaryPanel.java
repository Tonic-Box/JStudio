package com.tonic.ui.vm.heap;

import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.vm.heap.model.HeapSnapshot;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

public class ClassSummaryPanel extends JPanel {

    private final JTable table;
    private final ClassTableModel tableModel;
    private final JTextField filterField;
    private final JLabel totalsLabel;

    private Consumer<String> onClassSelected;
    private Map<String, Integer> currentCounts = new HashMap<>();
    private Map<String, Integer> snapshotCounts = new HashMap<>();

    public ClassSummaryPanel() {
        setLayout(new BorderLayout(5, 5));
        setBackground(JStudioTheme.getBgSecondary());

        filterField = new JTextField();
        filterField.putClientProperty("JTextField.placeholderText", "Filter classes...");
        filterField.addActionListener(e -> applyFilter());
        filterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
        });
        add(filterField, BorderLayout.NORTH);

        tableModel = new ClassTableModel();
        table = new JTable(tableModel);
        table.setBackground(JStudioTheme.getBgSecondary());
        table.setForeground(JStudioTheme.getTextPrimary());
        table.setGridColor(JStudioTheme.getBorder());
        table.setSelectionBackground(JStudioTheme.getAccent());
        table.setSelectionForeground(JStudioTheme.getTextPrimary());
        table.setRowHeight(22);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 1));

        table.getColumnModel().getColumn(0).setPreferredWidth(140);
        table.getColumnModel().getColumn(1).setPreferredWidth(40);
        table.getColumnModel().getColumn(2).setPreferredWidth(40);

        table.setDefaultRenderer(Object.class, new ClassCellRenderer());

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && onClassSelected != null) {
                int row = table.getSelectedRow();
                if (row >= 0) {
                    String className = (String) tableModel.getValueAt(row, 0);
                    onClassSelected.accept(className);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(JStudioTheme.getBgSecondary());
        add(scroll, BorderLayout.CENTER);

        totalsLabel = new JLabel("0 classes, 0 objects");
        totalsLabel.setForeground(JStudioTheme.getTextSecondary());
        totalsLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(totalsLabel, BorderLayout.SOUTH);
    }

    public void setOnClassSelected(Consumer<String> callback) {
        this.onClassSelected = callback;
    }

    public void selectFirstRow() {
        if (table.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        }
    }

    public void update(Map<String, Integer> counts, HeapSnapshot snapshot) {
        this.currentCounts = new HashMap<>(counts);
        this.snapshotCounts = snapshot != null ? new HashMap<>(snapshot.getClassCounts()) : new HashMap<>();
        applyFilter();
        updateTotals();
    }

    public void incrementClass(String className) {
        currentCounts.merge(className, 1, Integer::sum);
        applyFilter();
        updateTotals();
    }

    public void clear() {
        currentCounts.clear();
        snapshotCounts.clear();
        tableModel.setData(List.of());
        updateTotals();
    }

    private void applyFilter() {
        String filter = filterField.getText().toLowerCase().trim();
        List<ClassEntry> entries = new ArrayList<>();

        for (Map.Entry<String, Integer> e : currentCounts.entrySet()) {
            String className = e.getKey();
            if (filter.isEmpty() || className.toLowerCase().contains(filter)) {
                int count = e.getValue();
                int snapshotCount = snapshotCounts.getOrDefault(className, 0);
                int delta = count - snapshotCount;
                entries.add(new ClassEntry(className, count, delta));
            }
        }

        entries.sort((a, b) -> Integer.compare(b.count, a.count));
        tableModel.setData(entries);
    }

    private void updateTotals() {
        int totalClasses = currentCounts.size();
        int totalObjects = currentCounts.values().stream().mapToInt(Integer::intValue).sum();
        totalsLabel.setText(String.format("%d classes, %d objects", totalClasses, totalObjects));
    }

    private String getSimpleClassName(String fullName) {
        if (fullName == null) return "null";
        int lastSlash = fullName.lastIndexOf('/');
        return lastSlash >= 0 ? fullName.substring(lastSlash + 1) : fullName;
    }

    private static class ClassEntry {
        final String className;
        final int count;
        final int delta;

        ClassEntry(String className, int count, int delta) {
            this.className = className;
            this.count = count;
            this.delta = delta;
        }
    }

    private class ClassTableModel extends AbstractTableModel {
        private final String[] columns = {"Class", "Count", "Δ"};
        private List<ClassEntry> data = new ArrayList<>();

        public void setData(List<ClassEntry> data) {
            this.data = data;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return data.size();
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
        public Object getValueAt(int row, int column) {
            ClassEntry entry = data.get(row);
            switch (column) {
                case 0: return entry.className;
                case 1: return entry.count;
                case 2: return entry.delta;
                default: return null;
            }
        }
    }

    private class ClassCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            setBackground(isSelected ? JStudioTheme.getAccent() : JStudioTheme.getBgSecondary());
            setForeground(JStudioTheme.getTextPrimary());

            if (column == 0 && value instanceof String) {
                String className = (String) value;
                String simple = getSimpleClassName(className);
                setText(simple);
                setToolTipText(className);
            } else if (column == 2 && value instanceof Integer) {
                int delta = (Integer) value;
                if (delta > 0) {
                    setText("+" + delta);
                    if (!isSelected) setForeground(new Color(100, 200, 100));
                } else if (delta < 0) {
                    setText(String.valueOf(delta));
                    if (!isSelected) setForeground(new Color(200, 100, 100));
                } else {
                    setText("0");
                }
            }

            return this;
        }
    }
}
