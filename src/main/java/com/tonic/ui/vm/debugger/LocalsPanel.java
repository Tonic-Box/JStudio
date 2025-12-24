package com.tonic.ui.vm.debugger;

import com.tonic.ui.theme.JStudioTheme;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class LocalsPanel extends JPanel {

    private final JTable localsTable;
    private final LocalsTableModel tableModel;

    public LocalsPanel() {
        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgPrimary());
        setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            "Local Variables",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            null,
            JStudioTheme.getTextPrimary()
        ));

        tableModel = new LocalsTableModel();
        localsTable = new JTable(tableModel);
        localsTable.setBackground(JStudioTheme.getBgSecondary());
        localsTable.setForeground(JStudioTheme.getTextPrimary());
        localsTable.setGridColor(JStudioTheme.getBorder());
        localsTable.setSelectionBackground(JStudioTheme.getAccent());
        localsTable.setSelectionForeground(JStudioTheme.getTextPrimary());
        localsTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        localsTable.getTableHeader().setBackground(JStudioTheme.getBgPrimary());
        localsTable.getTableHeader().setForeground(JStudioTheme.getTextPrimary());
        localsTable.setRowHeight(20);

        localsTable.setDefaultRenderer(Object.class, new LocalsCellRenderer());

        localsTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        localsTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        localsTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        localsTable.getColumnModel().getColumn(3).setPreferredWidth(150);

        JScrollPane scrollPane = new JScrollPane(localsTable);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(JStudioTheme.getBgSecondary());

        add(scrollPane, BorderLayout.CENTER);
    }

    public void updateLocals(List<LocalEntry> entries) {
        tableModel.setEntries(entries);
    }

    public void clear() {
        tableModel.clear();
    }

    private class LocalsCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            LocalEntry entry = tableModel.getEntryAt(row);

            if (isSelected) {
                setBackground(JStudioTheme.getAccent());
            } else if (entry != null && entry.isChanged()) {
                setBackground(JStudioTheme.getWarning().darker().darker());
            } else {
                setBackground(JStudioTheme.getBgSecondary());
            }
            setForeground(JStudioTheme.getTextPrimary());

            return this;
        }
    }

    private static class LocalsTableModel extends AbstractTableModel {
        private final String[] columnNames = {"Slot", "Name", "Type", "Value"};
        private List<LocalEntry> entries = new ArrayList<>();

        public void setEntries(List<LocalEntry> entries) {
            this.entries = new ArrayList<>(entries);
            fireTableDataChanged();
        }

        public void clear() {
            entries.clear();
            fireTableDataChanged();
        }

        public LocalEntry getEntryAt(int row) {
            if (row >= 0 && row < entries.size()) {
                return entries.get(row);
            }
            return null;
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            LocalEntry entry = entries.get(rowIndex);
            switch (columnIndex) {
                case 0: return entry.getSlot();
                case 1: return entry.getName();
                case 2: return entry.getTypeName();
                case 3: return entry.getValue();
                default: return "";
            }
        }
    }
}
