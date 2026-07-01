package com.tonic.ui.editor.source;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * A read-only (but selectable), syntax-highlighted, theme-styled Java source view. A thin wrapper over
 * {@link JavaEditorFactory} so callers that cannot depend on {@code RSyntaxTextArea} directly (e.g. plugins,
 * whose classpath excludes the editor library) can still show highlighted Java. Not editable; call
 * {@link #setSource(String)} to replace the displayed text.
 */
public final class ReadonlyJavaView extends JPanel {

    private final RSyntaxTextArea editor;

    public ReadonlyJavaView(String source) {
        super(new BorderLayout());
        editor = JavaEditorFactory.createEditor(false);
        RTextScrollPane scrollPane = JavaEditorFactory.createScrollPane(editor);
        JavaEditorFactory.applyTheme(editor, scrollPane);
        add(scrollPane, BorderLayout.CENTER);
        setSource(source);
    }

    /** Replaces the displayed source and scrolls back to the top. */
    public void setSource(String source) {
        editor.setText(source == null ? "" : source);
        editor.setCaretPosition(0);
    }
}
