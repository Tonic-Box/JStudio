package com.tonic.ui.editor.source;

import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.SyntaxColors;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rtextarea.RTextScrollPane;

import java.awt.Color;

/** Builds and themes Java syntax editors, so the source view and the live scratch pad share one definition of the editor's style, font and token colors. */
public final class JavaEditorFactory
{

    private JavaEditorFactory()
    {
    }

    /**
     * Creates a Java editor with JStudio's syntax style, font and editing options.
     *
     * @param editable whether the user can edit the text
     * @return the new editor, not yet themed
     */
    public static RSyntaxTextArea createEditor(boolean editable)
    {
        RSyntaxTextArea editor = new RSyntaxTextArea();
        editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        editor.setAntiAliasingEnabled(true);
        editor.setCodeFoldingEnabled(true);
        editor.setEditable(editable);
        editor.setFont(JStudioTheme.getCodeFont(13));
        return editor;
    }

    /**
     * Wraps an editor in a borderless, line-numbered scroll pane.
     *
     * @param editor the editor to wrap
     * @return the scroll pane
     */
    public static RTextScrollPane createScrollPane(RSyntaxTextArea editor)
    {
        RTextScrollPane scrollPane = new RTextScrollPane(editor);
        scrollPane.setLineNumbersEnabled(true);
        scrollPane.setBorder(null);
        return scrollPane;
    }

    /**
     * Applies the current theme's surface, gutter and token colors; safe to call again on each theme change.
     *
     * @param editor the editor to color
     * @param scrollPane the editor's scroll pane, whose gutter is colored; may be null
     */
    public static void applyTheme(RSyntaxTextArea editor, RTextScrollPane scrollPane)
    {
        editor.setBackground(JStudioTheme.getBgTertiary());
        editor.setForeground(JStudioTheme.getTextPrimary());
        editor.setCaretColor(JStudioTheme.getTextPrimary());
        editor.setSelectionColor(JStudioTheme.getSelection());
        editor.setCurrentLineHighlightColor(JStudioTheme.getLineHighlight());
        editor.setFadeCurrentLineHighlight(true);
        editor.setMatchedBracketBGColor(JStudioTheme.getSelection());
        editor.setMatchedBracketBorderColor(JStudioTheme.getAccent());

        if (scrollPane != null && scrollPane.getGutter() != null)
        {
            scrollPane.getGutter().setBackground(JStudioTheme.getBgSecondary());
            scrollPane.getGutter().setLineNumberColor(JStudioTheme.getTextSecondary());
            scrollPane.getGutter().setBorderColor(JStudioTheme.getBorder());
        }

        SyntaxScheme scheme = editor.getSyntaxScheme();
        setTokenStyle(scheme, Token.RESERVED_WORD, SyntaxColors.getJavaKeyword());
        setTokenStyle(scheme, Token.RESERVED_WORD_2, SyntaxColors.getJavaKeyword());
        setTokenStyle(scheme, Token.DATA_TYPE, SyntaxColors.getJavaType());
        setTokenStyle(scheme, Token.LITERAL_STRING_DOUBLE_QUOTE, SyntaxColors.getJavaString());
        setTokenStyle(scheme, Token.LITERAL_CHAR, SyntaxColors.getJavaString());
        setTokenStyle(scheme, Token.LITERAL_NUMBER_DECIMAL_INT, SyntaxColors.getJavaNumber());
        setTokenStyle(scheme, Token.LITERAL_NUMBER_FLOAT, SyntaxColors.getJavaNumber());
        setTokenStyle(scheme, Token.LITERAL_NUMBER_HEXADECIMAL, SyntaxColors.getJavaNumber());
        setTokenStyle(scheme, Token.COMMENT_EOL, SyntaxColors.getJavaComment());
        setTokenStyle(scheme, Token.COMMENT_MULTILINE, SyntaxColors.getJavaComment());
        setTokenStyle(scheme, Token.COMMENT_DOCUMENTATION, SyntaxColors.getJavaComment());
        setTokenStyle(scheme, Token.COMMENT_KEYWORD, SyntaxColors.getJavaAnnotation());
        setTokenStyle(scheme, Token.FUNCTION, SyntaxColors.getJavaMethod());
        setTokenStyle(scheme, Token.OPERATOR, SyntaxColors.getJavaOperator());
        setTokenStyle(scheme, Token.ANNOTATION, SyntaxColors.getJavaAnnotation());
        setTokenStyle(scheme, Token.IDENTIFIER, JStudioTheme.getTextPrimary());
        setTokenStyle(scheme, Token.LITERAL_BOOLEAN, SyntaxColors.getJavaConstant());
        setTokenStyle(scheme, Token.SEPARATOR, JStudioTheme.getTextPrimary());
    }

    private static void setTokenStyle(SyntaxScheme scheme, int tokenType, Color color)
    {
        if (scheme.getStyle(tokenType) != null)
        {
            scheme.getStyle(tokenType).foreground = color;
        }
    }
}
