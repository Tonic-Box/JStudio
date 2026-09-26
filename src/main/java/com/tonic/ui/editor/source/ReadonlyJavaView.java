package com.tonic.ui.editor.source;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.JPanel;
import java.awt.BorderLayout;

/** A read-only, selectable, syntax-highlighted Java source view for callers that cannot depend on the editor library directly. */
public final class ReadonlyJavaView extends JPanel
{

    private final RSyntaxTextArea editor;

    /**
     * Creates the view showing the given source.
     *
     * @param source the Java source to show; null shows nothing
     */
    public ReadonlyJavaView(String source)
    {
        super(new BorderLayout());
        editor = JavaEditorFactory.createEditor(false);
        RTextScrollPane scrollPane = JavaEditorFactory.createScrollPane(editor);
        JavaEditorFactory.applyTheme(editor, scrollPane);
        add(scrollPane, BorderLayout.CENTER);
        setSource(source);
    }

    /**
     * Replaces the displayed source and scrolls back to the top.
     *
     * @param source the Java source to show; null shows nothing
     */
    public void setSource(String source)
    {
        editor.setText(source == null ? "" : source);
        editor.setCaretPosition(0);
    }
}
