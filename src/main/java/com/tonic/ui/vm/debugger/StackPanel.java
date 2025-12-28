package com.tonic.ui.vm.debugger;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.execution.state.ValueTag;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.vm.debugger.edit.ValueEditDialog;
import com.tonic.ui.vm.debugger.inspector.ObjectInspectorDialog;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class StackPanel extends JPanel {

    private static final int VALUE_COLUMN = 2;

    private final JTable stackTable;
    private final StackTableModel tableModel;
    private BiConsumer<Integer, ConcreteValue> onValueEdit;
    private ObjectInspectorDialog.FieldEditCallback onObjectFieldEdit;
    private ClassResolver classResolver;

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

        stackTable.setDefaultRenderer(Object.class, new StackCellRenderer());

        stackTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        stackTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        stackTable.getColumnModel().getColumn(2).setPreferredWidth(150);

        stackTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleDoubleClick(e.getPoint());
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(stackTable);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(JStudioTheme.getBgSecondary());

        add(scrollPane, BorderLayout.CENTER);
    }

    public void setOnValueEdit(BiConsumer<Integer, ConcreteValue> callback) {
        this.onValueEdit = callback;
    }

    public void setOnObjectFieldEdit(ObjectInspectorDialog.FieldEditCallback callback) {
        this.onObjectFieldEdit = callback;
    }

    public void setClassResolver(ClassResolver classResolver) {
        this.classResolver = classResolver;
    }

    private void handleDoubleClick(Point point) {
        int row = stackTable.rowAtPoint(point);
        int column = stackTable.columnAtPoint(point);

        if (row < 0 || column != VALUE_COLUMN) {
            return;
        }

        StackEntry entry = tableModel.getEntryAt(row);
        if (entry == null) {
            return;
        }

        if (!(entry instanceof EditableStackEntry)) {
            return;
        }

        EditableStackEntry editable = (EditableStackEntry) entry;
        ValueTag tag = editable.getValueTag();

        if ((tag == ValueTag.REFERENCE || tag == ValueTag.NULL) && editable.getRawValue() != null) {
            Object raw = editable.getRawValue();
            if (raw instanceof ObjectInstance && classResolver != null) {
                ObjectInspectorDialog.showDialog(
                    this,
                    (ObjectInstance) raw,
                    classResolver,
                    onObjectFieldEdit
                );
                return;
            }
        }

        if (!editable.isEditable()) {
            return;
        }

        ConcreteValue newValue = ValueEditDialog.showDialog(
            this,
            "Edit Stack Value [" + entry.getIndex() + "]",
            entry.getValue(),
            tag
        );

        if (newValue != null && onValueEdit != null) {
            editable.setUserModified(true);
            onValueEdit.accept(entry.getIndex(), newValue);
        }
    }

    public void updateStack(List<StackEntry> entries) {
        tableModel.setEntries(entries);
    }

    public void clear() {
        tableModel.clear();
    }

    private class StackCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            StackEntry entry = tableModel.getEntryAt(row);

            if (isSelected) {
                setBackground(JStudioTheme.getAccent());
            } else if (entry instanceof EditableStackEntry &&
                       ((EditableStackEntry) entry).isUserModified()) {
                setBackground(new Color(40, 80, 40));
            } else {
                setBackground(JStudioTheme.getBgSecondary());
            }
            setForeground(JStudioTheme.getTextPrimary());

            if (column == VALUE_COLUMN && entry instanceof EditableStackEntry) {
                EditableStackEntry editable = (EditableStackEntry) entry;
                ValueTag tag = editable.getValueTag();

                if ((tag == ValueTag.REFERENCE || tag == ValueTag.NULL) &&
                    editable.getRawValue() instanceof ObjectInstance) {
                    setForeground(JStudioTheme.getAccent());
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else if (editable.isEditable()) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }
            }

            return this;
        }
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

        public StackEntry getEntryAt(int row) {
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
