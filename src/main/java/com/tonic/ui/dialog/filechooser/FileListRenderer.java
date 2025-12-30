package com.tonic.ui.dialog.filechooser;

import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Component;
import java.util.Date;

/**
 * Custom renderer for file list cells with proper styling.
 */
public class FileListRenderer extends DefaultTableCellRenderer {

    private final FileListModel model;

    public FileListRenderer(FileListModel model) {
        this.model = model;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        // Background colors
        if (isSelected) {
            setBackground(JStudioTheme.getSelection());
            setForeground(JStudioTheme.getTextPrimary());
        } else {
            // Alternating row colors
            if (row % 2 == 0) {
                setBackground(JStudioTheme.getBgTertiary());
            } else {
                setBackground(JStudioTheme.getBgSecondary());
            }
            setForeground(JStudioTheme.getTextPrimary());
        }

        // Reset icon
        setIcon(null);
        setHorizontalAlignment(JLabel.LEFT);

        // Format based on column
        int modelColumn = table.convertColumnIndexToModel(column);
        FileListModel.FileEntry entry = model.getEntryAt(table.convertRowIndexToModel(row));

        if (entry == null) {
            return this;
        }

        switch (modelColumn) {
            case FileListModel.COL_ICON:
                setIcon((Icon) value);
                setText("");
                setHorizontalAlignment(JLabel.CENTER);
                break;

            case FileListModel.COL_NAME:
                setText(entry.getName());
                break;

            case FileListModel.COL_SIZE:
                setText(entry.getFormattedSize());
                setHorizontalAlignment(JLabel.RIGHT);
                if (entry.isDirectory()) {
                    setForeground(isSelected ? JStudioTheme.getTextSecondary() : JStudioTheme.getTextSecondary());
                }
                break;

            case FileListModel.COL_DATE:
                setText(entry.getFormattedDate());
                break;

            case FileListModel.COL_TYPE:
                setText(entry.getType());
                setForeground(isSelected ? JStudioTheme.getTextPrimary() : JStudioTheme.getTextSecondary());
                break;

            default:
                if (value != null) {
                    setText(value.toString());
                }
        }

        // Add some padding
        setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));

        return this;
    }
}
