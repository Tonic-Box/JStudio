package com.tonic.ui.dialog.filechooser;

import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.util.Settings;
import lombok.Getter;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom file chooser dialog to replace JFileChooser.
 * Provides a modern, dark-themed file selection experience.
 */
public class FileChooserDialog extends JDialog {

    /**
     * -- GETTER --
     *  Get the panel for configuration.
     */
    @Getter
    private final FileChooserPanel panel;
    private FileChooserResult result = FileChooserResult.cancelled();

    /**
     * -- GETTER --
     *  Get the last used directory.
     */
    // Remember last used directory
    @Getter
    private static File lastDirectory = new File(System.getProperty("user.home"));

    /**
     * Create a new file chooser dialog.
     */
    public FileChooserDialog(Window owner, String title) {
        super(owner, title, Dialog.ModalityType.APPLICATION_MODAL);

        setBackground(JStudioTheme.getBgPrimary());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        panel = new FileChooserPanel();
        panel.setFileChooserListener(new FileChooserPanel.FileChooserListener() {
            @Override
            public void onFilesSelected(List<File> files) {
                if (!files.isEmpty()) {
                    result = FileChooserResult.approved(files);
                    // Remember directory
                    File first = files.get(0);
                    File dir = first.isDirectory() ? first : first.getParentFile();

                    if (dir != null) {
                        lastDirectory = dir;
                        Settings.getInstance().setLastDirectory(dir.getAbsolutePath());
                    }
                }
                dispose();
            }

            @Override
            public void onCancelled() {
                result = FileChooserResult.cancelled();
                dispose();
            }
        });

        add(panel);
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Show the dialog and return the result.
     */
    public FileChooserResult showDialog() {
        setVisible(true);
        return result;
    }

    // ======================== Static Factory Methods ========================

    /**
     * Show an open file dialog.
     */
    public static FileChooserResult showOpenDialog(Component parent, ExtensionFileFilter... filters) {
        return builder()
                .mode(FileChooserMode.OPEN_FILE)
                .title("Open File")
                .filters(filters)
                .build(parent)
                .showDialog();
    }

    /**
     * Show an open file dialog with a title.
     */
    public static FileChooserResult showOpenDialog(Component parent, String title, ExtensionFileFilter... filters) {
        return builder()
                .mode(FileChooserMode.OPEN_FILE)
                .title(title)
                .filters(filters)
                .build(parent)
                .showDialog();
    }

    /**
     * Show a save file dialog.
     */
    public static FileChooserResult showSaveDialog(Component parent, String suggestedName) {
        return builder()
                .mode(FileChooserMode.SAVE_FILE)
                .title("Save File")
                .fileName(suggestedName)
                .build(parent)
                .showDialog();
    }

    /**
     * Show a save file dialog with filters.
     */
    public static FileChooserResult showSaveDialog(Component parent, String suggestedName, ExtensionFileFilter... filters) {
        return builder()
                .mode(FileChooserMode.SAVE_FILE)
                .title("Save File")
                .fileName(suggestedName)
                .filters(filters)
                .build(parent)
                .showDialog();
    }

    /**
     * Show a directory selection dialog.
     */
    public static FileChooserResult showDirectoryDialog(Component parent) {
        return builder()
                .mode(FileChooserMode.SELECT_DIRECTORY)
                .title("Select Folder")
                .build(parent)
                .showDialog();
    }

    /**
     * Show a directory selection dialog with a title.
     */
    public static FileChooserResult showDirectoryDialog(Component parent, String title) {
        return builder()
                .mode(FileChooserMode.SELECT_DIRECTORY)
                .title(title)
                .build(parent)
                .showDialog();
    }

    /**
     * Set the last used directory.
     */
    public static void setLastDirectory(File directory) {
        if (directory != null && directory.exists()) {
            lastDirectory = directory.isDirectory() ? directory : directory.getParentFile();
        }
    }

    // ======================== Builder Pattern ========================

    /**
     * Create a builder for advanced configuration.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating customized file chooser dialogs.
     */
    public static class Builder {
        private FileChooserMode mode = FileChooserMode.OPEN_FILE;
        private String title = "Select File";
        private File initialDirectory = lastDirectory;
        private String initialFileName = "";
        private final List<ExtensionFileFilter> filters = new ArrayList<>();
        private boolean useLastDirectory = true;

        private Builder() {
            // Add default filters
        }

        /**
         * Set the dialog mode.
         */
        public Builder mode(FileChooserMode mode) {
            this.mode = mode;
            return this;
        }

        /**
         * Set the dialog title.
         */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /**
         * Set the initial directory.
         */
        public Builder directory(File directory) {
            this.initialDirectory = directory;
            this.useLastDirectory = false;
            return this;
        }

        /**
         * Set the initial file name.
         */
        public Builder fileName(String name) {
            this.initialFileName = name;
            return this;
        }

        /**
         * Add file filters.
         */
        public Builder filters(ExtensionFileFilter... filters) {
            if (filters != null) {
                for (ExtensionFileFilter filter : filters) {
                    if (filter != null) {
                        this.filters.add(filter);
                    }
                }
            }
            return this;
        }

        /**
         * Add a single filter.
         */
        public Builder filter(ExtensionFileFilter filter) {
            if (filter != null) {
                this.filters.add(filter);
            }
            return this;
        }

        /**
         * Whether to use the last used directory.
         */
        public Builder useLastDirectory(boolean use) {
            this.useLastDirectory = use;
            return this;
        }

        /**
         * Build and return the dialog.
         */
        public FileChooserDialog build(Component parent) {
            Window owner = getWindow(parent);
            FileChooserDialog dialog = new FileChooserDialog(owner, title);

            // Configure panel
            FileChooserPanel panel = dialog.getPanel();
            panel.setMode(mode);

            // Set filters
            if (!filters.isEmpty()) {
                panel.setFileFilters(filters.toArray(new ExtensionFileFilter[0]));
            }

            // Set initial directory
            File startDir;
            if (useLastDirectory) {
                String saved = Settings.getInstance().getLastDirectory();
                startDir = new File(saved);
            } else {
                startDir = initialDirectory;
            }

            if (startDir != null && startDir.exists() && startDir.isDirectory()) {
                panel.setCurrentDirectory(startDir);
            } else {
                panel.setCurrentDirectory(new File(System.getProperty("user.home")));
            }

            // Set initial file name
            if (initialFileName != null && !initialFileName.isEmpty()) {
                panel.setSelectedFileName(initialFileName);
            }

            return dialog;
        }

        /**
         * Get the parent window.
         */
        private Window getWindow(Component component) {
            if (component == null) {
                return null;
            }
            if (component instanceof Window) {
                return (Window) component;
            }
            return SwingUtilities.getWindowAncestor(component);
        }
    }
}
