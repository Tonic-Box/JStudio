package com.tonic.ui.vm.debugger.inspector;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/** The table model for the object inspector: one row per field with name, type and value. */
public class FieldsTableModel extends AbstractTableModel
{

    private static final String[] COLUMN_NAMES = {"Name", "Type", "Value"};
    private static final int COL_NAME = 0;
    private static final int COL_TYPE = 1;
    private static final int COL_VALUE = 2;

    private List<FieldInfo> fields = new ArrayList<>();

    /**
     * Replaces the rows with a copy of the given fields.
     *
     * @param fields the fields to show
     */
    public void setFields(List<FieldInfo> fields)
    {
        this.fields = new ArrayList<>(fields);
        fireTableDataChanged();
    }

    /** Removes every row. */
    public void clear()
    {
        fields.clear();
        fireTableDataChanged();
    }

    /**
     * Returns the field shown in a row.
     *
     * @param row the row index
     * @return the field, or null if the row is out of range
     */
    public FieldInfo getFieldAt(int row)
    {
        if (row >= 0 && row < fields.size())
        {
            return fields.get(row);
        }
        return null;
    }

    @Override
    public int getRowCount()
    {
        return fields.size();
    }

    @Override
    public int getColumnCount()
    {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column)
    {
        return COLUMN_NAMES[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex)
    {
        return String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex)
    {
        FieldInfo field = fields.get(rowIndex);
        switch (columnIndex)
        {
            case COL_NAME:
                return formatFieldName(field);
            case COL_TYPE:
                return field.getTypeName();
            case COL_VALUE:
                return field.getValueString();
            default:
                return "";
        }
    }

    private String formatFieldName(FieldInfo field)
    {
        StringBuilder sb = new StringBuilder();

        if (field.isStatic())
        {
            sb.append("(static) ");
        }
        if (field.isFinal())
        {
            sb.append("(final) ");
        }

        sb.append(field.getName());

        return sb.toString();
    }
}
