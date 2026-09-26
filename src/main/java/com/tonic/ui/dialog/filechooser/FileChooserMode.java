package com.tonic.ui.dialog.filechooser;

/**
 * Defines the operation mode for the file chooser dialog.
 */
public enum FileChooserMode
{
    /**
     * Open one or more existing files.
     */
    OPEN_FILE,

    /**
     * Save a file (select location and name).
     */
    SAVE_FILE,

    /**
     * Select a directory only.
     */
    SELECT_DIRECTORY
}
