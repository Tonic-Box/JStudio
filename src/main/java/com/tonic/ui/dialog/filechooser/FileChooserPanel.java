package com.tonic.ui.dialog.filechooser;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.util.QuickAccessManager;
import lombok.Getter;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** The body of the file chooser: path bar, quick access list, file list, file name field, filter combo and action buttons. */
public class FileChooserPanel extends ThemedJPanel
{

    /** A callback for the chooser being approved or cancelled. */
    public interface FileChooserListener
    {
        /**
         * Called when the user approves a selection.
         *
         * @param files the chosen files, never empty
         */
        void onFilesSelected(List<File> files);

        /** Called when the user cancels. */
        void onCancelled();
    }

    private FileChooserMode mode = FileChooserMode.OPEN_FILE;
    private FileChooserListener listener;
    /** The directory being shown, or null before the first navigation. */
    @Getter
    private File currentDirectory;

    private final PathBar pathBar;
    private final QuickAccessPanel quickAccessPanel;
    private final FileListPanel fileListPanel;
    private final FileFilterComboBox filterComboBox;

    private final JTextField fileNameField;
    private final JButton actionButton;
    private final JButton cancelButton;

    private int navigation;
    private ExtensionFileFilter currentFilter;

    /** Creates the panel in open-file mode with no directory shown. */
    public FileChooserPanel()
    {
        super(BackgroundStyle.PRIMARY, new BorderLayout());
        setPreferredSize(new Dimension(800, 500));

        pathBar = new PathBar(this::navigateTo);
        quickAccessPanel = new QuickAccessPanel(this::navigateTo);
        fileListPanel = new FileListPanel();
        filterComboBox = new FileFilterComboBox();

        fileListPanel.setFileListListener(new FileListPanel.FileListListener()
        {
            @Override
            public void onFileDoubleClicked(File file)
            {
                handleFileDoubleClicked(file);
            }

            @Override
            public void onSelectionChanged(List<File> selectedFiles)
            {
                handleSelectionChanged(selectedFiles);
            }

            @Override
            public void onDirectoryEntered(File directory)
            {
                navigateTo(directory);
            }

            @Override
            public void onNewFolderRequested()
            {
                createNewFolder();
            }
        });

        filterComboBox.setFilterChangeListener(filter ->
        {
            currentFilter = filter;
            refreshFileList();
        });

        fileNameField = new JTextField();
        actionButton = createStyledButton("Open");
        cancelButton = createStyledButton("Cancel");

        setupLayout();
        setupKeyboardShortcuts();
    }

    private void setupLayout()
    {
        add(pathBar, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(quickAccessPanel);
        splitPane.setRightComponent(fileListPanel);
        splitPane.setDividerLocation(180);
        splitPane.setDividerSize(4);
        splitPane.setBorder(null);
        splitPane.setBackground(JStudioTheme.getBorder());

        add(splitPane, BorderLayout.CENTER);

        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createBottomPanel()
    {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(JStudioTheme.getBgSecondary());
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()), BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        JPanel nameRow = new JPanel(new BorderLayout(8, 0));
        nameRow.setOpaque(false);

        JLabel nameLabel = new JLabel("File name:");
        nameLabel.setForeground(JStudioTheme.getTextPrimary());
        nameLabel.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_NORMAL));
        nameLabel.setPreferredSize(new Dimension(70, 24));

        fileNameField.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_NORMAL));
        fileNameField.setBackground(JStudioTheme.getBgTertiary());
        fileNameField.setForeground(JStudioTheme.getTextPrimary());
        fileNameField.setCaretColor(JStudioTheme.getTextPrimary());
        fileNameField.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()), BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        fileNameField.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e)
            {
                validateFileName();
            }

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                validateFileName();
            }

            @Override
            public void changedUpdate(DocumentEvent e)
            {
                validateFileName();
            }
        });

        fileNameField.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                if (e.getKeyCode() == KeyEvent.VK_ENTER)
                {
                    handleActionButton();
                }
            }
        });

        nameRow.add(nameLabel, BorderLayout.WEST);
        nameRow.add(fileNameField, BorderLayout.CENTER);
        nameRow.add(filterComboBox, BorderLayout.EAST);

        panel.add(nameRow, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonRow.setOpaque(false);

        actionButton.addActionListener(e -> handleActionButton());
        cancelButton.addActionListener(e -> handleCancelButton());

        buttonRow.add(actionButton);
        buttonRow.add(cancelButton);

        panel.add(buttonRow, BorderLayout.SOUTH);

        return panel;
    }

    private JButton createStyledButton(String text)
    {
        JButton button = new JButton(text);
        button.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_NORMAL));
        button.setPreferredSize(new Dimension(90, 28));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        if (text.equals("Cancel"))
        {
            button.setBackground(JStudioTheme.getBgTertiary());
            button.setForeground(JStudioTheme.getTextPrimary());
            button.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));
        }
        else
        {
            button.setBackground(JStudioTheme.getAccent());
            button.setForeground(JStudioTheme.getTextPrimary());
            button.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        }

        button.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                if (button.isEnabled())
                {
                    button.setBackground(text.equals("Cancel") ? JStudioTheme.getHover() : JStudioTheme.getAccent().brighter());
                }
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                button.setBackground(text.equals("Cancel") ? JStudioTheme.getBgTertiary() : JStudioTheme.getAccent());
            }
        });

        return button;
    }

    private void setupKeyboardShortcuts()
    {
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK), "focusPathBar");
        getActionMap().put("focusPathBar", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                pathBar.focusPathBar();
            }
        });

        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        getActionMap().put("cancel", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                handleCancelButton();
            }
        });

        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK), "newFolder");
        getActionMap().put("newFolder", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if (mode == FileChooserMode.SAVE_FILE)
                {
                    createNewFolder();
                }
            }
        });
    }

    /**
     * Lists a directory in the background and shows it, recording it as a recent location; a later navigation supersedes one still loading, and a directory that does not exist is ignored.
     *
     * @param directory the directory
     */
    public void navigateTo(File directory)
    {
        navigateTo(directory, null);
    }

    private void navigateTo(File directory, String selectName)
    {
        if (directory == null || !directory.exists() || !directory.isDirectory())
        {
            return;
        }

        int request = ++navigation;
        currentDirectory = directory;
        pathBar.setCurrentDirectory(directory);

        FileSystemWorker.listDirectory(directory, currentFilter, new FileSystemWorker.DirectoryListingListener()
        {
            @Override
            public void onListingComplete(File dir, List<File> files)
            {
                SwingUtilities.invokeLater(() ->
                {
                    if (request != navigation)
                    {
                        return;
                    }
                    fileListPanel.setFiles(filterFilesByMode(files), directory);
                    QuickAccessManager.getInstance().addRecent(directory);
                    if (selectName != null)
                    {
                        fileListPanel.selectFile(selectName);
                    }
                    fileListPanel.focusTable();
                });
            }

            @Override
            public void onListingError(File dir, Exception e)
            {
                SwingUtilities.invokeLater(() ->
                {
                    if (request != navigation)
                    {
                        return;
                    }
                    fileListPanel.setFiles(new ArrayList<>(), directory);
                    JOptionPane.showMessageDialog(FileChooserPanel.this, "Cannot list " + dir.getAbsolutePath() + ": " + e.getMessage(), "Cannot Open Folder", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private List<File> filterFilesByMode(List<File> files)
    {
        if (mode == FileChooserMode.SELECT_DIRECTORY)
        {
            List<File> result = new ArrayList<>();
            for (File file : files)
            {
                if (file.isDirectory())
                {
                    result.add(file);
                }
            }
            return result;
        }

        return files;
    }

    /** Drops the cached listing of the current directory and lists it again. */
    public void refreshFileList()
    {
        if (currentDirectory != null)
        {
            FileSystemWorker.invalidateCache(currentDirectory);
            navigateTo(currentDirectory);
        }
    }

    private void handleFileDoubleClicked(File file)
    {
        if (file.isDirectory())
        {
            navigateTo(file);
        }
        else
        {
            fileNameField.setText(file.getName());
            handleActionButton();
        }
    }

    private void handleSelectionChanged(List<File> selectedFiles)
    {
        if (selectedFiles.isEmpty())
        {
            return;
        }

        if (mode == FileChooserMode.SELECT_DIRECTORY)
        {
            for (File file : selectedFiles)
            {
                if (file.isDirectory())
                {
                    fileNameField.setText(file.getName());
                    break;
                }
            }
        }
        else if (selectedFiles.size() == 1)
        {
            File file = selectedFiles.get(0);
            if (!file.isDirectory())
            {
                fileNameField.setText(file.getName());
            }
        }
        else
        {
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (File file : selectedFiles)
            {
                if (!file.isDirectory())
                {
                    if (sb.length() > 0)
                    {
                        sb.append("; ");
                    }
                    sb.append("\"").append(file.getName()).append("\"");
                    count++;
                }
            }
            if (count > 0)
            {
                fileNameField.setText(sb.toString());
            }
        }
    }

    private void validateFileName()
    {
        String text = fileNameField.getText().trim();

        if (mode == FileChooserMode.SAVE_FILE)
        {
            actionButton.setEnabled(!text.isEmpty());
        }
        else
        {
            actionButton.setEnabled(!text.isEmpty());
        }
    }

    private void handleActionButton()
    {
        String text = fileNameField.getText().trim();
        if (text.isEmpty())
        {
            return;
        }

        File asAbsolute = new File(text);
        if (asAbsolute.isAbsolute())
        {
            if (asAbsolute.exists())
            {
                if (asAbsolute.isDirectory())
                {
                    if (mode == FileChooserMode.SELECT_DIRECTORY)
                    {
                        if (listener != null)
                        {
                            List<File> selected = new ArrayList<>();
                            selected.add(asAbsolute);
                            listener.onFilesSelected(selected);
                        }
                    }
                    else
                    {
                        navigateTo(asAbsolute);
                        fileNameField.setText("");
                    }
                    return;
                }
                else
                {
                    if (listener != null)
                    {
                        List<File> selected = new ArrayList<>();
                        selected.add(asAbsolute);
                        listener.onFilesSelected(selected);
                    }
                    return;
                }
            }
            else
            {
                if (mode == FileChooserMode.SAVE_FILE)
                {
                    if (listener != null)
                    {
                        List<File> selected = new ArrayList<>();
                        selected.add(asAbsolute);
                        listener.onFilesSelected(selected);
                    }
                    return;
                }
            }
        }

        List<File> selectedFiles = new ArrayList<>();

        if (mode == FileChooserMode.SAVE_FILE)
        {
            File file = new File(currentDirectory, text);
            selectedFiles.add(file);
        }
        else if (text.contains(";"))
        {
            String[] parts = text.split(";");
            for (String part : parts)
            {
                String name = part.trim();
                if (name.startsWith("\"") && name.endsWith("\""))
                {
                    name = name.substring(1, name.length() - 1);
                }
                if (!name.isEmpty())
                {
                    File file = new File(currentDirectory, name);
                    if (file.exists())
                    {
                        selectedFiles.add(file);
                    }
                }
            }
        }
        else
        {
            File file = new File(currentDirectory, text);
            if (file.exists())
            {
                if (file.isDirectory() && mode != FileChooserMode.SELECT_DIRECTORY)
                {
                    navigateTo(file);
                    return;
                }
                selectedFiles.add(file);
            }
            else if (mode == FileChooserMode.SELECT_DIRECTORY)
            {
                selectedFiles.add(currentDirectory);
            }
        }

        if (!selectedFiles.isEmpty() && listener != null)
        {
            listener.onFilesSelected(selectedFiles);
        }
    }

    private void handleCancelButton()
    {
        if (listener != null)
        {
            listener.onCancelled();
        }
    }

    private void createNewFolder()
    {
        String name = JOptionPane.showInputDialog(this, "Folder name:", "New Folder", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty())
        {
            return;
        }

        File newFolder = new File(currentDirectory, name.trim());
        if (newFolder.exists())
        {
            JOptionPane.showMessageDialog(this, "A folder with this name already exists.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (newFolder.mkdir())
        {
            FileSystemWorker.invalidateCache(currentDirectory);
            navigateTo(currentDirectory, newFolder.getName());
        }
        else
        {
            JOptionPane.showMessageDialog(this, "Failed to create folder.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Sets what the chooser selects, relabels the action button and relists the directory.
     *
     * @param mode the mode
     */
    public void setMode(FileChooserMode mode)
    {
        this.mode = mode;
        fileListPanel.setMode(mode);

        switch (mode)
        {
            case OPEN_FILE:
                actionButton.setText("Open");
                break;
            case SAVE_FILE:
                actionButton.setText("Save");
                break;
            case SELECT_DIRECTORY:
                actionButton.setText("Select");
                break;
        }

        refreshFileList();
    }

    /**
     * Sets the listener told about approval and cancellation, replacing any previous one.
     *
     * @param listener the listener
     */
    public void setFileChooserListener(FileChooserListener listener)
    {
        this.listener = listener;
    }

    /**
     * Shows a directory, as navigateTo does.
     *
     * @param directory the directory
     */
    public void setCurrentDirectory(File directory)
    {
        navigateTo(directory);
    }

    /**
     * Fills the file name field.
     *
     * @param name the file name
     */
    public void setSelectedFileName(String name)
    {
        fileNameField.setText(name);
    }

    /**
     * The file name as currently typed.
     *
     * @return the field's text, trimmed
     */
    public String getSelectedFileName()
    {
        return fileNameField.getText().trim();
    }

    /**
     * Sets the filters offered, selecting the first.
     *
     * @param filters the filters, in order; All Files is always added
     */
    public void setFileFilters(ExtensionFileFilter... filters)
    {
        filterComboBox.setFilters(filters);
        if (filters != null && filters.length > 0)
        {
            currentFilter = filters[0];
        }
    }

    /**
     * The selected filter.
     *
     * @return the filter, or null if none is selected
     */
    public ExtensionFileFilter getSelectedFilter()
    {
        return filterComboBox.getSelectedFilter();
    }

    /**
     * The files selected in the list.
     *
     * @return the selected files, empty if none
     */
    public List<File> getSelectedFiles()
    {
        return fileListPanel.getSelectedFiles();
    }

    /**
     * The file named in the file name field, resolved against the current directory; for a list of quoted names, the first one.
     *
     * @return the file, or null if the field is empty
     */
    public File getSelectedFile()
    {
        String text = fileNameField.getText().trim();
        if (text.isEmpty())
        {
            return null;
        }

        if (text.contains(";"))
        {
            String first = text.split(";")[0].trim();
            if (first.startsWith("\"") && first.endsWith("\""))
            {
                first = first.substring(1, first.length() - 1);
            }
            return new File(currentDirectory, first);
        }

        return new File(currentDirectory, text);
    }

    /** Focuses the file name field and selects its text. */
    public void focusFileNameField()
    {
        fileNameField.requestFocusInWindow();
        fileNameField.selectAll();
    }

    /** Focuses the file list. */
    public void focusFileList()
    {
        fileListPanel.focusTable();
    }
}
