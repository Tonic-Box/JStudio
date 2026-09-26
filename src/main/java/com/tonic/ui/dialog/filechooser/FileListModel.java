package com.tonic.ui.dialog.filechooser;

import lombok.Getter;

import javax.swing.Icon;
import javax.swing.table.AbstractTableModel;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/** Table model for the file list, with icon, name, size, date and type columns; directories always sort before files. */
public class FileListModel extends AbstractTableModel
{

    public static final int COL_ICON = 0;
    public static final int COL_NAME = 1;
    public static final int COL_SIZE = 2;
    public static final int COL_DATE = 3;
    public static final int COL_TYPE = 4;

    private static final String[] COLUMN_NAMES = {"", "Name", "Size", "Date Modified", "Type"};
    private static final Class<?>[] COLUMN_CLASSES = {Icon.class, String.class, Long.class, Date.class, String.class};

    private final List<FileEntry> entries = new ArrayList<>();
    /** The directory whose files are shown, or null if none was set. */
    @Getter
    private File currentDirectory;
    /** The column the rows are sorted by. */
    @Getter
    private int sortColumn = COL_NAME;
    /** Whether the sort is ascending. */
    @Getter
    private boolean sortAscending = true;

    @Override
    public int getRowCount()
    {
        return entries.size();
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
    public Class<?> getColumnClass(int column)
    {
        return COLUMN_CLASSES[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex)
    {
        if (rowIndex < 0 || rowIndex >= entries.size())
        {
            return null;
        }

        FileEntry entry = entries.get(rowIndex);
        switch (columnIndex)
        {
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
     * The entry in a row.
     *
     * @param row the row index
     * @return the entry, or null if the row is out of range
     */
    public FileEntry getEntryAt(int row)
    {
        if (row < 0 || row >= entries.size())
        {
            return null;
        }
        return entries.get(row);
    }

    /**
     * The file in a row.
     *
     * @param row the row index
     * @return the file, or null if the row is out of range
     */
    public File getFileAt(int row)
    {
        FileEntry entry = getEntryAt(row);
        return entry != null ? entry.getFile() : null;
    }

    /**
     * The entries in several rows, skipping rows out of range.
     *
     * @param rows the row indexes
     * @return a new list of entries, in the order of rows
     */
    public List<FileEntry> getEntriesAt(int[] rows)
    {
        List<FileEntry> result = new ArrayList<>();
        for (int row : rows)
        {
            FileEntry entry = getEntryAt(row);
            if (entry != null)
            {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * The files in several rows, skipping rows out of range.
     *
     * @param rows the row indexes
     * @return a new list of files, in the order of rows
     */
    public List<File> getFilesAt(int[] rows)
    {
        List<File> result = new ArrayList<>();
        for (int row : rows)
        {
            File file = getFileAt(row);
            if (file != null)
            {
                result.add(file);
            }
        }
        return result;
    }

    /**
     * Replaces the rows with the given files, sorted by the current sort.
     *
     * @param files the files, or null for none
     */
    public void setFiles(List<File> files)
    {
        entries.clear();

        if (files != null)
        {
            for (File file : files)
            {
                entries.add(new FileEntry(file));
            }
        }

        sortEntries();
        fireTableDataChanged();
    }

    /**
     * Records which directory the rows belong to.
     *
     * @param directory the directory
     */
    public void setCurrentDirectory(File directory)
    {
        this.currentDirectory = directory;
    }

    /**
     * Sorts by a column, flipping the direction if it is already the sort column and starting ascending otherwise.
     *
     * @param column the column, one of the COL constants
     */
    public void sortBy(int column)
    {
        if (column == sortColumn)
        {
            sortAscending = !sortAscending;
        }
        else
        {
            sortColumn = column;
            sortAscending = true;
        }
        sortEntries();
        fireTableDataChanged();
    }

    private void sortEntries()
    {
        Comparator<FileEntry> comparator;

        switch (sortColumn)
        {
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

        Comparator<FileEntry> fullComparator = Comparator
                .comparing((FileEntry e) -> !e.isDirectory())
                .thenComparing(sortAscending ? comparator : comparator.reversed());

        entries.sort(fullComparator);
    }

    /**
     * Finds the first row whose name starts with a prefix, ignoring case.
     *
     * @param prefix the prefix
     * @return the row index, or -1 if none matches or the prefix is null or empty
     */
    public int findByPrefix(String prefix)
    {
        if (prefix == null || prefix.isEmpty())
        {
            return -1;
        }

        String lowerPrefix = prefix.toLowerCase();
        for (int i = 0; i < entries.size(); i++)
        {
            if (entries.get(i).getName().toLowerCase().startsWith(lowerPrefix))
            {
                return i;
            }
        }
        return -1;
    }

    /** A file with its name, kind, size, modification date, type label and system icon read once at creation. */
    @Getter
    public static class FileEntry
    {
        private final File file;
        private final String name;
        private final boolean directory;
        private final long size;
        private final Date lastModified;
        private final String type;
        private final Icon icon;

        /**
         * Reads the file's properties.
         *
         * @param file the file
         */
        public FileEntry(File file)
        {
            this.file = file;
            this.name = file.getName();
            this.directory = file.isDirectory();
            this.size = directory ? 0 : file.length();
            this.lastModified = new Date(file.lastModified());
            this.type = computeType();
            this.icon = FileSystemWorker.getSystemIcon(file);
        }

        private String computeType()
        {
            if (directory)
            {
                return "Folder";
            }

            String name = file.getName();
            int dot = name.lastIndexOf('.');
            if (dot > 0 && dot < name.length() - 1)
            {
                String ext = name.substring(dot + 1).toUpperCase();
                switch (ext)
                {
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

        /**
         * The size in B, KB, MB or GB with one decimal place.
         *
         * @return the size text, or -- for a directory
         */
        public String getFormattedSize()
        {
            if (directory)
            {
                return "--";
            }

            if (size < 1024)
            {
                return size + " B";
            }
            else if (size < 1024 * 1024)
            {
                return String.format("%.1f KB", size / 1024.0);
            }
            else if (size < 1024 * 1024 * 1024)
            {
                return String.format("%.1f MB", size / (1024.0 * 1024));
            }
            else
            {
                return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
            }
        }

        /**
         * The modification date in the form Jan 05, 2026 14:30.
         *
         * @return the date text
         */
        public String getFormattedDate()
        {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm");
            return sdf.format(lastModified);
        }
    }
}
