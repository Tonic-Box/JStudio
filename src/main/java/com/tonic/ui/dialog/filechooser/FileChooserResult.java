package com.tonic.ui.dialog.filechooser;

import lombok.Getter;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Result from a file chooser dialog operation.
 */
@Getter
public class FileChooserResult {

    /**
     * -- GETTER --
     *  Whether the user approved the selection (clicked Open/Save).
     */
    private final boolean approved;
    /**
     * -- GETTER --
     *  Get all selected files.
     */
    private final List<File> selectedFiles;

    private FileChooserResult(boolean approved, List<File> selectedFiles) {
        this.approved = approved;
        this.selectedFiles = selectedFiles != null ?
                List.copyOf(selectedFiles) :
                Collections.emptyList();
    }

    /**
     * Create a result for when the user approved (clicked Open/Save).
     */
    public static FileChooserResult approved(File file) {
        return new FileChooserResult(true, Collections.singletonList(file));
    }

    /**
     * Create a result for when the user approved with multiple files.
     */
    public static FileChooserResult approved(List<File> files) {
        return new FileChooserResult(true, files);
    }

    /**
     * Create a result for when the user cancelled.
     */
    public static FileChooserResult cancelled() {
        return new FileChooserResult(false, null);
    }

    /**
     * Whether the user cancelled the dialog.
     */
    public boolean isCancelled() {
        return !approved;
    }

    /**
     * Get the selected file (first file if multiple were selected).
     */
    public File getSelectedFile() {
        return selectedFiles.isEmpty() ? null : selectedFiles.get(0);
    }

    /**
     * Check if any files were selected.
     */
    public boolean hasSelection() {
        return !selectedFiles.isEmpty();
    }

    /**
     * Get the count of selected files.
     */
    public int getSelectionCount() {
        return selectedFiles.size();
    }
}
