package com.tonic.ui.dialog.filechooser;

import javax.swing.Icon;
import javax.swing.table.AbstractTableModel;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Table model for the file list, providing columns for name, size, date, and type.
 */
public class FileListModel extends AbstractTableModel {

    public static final int COL_ICON = 0;
    public static final int COL_NAME = 1;
    public static final int COL_SIZE = 2;
    public static final int COL_DATE = 3;
    public static final int COL_TYPE = 4;

    private static final String[] COLUMN_NAMES = {"", "Name", "Size", "Date Modified", "Type"};
    private static final Class<?>[] COLUMN_CLASSES = {Icon.class, String.class, Long.class, Date.class, String.class};

    private final List<FileEntry> entries = new ArrayList<>();
    private File currentDirectory;
    private int sortColumn = COL_NAME;
    private boolean sortAscending = true;

    @Override
    public int getRowCount() {
        return entries.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return COLUMN_CLASSES[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= entries.size()) {
            return null;
        }

        FileEntry entry = entries.get(rowIndex);
        switch (columnIndex) {
            case COL_ICON:
                return entry.getIcon();
            case COL_NAME:
                return entry.getName();
            case COL_SIZE:
                return entry.isDirectory() ? -1L : entry.getSize();
            case COL_DATE:
                return entry.getLastModified();
            case COL_TYPE:
                return entry.getType();
            default:
                return null;
        }
    }

    /**
     * Get the file entry at the specified row.
     */
    public FileEntry getEntryAt(int row) {
        if (row < 0 || row >= entries.size()) {
            return null;
        }
        return entries.get(row);
    }

    /**
     * Get the file at the specified row.
     */
    public File getFileAt(int row) {
        FileEntry entry = getEntryAt(row);
        return entry != null ? entry.getFile() : null;
    }

    /**
     * Get entries for the specified rows.
     */
    public List<FileEntry> getEntriesAt(int[] rows) {
        List<FileEntry> result = new ArrayList<>();
        for (int row : rows) {
            FileEntry entry = getEntryAt(row);
            if (entry != null) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Get files for the specified rows.
     */
    public List<File> getFilesAt(int[] rows) {
        List<File> result = new ArrayList<>();
        for (int row : rows) {
            File file = getFileAt(row);
            if (file != null) {
                result.add(file);
            }
        }
        return result;
    }

    /**
     * Set the files to display.
     */
    public void setFiles(List<File> files) {
        entries.clear();

        if (files != null) {
            for (File file : files) {
                entries.add(new FileEntry(file));
            }
        }

        sortEntries();
        fireTableDataChanged();
    }

    /**
     * Get the current directory.
     */
    public File getCurrentDirectory() {
        return currentDirectory;
    }

    /**
     * Set the current directory.
     */
    public void setCurrentDirectory(File directory) {
        this.currentDirectory = directory;
    }

    /**
     * Sort by the specified column.
     */
    public void sortBy(int column) {
        if (column == sortColumn) {
            sortAscending = !sortAscending;
        } else {
            sortColumn = column;
            sortAscending = true;
        }
        sortEntries();
        fireTableDataChanged();
    }

    /**
     * Get the current sort column.
     */
    public int getSortColumn() {
        return sortColumn;
    }

    /**
     * Check if sorting is ascending.
     */
    public boolean isSortAscending() {
        return sortAscending;
    }

    /**
     * Sort entries by current column.
     */
    private void sortEntries() {
        Comparator<FileEntry> comparator;

        switch (sortColumn) {
            case COL_SIZE:
                comparator = Comparator.comparing(FileEntry::getSize);
                break;
            case COL_DATE:
                comparator = Comparator.comparing(e -> e.getLastModified().getTime());
                break;
            case COL_TYPE:
                comparator = Comparator.comparing(FileEntry::getType, String.CASE_INSENSITIVE_ORDER);
                break;
            case COL_NAME:
            default:
                comparator = Comparator.comparing(FileEntry::getName, String.CASE_INSENSITIVE_ORDER);
                break;
        }

        // Directories always first
        Comparator<FileEntry> fullComparator = Comparator
                .comparing((FileEntry e) -> !e.isDirectory())
                .thenComparing(sortAscending ? comparator : comparator.reversed());

        entries.sort(fullComparator);
    }

    /**
     * Find the row index for a file matching the given prefix.
     */
    public int findByPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return -1;
        }

        String lowerPrefix = prefix.toLowerCase();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getName().toLowerCase().startsWith(lowerPrefix)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * File entry wrapper with cached properties.
     */
    public static class FileEntry {
        private final File file;
        private final String name;
        private final boolean directory;
        private final long size;
        private final Date lastModified;
        private final String type;
        private final Icon icon;

        public FileEntry(File file) {
            this.file = file;
            this.name = file.getName();
            this.directory = file.isDirectory();
            this.size = directory ? 0 : file.length();
            this.lastModified = new Date(file.lastModified());
            this.type = computeType();
            this.icon = FileSystemWorker.getSystemIcon(file);
        }

        private String computeType() {
            if (directory) {
                return "Folder";
            }

            String name = file.getName();
            int dot = name.lastIndexOf('.');
            if (dot > 0 && dot < name.length() - 1) {
                String ext = name.substring(dot + 1).toUpperCase();
                switch (ext) {
                    case "JAR":
                        return "JAR Archive";
                    case "CLASS":
                        return "Class File";
                    case "JAVA":
                        return "Java Source";
                    case "TXT":
                        return "Text File";
                    case "MD":
                        return "Markdown";
                    case "XML":
                        return "XML File";
                    case "JSON":
                        return "JSON File";
                    case "PROPERTIES":
                        return "Properties";
                    default:
                        return ext + " File";
                }
            }
            return "File";
        }

        public File getFile() {
            return file;
        }

        public String getName() {
            return name;
        }

        public boolean isDirectory() {
            return directory;
        }

        public long getSize() {
            return size;
        }

        public Date getLastModified() {
            return lastModified;
        }

        public String getType() {
            return type;
        }

        public Icon getIcon() {
            return icon;
        }

        /**
         * Format size for display.
         */
        public String getFormattedSize() {
            if (directory) {
                return "--";
            }

            if (size < 1024) {
                return size + " B";
            } else if (size < 1024 * 1024) {
                return String.format("%.1f KB", size / 1024.0);
            } else if (size < 1024 * 1024 * 1024) {
                return String.format("%.1f MB", size / (1024.0 * 1024));
            } else {
                return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
            }
        }

        /**
         * Format date for display.
         */
        public String getFormattedDate() {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm");
            return sdf.format(lastModified);
        }
    }
}
