package com.tonic.ui.dialog.filechooser;

import com.tonic.ui.theme.JStudioTheme;
import com.tonic.util.Settings;
import lombok.Getter;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** A modal, themed replacement for JFileChooser; approving a selection saves its directory to settings as the last directory. */
public class FileChooserDialog extends JDialog
{

    /** The panel, for configuring the dialog before it is shown. */
    @Getter
    private final FileChooserPanel panel;
    private FileChooserResult result = FileChooserResult.cancelled();

    @Getter
    private static File lastDirectory = new File(System.getProperty("user.home"));

    /**
     * Creates the dialog; configure it through its panel or use the builder.
     *
     * @param owner the window to center on and block, or null
     * @param title the title
     */
    public FileChooserDialog(Window owner, String title)
    {
        super(owner, title, Dialog.ModalityType.APPLICATION_MODAL);

        setBackground(JStudioTheme.getBgPrimary());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        panel = new FileChooserPanel();
        panel.setFileChooserListener(new FileChooserPanel.FileChooserListener()
        {
            @Override
            public void onFilesSelected(List<File> files)
            {
                if (!files.isEmpty())
                {
                    result = FileChooserResult.approved(files);
                    File first = files.get(0);
                    File dir = first.isDirectory() ? first : first.getParentFile();

                    if (dir != null)
                    {
                        lastDirectory = dir;
                        Settings.getInstance().setLastDirectory(dir.getAbsolutePath());
                    }
                }
                dispose();
            }

            @Override
            public void onCancelled()
            {
                result = FileChooserResult.cancelled();
                dispose();
            }
        });

        add(panel);
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Shows the dialog and blocks until it closes.
     *
     * @return the result, cancelled if the dialog was closed without approving
     */
    public FileChooserResult showDialog()
    {
        setVisible(true);
        return result;
    }

    /**
     * Shows an Open File dialog starting in the last directory.
     *
     * @param parent a component in the owning window, or null
     * @param filters the file filters, in order; All Files is always added
     * @return the result
     */
    public static FileChooserResult showOpenDialog(Component parent, ExtensionFileFilter... filters)
    {
        return builder()
                .mode(FileChooserMode.OPEN_FILE)
                .title("Open File")
                .filters(filters)
                .build(parent)
                .showDialog();
    }

    /**
     * Shows an open file dialog with a title, starting in the last directory.
     *
     * @param parent a component in the owning window, or null
     * @param title the title
     * @param filters the file filters, in order; All Files is always added
     * @return the result
     */
    public static FileChooserResult showOpenDialog(Component parent, String title, ExtensionFileFilter... filters)
    {
        return builder()
                .mode(FileChooserMode.OPEN_FILE)
                .title(title)
                .filters(filters)
                .build(parent)
                .showDialog();
    }

    /**
     * Shows a Save File dialog starting in the last directory.
     *
     * @param parent a component in the owning window, or null
     * @param suggestedName the initial file name, or null or empty for none
     * @return the result
     */
    public static FileChooserResult showSaveDialog(Component parent, String suggestedName)
    {
        return builder()
                .mode(FileChooserMode.SAVE_FILE)
                .title("Save File")
                .fileName(suggestedName)
                .build(parent)
                .showDialog();
    }

    /**
     * Shows a Save File dialog with filters, starting in the last directory.
     *
     * @param parent a component in the owning window, or null
     * @param suggestedName the initial file name, or null or empty for none
     * @param filters the file filters, in order; All Files is always added
     * @return the result
     */
    public static FileChooserResult showSaveDialog(Component parent, String suggestedName, ExtensionFileFilter... filters)
    {
        return builder()
                .mode(FileChooserMode.SAVE_FILE)
                .title("Save File")
                .fileName(suggestedName)
                .filters(filters)
                .build(parent)
                .showDialog();
    }

    /**
     * Shows a Select Folder dialog starting in the last directory.
     *
     * @param parent a component in the owning window, or null
     * @return the result
     */
    public static FileChooserResult showDirectoryDialog(Component parent)
    {
        return builder()
                .mode(FileChooserMode.SELECT_DIRECTORY)
                .title("Select Folder")
                .build(parent)
                .showDialog();
    }

    /**
     * Shows a folder selection dialog with a title, starting in the last directory.
     *
     * @param parent a component in the owning window, or null
     * @param title the title
     * @return the result
     */
    public static FileChooserResult showDirectoryDialog(Component parent, String title)
    {
        return builder()
                .mode(FileChooserMode.SELECT_DIRECTORY)
                .title(title)
                .build(parent)
                .showDialog();
    }

    /**
     * Sets the in-memory last directory, using a file's parent if given a file; ignores null and missing paths.
     *
     * @param directory the directory or a file in it
     */
    public static void setLastDirectory(File directory)
    {
        if (directory != null && directory.exists())
        {
            lastDirectory = directory.isDirectory() ? directory : directory.getParentFile();
        }
    }

    /**
     * Starts building a dialog.
     *
     * @return a new builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /** Builder for a configured file chooser dialog; by default it opens a file, starting in the last directory saved to settings. */
    public static class Builder
    {
        private FileChooserMode mode = FileChooserMode.OPEN_FILE;
        private String title = "Select File";
        private File initialDirectory = lastDirectory;
        private String initialFileName = "";
        private final List<ExtensionFileFilter> filters = new ArrayList<>();
        private boolean useLastDirectory = true;

        private Builder()
        {
        }

        /**
         * Sets what the dialog selects.
         *
         * @param mode the mode
         * @return this builder
         */
        public Builder mode(FileChooserMode mode)
        {
            this.mode = mode;
            return this;
        }

        /**
         * Sets the title.
         *
         * @param title the title
         * @return this builder
         */
        public Builder title(String title)
        {
            this.title = title;
            return this;
        }

        /**
         * Sets the starting directory, which replaces the last directory.
         *
         * @param directory the directory; the home directory is used if it does not exist
         * @return this builder
         */
        public Builder directory(File directory)
        {
            this.initialDirectory = directory;
            this.useLastDirectory = false;
            return this;
        }

        /**
         * Sets the initial file name.
         *
         * @param name the name, or null or empty for none
         * @return this builder
         */
        public Builder fileName(String name)
        {
            this.initialFileName = name;
            return this;
        }

        /**
         * Adds file filters, skipping nulls.
         *
         * @param filters the filters, or null for none
         * @return this builder
         */
        public Builder filters(ExtensionFileFilter... filters)
        {
            if (filters != null)
            {
                for (ExtensionFileFilter filter : filters)
                {
                    if (filter != null)
                    {
                        this.filters.add(filter);
                    }
                }
            }
            return this;
        }

        /**
         * Adds one file filter.
         *
         * @param filter the filter, or null to add nothing
         * @return this builder
         */
        public Builder filter(ExtensionFileFilter filter)
        {
            if (filter != null)
            {
                this.filters.add(filter);
            }
            return this;
        }

        /**
         * Chooses whether to start in the last directory saved to settings or in the directory set with directory.
         *
         * @param use true for the last directory
         * @return this builder
         */
        public Builder useLastDirectory(boolean use)
        {
            this.useLastDirectory = use;
            return this;
        }

        /**
         * Creates the configured dialog, falling back to the home directory if the starting directory does not exist.
         *
         * @param parent a component in the owning window, or null
         * @return the dialog, not yet shown
         */
        public FileChooserDialog build(Component parent)
        {
            Window owner = getWindow(parent);
            FileChooserDialog dialog = new FileChooserDialog(owner, title);

            FileChooserPanel panel = dialog.getPanel();
            panel.setMode(mode);

            if (!filters.isEmpty())
            {
                panel.setFileFilters(filters.toArray(new ExtensionFileFilter[0]));
            }

            File startDir;
            if (useLastDirectory)
            {
                String saved = Settings.getInstance().getLastDirectory();
                startDir = new File(saved);
            }
            else
            {
                startDir = initialDirectory;
            }

            if (startDir != null && startDir.exists() && startDir.isDirectory())
            {
                panel.setCurrentDirectory(startDir);
            }
            else
            {
                panel.setCurrentDirectory(new File(System.getProperty("user.home")));
            }

            if (initialFileName != null && !initialFileName.isEmpty())
            {
                panel.setSelectedFileName(initialFileName);
            }

            return dialog;
        }

        private Window getWindow(Component component)
        {
            if (component == null)
            {
                return null;
            }
            if (component instanceof Window)
            {
                return (Window) component;
            }
            return SwingUtilities.getWindowAncestor(component);
        }
    }
}
