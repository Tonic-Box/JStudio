package com.tonic.ui.vm.debugger;

import com.tonic.ui.theme.JStudioTheme;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class StackPanel extends JPanel {

    private final JTable stackTable;
    private final StackTableModel tableModel;

    public StackPanel() {
        setLayout(new BorderLayout());
        setBackground(JStudioTheme.getBgPrimary());
        setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            "Operand Stack",
            TitledBorder.LEFT,
            TitledBorder.TOP,
            null,
            JStudioTheme.getTextPrimary()
        ));

        tableModel = new StackTableModel();
        stackTable = new JTable(tableModel);
        stackTable.setBackground(JStudioTheme.getBgSecondary());
        stackTable.setForeground(JStudioTheme.getTextPrimary());
        stackTable.setGridColor(JStudioTheme.getBorder());
        stackTable.setSelectionBackground(JStudioTheme.getAccent());
        stackTable.setSelectionForeground(JStudioTheme.getTextPrimary());
        stackTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        stackTable.getTableHeader().setBackground(JStudioTheme.getBgPrimary());
        stackTable.getTableHeader().setForeground(JStudioTheme.getTextPrimary());
        stackTable.setRowHeight(20);

        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setBackground(isSelected ? JStudioTheme.getAccent() : JStudioTheme.getBgSecondary());
                setForeground(JStudioTheme.getTextPrimary());
                return this;
            }
        };

        for (int i = 0; i < stackTable.getColumnCount(); i++) {
            stackTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        stackTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        stackTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        stackTable.getColumnModel().getColumn(2).setPreferredWidth(150);

        JScrollPane scrollPane = new JScrollPane(stackTable);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(JStudioTheme.getBgSecondary());

        add(scrollPane, BorderLayout.CENTER);
    }

    public void updateStack(List<StackEntry> entries) {
        tableModel.setEntries(entries);
    }

    public void clear() {
        tableModel.clear();
    }

    private static class StackTableModel extends AbstractTableModel {
        private final String[] columnNames = {"#", "Type", "Value"};
        private List<StackEntry> entries = new ArrayList<>();

        public void setEntries(List<StackEntry> entries) {
            this.entries = new ArrayList<>(entries);
            fireTableDataChanged();
        }

        public void clear() {
            entries.clear();
            fireTableDataChanged();
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
            StackEntry entry = entries.get(rowIndex);
            switch (columnIndex) {
                case 0: return entry.getIndex();
                case 1: return entry.getTypeName();
                case 2: return entry.getValue();
                default: return "";
            }
        }
    }
}
