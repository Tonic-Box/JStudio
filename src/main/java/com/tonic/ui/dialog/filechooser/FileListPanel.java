package com.tonic.ui.dialog.filechooser;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.util.QuickAccessManager;

import javax.swing.BorderFactory;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;

public class FileListPanel extends ThemedJPanel {

    /**
     * Listener for file list events.
     */
    public interface FileListListener {
        void onFileDoubleClicked(File file);
        void onSelectionChanged(List<File> selectedFiles);
        void onDirectoryEntered(File directory);
    }

    private final FileListModel model;
    private final JTable table;
    private final TableRowSorter<FileListModel> sorter;
    private final JScrollPane scrollPane;
    private JPopupMenu contextMenu;

    private FileListListener listener;
    private FileChooserMode mode = FileChooserMode.OPEN_FILE;
    private File currentDirectory;

    // Type-ahead search
    private StringBuilder typeAheadBuffer = new StringBuilder();
    private long lastKeyTime = 0;
    private static final long TYPE_AHEAD_TIMEOUT = 1000; // 1 second

    public FileListPanel() {
        super(BackgroundStyle.TERTIARY, new BorderLayout());

        model = new FileListModel();
        table = new JTable(model);
        sorter = new TableRowSorter<>(model);

        setupTable();
        setupContextMenu();

        scrollPane = new JScrollPane(table);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());

        add(scrollPane, BorderLayout.CENTER);
    }

    private void setupTable() {
        table.setRowSorter(sorter);
        table.setShowGrid(false);
        table.setIntercellSpacing(new java.awt.Dimension(0, 0));
        table.setRowHeight(UIConstants.TABLE_ROW_HEIGHT + 4);
        table.setBackground(JStudioTheme.getBgTertiary());
        table.setForeground(JStudioTheme.getTextPrimary());
        table.setSelectionBackground(JStudioTheme.getSelection());
        table.setSelectionForeground(JStudioTheme.getTextPrimary());
        table.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_NORMAL));
        table.setFillsViewportHeight(true);

        // Selection mode
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // Custom renderer
        FileListRenderer renderer = new FileListRenderer(model);
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        // Column widths
        setupColumnWidths();

        // Header styling
        JTableHeader header = table.getTableHeader();
        header.setBackground(JStudioTheme.getBgSecondary());
        header.setForeground(JStudioTheme.getTextPrimary());
        header.setFont(JStudioTheme.getUIFont(11));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));
        header.setReorderingAllowed(false);

        // Click header to sort
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int column = header.columnAtPoint(e.getPoint());
                if (column >= 0 && column != FileListModel.COL_ICON) {
                    model.sortBy(column);
                }
            }
        });

        // Double-click to open
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        handleDoubleClick(row);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }

            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row >= 0 && !table.isRowSelected(row)) {
                        table.setRowSelectionInterval(row, row);
                    }
                    showContextMenu(e);
                }
            }
        });

        // Keyboard navigation
        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyPress(e);
            }

            @Override
            public void keyTyped(KeyEvent e) {
                handleKeyTyped(e);
            }
        });

        // Selection listener
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting() && listener != null) {
                    List<File> selected = getSelectedFiles();
                    listener.onSelectionChanged(selected);
                }
            }
        });
    }

    private void setupColumnWidths() {
        TableColumn iconCol = table.getColumnModel().getColumn(FileListModel.COL_ICON);
        iconCol.setMinWidth(28);
        iconCol.setMaxWidth(28);
        iconCol.setPreferredWidth(28);

        TableColumn nameCol = table.getColumnModel().getColumn(FileListModel.COL_NAME);
        nameCol.setPreferredWidth(200);

        TableColumn sizeCol = table.getColumnModel().getColumn(FileListModel.COL_SIZE);
        sizeCol.setPreferredWidth(80);

        TableColumn dateCol = table.getColumnModel().getColumn(FileListModel.COL_DATE);
        dateCol.setPreferredWidth(130);

        TableColumn typeCol = table.getColumnModel().getColumn(FileListModel.COL_TYPE);
        typeCol.setPreferredWidth(100);
    }

    private void setupContextMenu() {
        contextMenu = new JPopupMenu();
        styleMenu(contextMenu);
    }

    private void showContextMenu(MouseEvent e) {
        contextMenu.removeAll();

        File selectedFile = getSelectedFile();
        boolean hasSelection = selectedFile != null;
        boolean isFolder = hasSelection && selectedFile.isDirectory();
        QuickAccessManager manager = QuickAccessManager.getInstance();

        if (isFolder) {
            if (manager.isPinned(selectedFile)) {
                addMenuItem(contextMenu, "Unpin from Quick Access", () -> manager.removePinned(selectedFile));
            } else {
                addMenuItem(contextMenu, "Pin to Quick Access", () -> manager.addPinned(selectedFile));
            }
            contextMenu.addSeparator();
        }

        if (mode == FileChooserMode.SAVE_FILE) {
            addMenuItem(contextMenu, "New Folder", this::createNewFolder);

            if (hasSelection) {
                contextMenu.addSeparator();
                addMenuItem(contextMenu, "Delete", this::deleteSelectedFiles);
            }
        }

        if (contextMenu.getComponentCount() > 0) {
            contextMenu.show(e.getComponent(), e.getX(), e.getY());
        }
    }

    private void styleMenu(JPopupMenu menu) {
        menu.setBackground(JStudioTheme.getBgSecondary());
        menu.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));
    }

    private void addMenuItem(JPopupMenu menu, String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setBackground(JStudioTheme.getBgSecondary());
        item.setForeground(JStudioTheme.getTextPrimary());
        item.addActionListener(e -> action.run());
        menu.add(item);
    }

    private void handleDoubleClick(int viewRow) {
        int modelRow = table.convertRowIndexToModel(viewRow);
        File file = model.getFileAt(modelRow);

        if (file == null) {
            return;
        }

        if (file.isDirectory()) {
            // Navigate into directory
            if (listener != null) {
                listener.onDirectoryEntered(file);
            }
        } else {
            // File selected (double-click = confirm)
            if (listener != null) {
                listener.onFileDoubleClicked(file);
            }
        }
    }

    private void handleKeyPress(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ENTER:
                int[] selectedRows = table.getSelectedRows();
                if (selectedRows.length == 1) {
                    handleDoubleClick(selectedRows[0]);
                }
                e.consume();
                break;

            case KeyEvent.VK_BACK_SPACE:
                // Go up to parent
                if (currentDirectory != null && listener != null) {
                    File parent = currentDirectory.getParentFile();
                    if (parent != null) {
                        listener.onDirectoryEntered(parent);
                    }
                }
                e.consume();
                break;

            case KeyEvent.VK_HOME:
                if (table.getRowCount() > 0) {
                    table.setRowSelectionInterval(0, 0);
                    table.scrollRectToVisible(table.getCellRect(0, 0, true));
                }
                e.consume();
                break;

            case KeyEvent.VK_END:
                if (table.getRowCount() > 0) {
                    int lastRow = table.getRowCount() - 1;
                    table.setRowSelectionInterval(lastRow, lastRow);
                    table.scrollRectToVisible(table.getCellRect(lastRow, 0, true));
                }
                e.consume();
                break;

            case KeyEvent.VK_DELETE:
                // Only in save mode with confirmation
                if (mode == FileChooserMode.SAVE_FILE) {
                    deleteSelectedFiles();
                }
                e.consume();
                break;
        }
    }

    private void handleKeyTyped(KeyEvent e) {
        char c = e.getKeyChar();

        // Ignore control characters
        if (Character.isISOControl(c)) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastKeyTime > TYPE_AHEAD_TIMEOUT) {
            typeAheadBuffer.setLength(0);
        }
        lastKeyTime = currentTime;

        typeAheadBuffer.append(c);
        String prefix = typeAheadBuffer.toString();

        // Find matching file
        int row = model.findByPrefix(prefix);
        if (row >= 0) {
            int viewRow = table.convertRowIndexToView(row);
            table.setRowSelectionInterval(viewRow, viewRow);
            table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
        }
    }

    private void createNewFolder() {
        if (currentDirectory == null) {
            return;
        }

        String name = JOptionPane.showInputDialog(this, "Folder name:",
                "New Folder", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) {
            return;
        }

        File newFolder = new File(currentDirectory, name.trim());
        if (newFolder.exists()) {
            JOptionPane.showMessageDialog(this, "A folder with this name already exists.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (newFolder.mkdir()) {
            // Refresh and select new folder
            FileSystemWorker.invalidateCache(currentDirectory);
            if (listener != null) {
                listener.onDirectoryEntered(currentDirectory);
            }
        } else {
            JOptionPane.showMessageDialog(this, "Failed to create folder.",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedFiles() {
        List<File> selected = getSelectedFiles();
        if (selected.isEmpty()) {
            return;
        }

        String message = selected.size() == 1 ?
                "Delete '" + selected.get(0).getName() + "'?" :
                "Delete " + selected.size() + " items?";

        int result = JOptionPane.showConfirmDialog(this, message,
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (result == JOptionPane.YES_OPTION) {
            for (File file : selected) {
                if (!file.delete()) {
                    JOptionPane.showMessageDialog(this,
                            "Failed to delete: " + file.getName(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
            // Refresh
            FileSystemWorker.invalidateCache(currentDirectory);
            if (listener != null && currentDirectory != null) {
                listener.onDirectoryEntered(currentDirectory);
            }
        }
    }

    /**
     * Set the file list listener.
     */
    public void setFileListListener(FileListListener listener) {
        this.listener = listener;
    }

    /**
     * Set the chooser mode (affects selection behavior).
     */
    public void setMode(FileChooserMode mode) {
        this.mode = mode;

        // Adjust selection mode based on mode
        if (mode == FileChooserMode.SELECT_DIRECTORY) {
            // Only show directories - handled by filter
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        } else if (mode == FileChooserMode.SAVE_FILE) {
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        } else {
            table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        }
    }

    /**
     * Set the files to display.
     */
    public void setFiles(List<File> files, File directory) {
        this.currentDirectory = directory;
        model.setCurrentDirectory(directory);
        model.setFiles(files);

        // Clear selection
        table.clearSelection();

        // Scroll to top
        if (table.getRowCount() > 0) {
            table.scrollRectToVisible(table.getCellRect(0, 0, true));
        }
    }

    /**
     * Get the selected file (first if multiple).
     */
    public File getSelectedFile() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        return model.getFileAt(modelRow);
    }

    /**
     * Get all selected files.
     */
    public List<File> getSelectedFiles() {
        int[] viewRows = table.getSelectedRows();
        int[] modelRows = new int[viewRows.length];
        for (int i = 0; i < viewRows.length; i++) {
            modelRows[i] = table.convertRowIndexToModel(viewRows[i]);
        }
        return model.getFilesAt(modelRows);
    }

    /**
     * Select a file by name.
     */
    public void selectFile(String name) {
        for (int i = 0; i < model.getRowCount(); i++) {
            FileListModel.FileEntry entry = model.getEntryAt(i);
            if (entry != null && entry.getName().equalsIgnoreCase(name)) {
                int viewRow = table.convertRowIndexToView(i);
                table.setRowSelectionInterval(viewRow, viewRow);
                table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
                break;
            }
        }
    }

    /**
     * Get the model.
     */
    public FileListModel getModel() {
        return model;
    }

    /**
     * Get the table.
     */
    public JTable getTable() {
        return table;
    }

    /**
     * Request focus on the table.
     */
    public void focusTable() {
        table.requestFocusInWindow();
    }
}
