package com.tonic.ui.editor.source;

import com.tonic.ui.theme.JStudioTheme;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.text.BadLocationException;
import java.awt.Color;
import java.awt.Rectangle;

final class SourceLineHighlighter
{

    private final RSyntaxTextArea textArea;
    private Object currentLineHighlight;

    SourceLineHighlighter(RSyntaxTextArea textArea)
    {
        this.textArea = textArea;
    }

    void highlightLine(int lineNumber)
    {
        highlightAndScrollToLine(lineNumber);
    }

    void highlightLinkedLine(int lineNumber)
    {
        highlightAndScrollToLine(lineNumber, JStudioTheme.getLinkHighlight());
    }

    void highlightAndScrollToLine(int lineNumber)
    {
        highlightAndScrollToLine(lineNumber, JStudioTheme.getLineHighlight());
    }

    void highlightAndScrollToLine(int lineNumber, Color highlightColor)
    {
        clearHighlight();
        try
        {
            currentLineHighlight = textArea.addLineHighlight(lineNumber, highlightColor);

            int caretLine = Math.max(lineNumber - 1, 0);
            int caretOffset = textArea.getLineStartOffset(caretLine);
            textArea.setCaretPosition(caretOffset);
            textArea.getCaret().setVisible(true);

            int highlightOffset = textArea.getLineStartOffset(lineNumber);
            Rectangle rect = textArea.modelToView2D(highlightOffset).getBounds();
            if (rect != null)
            {
                rect.height = textArea.getHeight() / 3;
                textArea.scrollRectToVisible(rect);
            }
        }
        catch (BadLocationException e)
        {
        }
    }

    void clearHighlight()
    {
        if (currentLineHighlight != null)
        {
            textArea.removeLineHighlight(currentLineHighlight);
            currentLineHighlight = null;
        }
    }

    int pickLineContaining(String token, int primary, int secondary)
    {
        if (token == null || token.isEmpty())
        {
            return -1;
        }
        String callForm = token + "(";
        if (lineContains(primary, callForm)) return primary;
        if (lineContains(secondary, callForm)) return secondary;
        if (lineContains(primary, token)) return primary;
        if (lineContains(secondary, token)) return secondary;
        int from = Math.min(primary, secondary);
        int to = Math.max(primary, secondary);
        for (int l = from; l <= to; l++)
        {
            if (lineContains(l, callForm) || lineContains(l, token))
            {
                return l;
            }
        }
        return -1;
    }

    private boolean lineContains(int oneBasedLine, String token)
    {
        return lineText(oneBasedLine).contains(token);
    }

    private String lineText(int oneBasedLine)
    {
        try
        {
            int line = oneBasedLine - 1;
            if (line < 0 || line >= textArea.getLineCount())
            {
                return "";
            }
            int start = textArea.getLineStartOffset(line);
            int end = textArea.getLineEndOffset(line);
            return textArea.getText(start, end - start);
        }
        catch (BadLocationException e)
        {
            return "";
        }
    }

    void selectTokenOnLine(int zeroBasedLine, String token)
    {
        if (token == null || token.isEmpty())
        {
            return;
        }
        try
        {
            int start = textArea.getLineStartOffset(zeroBasedLine);
            int end = textArea.getLineEndOffset(zeroBasedLine);
            String text = textArea.getText(start, end - start);
            int idx = bestTokenIndex(text, token);
            if (idx >= 0)
            {
                textArea.select(start + idx, start + idx + token.length());
                textArea.getCaret().setSelectionVisible(true);
                textArea.requestFocusInWindow();
            }
        }
        catch (BadLocationException e)
        {
        }
    }

    private static int bestTokenIndex(String text, String token)
    {
        int firstWord = -1;
        for (int i = text.indexOf(token); i >= 0; i = text.indexOf(token, i + 1))
        {
            if (isInsideLiteral(text, i))
            {
                continue;
            }
            char before = i > 0 ? text.charAt(i - 1) : '\0';
            int afterPos = i + token.length();
            char after = afterPos < text.length() ? text.charAt(afterPos) : '\0';
            if (Character.isJavaIdentifierPart(before) || Character.isJavaIdentifierPart(after))
            {
                continue;
            }
            if (before == '.' || after == '(')
            {
                return i;
            }
            if (firstWord < 0)
            {
                firstWord = i;
            }
        }
        return firstWord >= 0 ? firstWord : text.indexOf(token);
    }

    private static boolean isInsideLiteral(String text, int i)
    {
        boolean inString = false;
        boolean inChar = false;
        for (int j = 0; j < i && j < text.length(); j++)
        {
            char c = text.charAt(j);
            if ((inString || inChar) && c == '\\')
            {
                j++;
                continue;
            }
            if (c == '"' && !inChar)
            {
                inString = !inString;
            }
            else if (c == '\'' && !inString)
            {
                inChar = !inChar;
            }
        }
        return inString || inChar;
    }
}
