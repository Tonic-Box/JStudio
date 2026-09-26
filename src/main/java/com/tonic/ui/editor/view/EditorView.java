package com.tonic.ui.editor.view;

/** The contract every editor tab content view exposes, so the editor tab can drive whichever view is current without casting; implementations are panels. */
public interface EditorView
{

    /** Builds the view's content; usually a no-op once loaded. */
    void refresh();

    /** Forces a fresh load, discarding any loaded state. */
    void reload();

    /**
     * Gets the view's content as text.
     *
     * @return the full content, or an empty string when the view is not text-backed
     */
    String getText();

    /** Copies the current selection, if any, to the system clipboard. */
    void copySelection();

    /**
     * Gets the selected text.
     *
     * @return the selection, or null when there is none or the view has no text
     */
    String getSelectedText();

    /**
     * Moves the caret to a line.
     *
     * @param line the 1-based line number
     */
    void goToLine(int line);

    /** Opens the view's find panel, if it has one. */
    void showFindDialog();

    /**
     * Scrolls to and selects the first occurrence of a text, where supported.
     *
     * @param text the text to find
     */
    void scrollToText(String text);

    /**
     * Highlights a line, or moves the caret there when the view cannot highlight.
     *
     * @param line the 1-based line number
     */
    void highlightLine(int line);

    /**
     * Sets the font size of the view's text.
     *
     * @param size the font size in points
     */
    void setFontSize(int size);

    /**
     * Turns word wrap on or off, where applicable.
     *
     * @param enabled whether to wrap
     */
    void setWordWrap(boolean enabled);
}
