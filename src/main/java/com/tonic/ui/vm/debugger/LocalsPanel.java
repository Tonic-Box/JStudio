package com.tonic.ui.vm.debugger;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.execution.state.ValueTag;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
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

public class LocalsPanel extends ThemedJPanel {

    private static final int VALUE_COLUMN = 3;

    private final JTable localsTable;
    private final LocalsTableModel tableModel;
    private BiConsumer<Integer, ConcreteValue> onValueEdit;
    private ObjectInspectorDialog.FieldEditCallback onObjectFieldEdit;
    private ClassResolver classResolver;

    public LocalsPanel() {
        super(BackgroundStyle.PRIMARY, new BorderLayout());
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
        localsTable.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_NORMAL));
        localsTable.getTableHeader().setBackground(JStudioTheme.getBgPrimary());
        localsTable.getTableHeader().setForeground(JStudioTheme.getTextPrimary());
        localsTable.setRowHeight(UIConstants.TABLE_ROW_HEIGHT);

        localsTable.setDefaultRenderer(Object.class, new LocalsCellRenderer());

        localsTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        localsTable.getColumnModel().getColumn(1).setPreferredWidth(80);
        localsTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        localsTable.getColumnModel().getColumn(3).setPreferredWidth(150);

        localsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleDoubleClick(e.getPoint());
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(localsTable);
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
        int row = localsTable.rowAtPoint(point);
        int column = localsTable.columnAtPoint(point);

        if (row < 0 || column != VALUE_COLUMN) {
            return;
        }

        LocalEntry entry = tableModel.getEntryAt(row);
        if (entry == null) {
            return;
        }

        if (!(entry instanceof EditableLocalEntry)) {
            return;
        }

        EditableLocalEntry editable = (EditableLocalEntry) entry;
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
            "Edit Local Variable: " + entry.getName(),
            entry.getValue(),
            tag
        );

        if (newValue != null && onValueEdit != null) {
            editable.setUserModified(true);
            onValueEdit.accept(entry.getSlot(), newValue);
        }
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
            } else if (entry instanceof EditableLocalEntry &&
                       ((EditableLocalEntry) entry).isUserModified()) {
                setBackground(new Color(40, 80, 40));
            } else if (entry != null && entry.isChanged()) {
                setBackground(JStudioTheme.getWarning().darker().darker());
            } else {
                setBackground(JStudioTheme.getBgSecondary());
            }
            setForeground(JStudioTheme.getTextPrimary());

            if (column == VALUE_COLUMN && entry instanceof EditableLocalEntry) {
                EditableLocalEntry editable = (EditableLocalEntry) entry;
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
