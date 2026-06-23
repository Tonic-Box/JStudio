package com.tonic.ui.editor.view;

/**
 * The contract every editor tab content view exposes, so the host ({@code EditorTab}) can drive whichever view is
 * current polymorphically instead of casting per concrete type. Implementations are all {@code JPanel}s. Most views
 * only implement a subset; {@link AbstractEditorView} supplies sensible no-op/fallback defaults for the rest.
 */
public interface EditorView {

    /** (Re)build the view's content; typically a no-op once already loaded. */
    void refresh();

    /** Force a fresh load, discarding any cached/loaded state. */
    void reload();

    /** The view's full content as text (empty string when not text-backed). */
    String getText();

    /** Copy the current selection to the system clipboard, if any. */
    void copySelection();

    /** The currently selected text, or null when none / not applicable. */
    String getSelectedText();

    /** Move the caret/selection to a 1-based line. */
    void goToLine(int line);

    /** Open the view's find UI, if it has one. */
    void showFindDialog();

    /** Scroll to and select the first occurrence of {@code text}, if supported. */
    void scrollToText(String text);

    /** Highlight a line (falls back to {@link #goToLine(int)} when the view has no highlight affordance). */
    void highlightLine(int line);

    /** Apply a font size to the view's text rendering. */
    void setFontSize(int size);

    /** Toggle word wrap, where applicable. */
    void setWordWrap(boolean enabled);
}
