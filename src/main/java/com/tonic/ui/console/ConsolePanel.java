package com.tonic.ui.console;

import com.tonic.service.LogLevel;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.text.SimpleDateFormat;
import java.util.Date;

/** The log console: timestamped, level-colored lines appended on the event thread, trimmed to the newest thousand by default. */
public class ConsolePanel extends ThemedJPanel
{

    private final JTextPane textPane;
    private final StyledDocument doc;
    private final JPanel toolbar;
    private final JButton clearButton;
    private final JScrollPane scrollPane;
    private SimpleAttributeSet infoStyle;
    private SimpleAttributeSet warnStyle;
    private SimpleAttributeSet errorStyle;
    private SimpleAttributeSet debugStyle;
    private SimpleAttributeSet timestampStyle;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
    private boolean showTimestamps = true;
    private int maxLines = 1000;

    /** Builds the console and logs a startup line. */
    public ConsolePanel()
    {
        super(BackgroundStyle.SECONDARY, new BorderLayout());

        toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_SMALL, UIConstants.SPACING_TINY));

        clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> clear());
        toolbar.add(clearButton);

        add(toolbar, BorderLayout.NORTH);

        textPane = new JTextPane();
        textPane.setEditable(false);

        doc = textPane.getStyledDocument();

        scrollPane = new JScrollPane(textPane);
        add(scrollPane, BorderLayout.CENTER);

        applyChildThemes();

        log(LogLevel.INFO, "JStudio initialized.");
    }

    @Override
    protected void applyChildThemes()
    {
        toolbar.setBackground(JStudioTheme.getBgSecondary());

        clearButton.setBackground(JStudioTheme.getBgTertiary());
        clearButton.setForeground(JStudioTheme.getTextPrimary());

        textPane.setBackground(JStudioTheme.getBgTertiary());
        textPane.setForeground(JStudioTheme.getTextPrimary());
        textPane.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE));

        scrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()));

        infoStyle = createStyle(JStudioTheme.getTextPrimary());
        warnStyle = createStyle(JStudioTheme.getWarning());
        errorStyle = createStyle(JStudioTheme.getError());
        debugStyle = createStyle(JStudioTheme.getTextSecondary());
        timestampStyle = createStyle(JStudioTheme.getTextSecondary());
    }

    private SimpleAttributeSet createStyle(Color color)
    {
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setForeground(style, color);
        StyleConstants.setFontFamily(style, JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE).getFamily());
        StyleConstants.setFontSize(style, UIConstants.FONT_SIZE_CODE);
        return style;
    }

    /**
     * Appends a line on the event thread, trimming the oldest lines past the limit and scrolling to the end; safe from any thread.
     *
     * @param level the severity, which picks the prefix and color
     * @param message the text
     */
    public void log(LogLevel level, String message)
    {
        SwingUtilities.invokeLater(() ->
        {
            try
            {
                if (showTimestamps)
                {
                    String timestamp = "[" + dateFormat.format(new Date()) + "] ";
                    doc.insertString(doc.getLength(), timestamp, timestampStyle);
                }

                String prefix;
                SimpleAttributeSet style;
                switch (level)
                {
                    case DEBUG:
                        prefix = "[DEBUG] ";
                        style = debugStyle;
                        break;
                    case WARN:
                        prefix = "[WARN]  ";
                        style = warnStyle;
                        break;
                    case ERROR:
                        prefix = "[ERROR] ";
                        style = errorStyle;
                        break;
                    case INFO:
                    default:
                        prefix = "[INFO]  ";
                        style = infoStyle;
                        break;
                }

                doc.insertString(doc.getLength(), prefix, style);
                doc.insertString(doc.getLength(), message + "\n", style);

                trimLines();

                textPane.setCaretPosition(doc.getLength());

            }
            catch (BadLocationException e)
            {
            }
        });
    }

    /**
     * Appends an info line.
     *
     * @param message the text
     */
    public void log(String message)
    {
        log(LogLevel.INFO, message);
    }

    /**
     * Appends an error line.
     *
     * @param message the text
     */
    public void logError(String message)
    {
        log(LogLevel.ERROR, message);
    }

    /**
     * Appends an info line.
     *
     * @param message the text
     */
    public void info(String message)
    {
        log(LogLevel.INFO, message);
    }

    /**
     * Appends a warning line.
     *
     * @param message the text
     */
    public void warn(String message)
    {
        log(LogLevel.WARN, message);
    }

    /**
     * Appends an error line.
     *
     * @param message the text
     */
    public void error(String message)
    {
        log(LogLevel.ERROR, message);
    }

    /**
     * Appends a debug line.
     *
     * @param message the text
     */
    public void debug(String message)
    {
        log(LogLevel.DEBUG, message);
    }

    /**
     * Appends an error line with the throwable's message, then its stack frames, abbreviated when there are more than five.
     *
     * @param message the text
     * @param t the throwable to report
     */
    public void error(String message, Throwable t)
    {
        log(LogLevel.ERROR, message + ": " + t.getMessage());
        for (StackTraceElement ste : t.getStackTrace())
        {
            log(LogLevel.ERROR, "  at " + ste.toString());
            if (t.getStackTrace().length > 5)
            {
                log(LogLevel.ERROR, "  ... " + (t.getStackTrace().length - 5) + " more");
                break;
            }
        }
    }

    /** Removes all text. */
    public void clear()
    {
        try
        {
            doc.remove(0, doc.getLength());
        }
        catch (BadLocationException e)
        {
        }
    }

    private void trimLines()
    {
        String text = textPane.getText();
        int lineCount = text.split("\n").length;
        if (lineCount > maxLines)
        {
            try
            {
                int linesToRemove = lineCount - maxLines;
                int removeEnd = 0;
                for (int i = 0; i < linesToRemove && removeEnd < text.length(); i++)
                {
                    int newline = text.indexOf('\n', removeEnd);
                    if (newline >= 0)
                    {
                        removeEnd = newline + 1;
                    }
                    else
                    {
                        break;
                    }
                }
                if (removeEnd > 0)
                {
                    doc.remove(0, removeEnd);
                }
            }
            catch (BadLocationException e)
            {
            }
        }
    }

    /**
     * Sets whether later lines start with a timestamp.
     *
     * @param show true to show timestamps
     */
    public void setShowTimestamps(boolean show)
    {
        this.showTimestamps = show;
    }

    /**
     * Sets how many lines are kept; takes effect on the next append.
     *
     * @param max the line limit
     */
    public void setMaxLines(int max)
    {
        this.maxLines = max;
    }

    /**
     * Gives the console contents.
     *
     * @return all text, as plain text
     */
    public String getText()
    {
        return textPane.getText();
    }
}
