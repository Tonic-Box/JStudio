package com.tonic.ui.dialog.filechooser;

import lombok.Getter;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** The outcome of a file chooser: whether it was approved and the chosen files. */
@Getter
public class FileChooserResult
{

    /** Whether the user approved the selection with Open or Save. */
    private final boolean approved;
    /** The chosen files, unmodifiable and empty when cancelled. */
    private final List<File> selectedFiles;

    private FileChooserResult(boolean approved, List<File> selectedFiles)
    {
        this.approved = approved;
        this.selectedFiles = selectedFiles != null ?
                List.copyOf(selectedFiles) :
                Collections.emptyList();
    }

    /**
     * Creates an approved result with one file.
     *
     * @param file the chosen file
     * @return the result
     * @throws NullPointerException if the file is null
     */
    public static FileChooserResult approved(File file)
    {
        return new FileChooserResult(true, Collections.singletonList(Objects.requireNonNull(file, "file")));
    }

    /**
     * Creates an approved result with several files.
     *
     * @param files the chosen files, copied; null for none
     * @return the result
     */
    public static FileChooserResult approved(List<File> files)
    {
        return new FileChooserResult(true, files);
    }

    /**
     * Creates a cancelled result with no files.
     *
     * @return the result
     */
    public static FileChooserResult cancelled()
    {
        return new FileChooserResult(false, null);
    }

    /**
     * Checks whether the user cancelled.
     *
     * @return true if the selection was not approved
     */
    public boolean isCancelled()
    {
        return !approved;
    }

    /**
     * The first chosen file.
     *
     * @return the file, or null if none was chosen
     */
    public File getSelectedFile()
    {
        return selectedFiles.isEmpty() ? null : selectedFiles.get(0);
    }

}
