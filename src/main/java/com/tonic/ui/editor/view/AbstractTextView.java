package com.tonic.ui.editor.view;

import com.tonic.ui.editor.SearchPanel;
import com.tonic.ui.theme.JStudioTheme;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

/**
 * Base for {@link org.fife.ui.rsyntaxtextarea.RSyntaxTextArea}-backed editor views. Owns the read-only code text
 * area, its line-numbered scroll pane and a find panel, the shared code-area setup + theming, and the text-backed
 * implementations of the common {@link EditorView} text operations. Subclasses add their own syntax/token styling
 * (in {@code applyChildThemes}, after calling {@link #applyTextTheme()}) and override the ops that differ.
 */
public abstract class AbstractTextView extends AbstractEditorView {

    protected RSyntaxTextArea textArea;
    protected RTextScrollPane scrollPane;
    protected SearchPanel searchPanel;

    /** Creates the read-only code text area, its line-numbered scroll pane, and a find panel. */
    protected void initTextArea(String syntaxStyle) {
        textArea = new RSyntaxTextArea();
        textArea.setSyntaxEditingStyle(syntaxStyle);
        textArea.setEditable(false);
        textArea.setAntiAliasingEnabled(true);
        textArea.setFont(JStudioTheme.getCodeFont(12));
        textArea.setCodeFoldingEnabled(false);
        textArea.setBracketMatchingEnabled(false);

        scrollPane = new RTextScrollPane(textArea);
        scrollPane.setLineNumbersEnabled(true);
        scrollPane.setBorder(null);

        searchPanel = new SearchPanel(textArea, scrollPane);
    }

    /**
     * Applies the shared code-area + gutter colors. Subclasses call this from {@code applyChildThemes()} and then
     * apply their own syntax-scheme token styling.
     */
    protected void applyTextTheme() {
        setBackground(JStudioTheme.getBgTertiary());

        textArea.setBackground(JStudioTheme.getBgTertiary());
        textArea.setForeground(JStudioTheme.getTextPrimary());
        textArea.setCaretColor(JStudioTheme.getTextPrimary());
        textArea.setSelectionColor(JStudioTheme.getSelection());
        textArea.setCurrentLineHighlightColor(JStudioTheme.getLineHighlight());
        textArea.setFadeCurrentLineHighlight(true);

        scrollPane.getGutter().setBackground(JStudioTheme.getBgSecondary());
        scrollPane.getGutter().setLineNumberColor(JStudioTheme.getTextSecondary());
        scrollPane.getGutter().setBorderColor(JStudioTheme.getBorder());
    }

    @Override
    public String getText() {
        return textArea.getText();
    }

    @Override
    public void copySelection() {
        copyToClipboard(textArea.getSelectedText());
    }

    @Override
    public String getSelectedText() {
        return textArea.getSelectedText();
    }

    @Override
    public void goToLine(int line) {
        try {
            int offset = textArea.getLineStartOffset(line - 1);
            textArea.setCaretPosition(offset);
            textArea.requestFocus();
        } catch (Exception e) {
            // Line out of range
        }
    }

    @Override
    public void showFindDialog() {
        searchPanel.showPanel();
    }

    @Override
    public void setFontSize(int size) {
        textArea.setFont(JStudioTheme.getCodeFont(size));
    }

    @Override
    public void setWordWrap(boolean enabled) {
        textArea.setLineWrap(enabled);
        textArea.setWrapStyleWord(enabled);
    }
}
