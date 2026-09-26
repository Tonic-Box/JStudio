package com.tonic.ui.dialog.filechooser;

/** What a file chooser selects. */
public enum FileChooserMode
{
    /** Open one or more existing files. */
    OPEN_FILE,

    /** Choose a location and name to save a file. */
    SAVE_FILE,

    /** Select a single directory. */
    SELECT_DIRECTORY
}
