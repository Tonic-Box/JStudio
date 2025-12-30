package com.tonic.ui.console;

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

public class ConsolePanel extends ThemedJPanel {

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

    public ConsolePanel() {
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
    protected void applyChildThemes() {
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

    private SimpleAttributeSet createStyle(Color color) {
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setForeground(style, color);
        StyleConstants.setFontFamily(style, JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE).getFamily());
        StyleConstants.setFontSize(style, UIConstants.FONT_SIZE_CODE);
        return style;
    }

    public void log(LogLevel level, String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                if (showTimestamps) {
                    String timestamp = "[" + dateFormat.format(new Date()) + "] ";
                    doc.insertString(doc.getLength(), timestamp, timestampStyle);
                }

                String prefix;
                SimpleAttributeSet style;
                switch (level) {
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

            } catch (BadLocationException e) {
                // Ignore
            }
        });
    }

    public void log(String message) {
        log(LogLevel.INFO, message);
    }

    public void logError(String message) {
        log(LogLevel.ERROR, message);
    }

    public void info(String message) {
        log(LogLevel.INFO, message);
    }

    public void warn(String message) {
        log(LogLevel.WARN, message);
    }

    public void error(String message) {
        log(LogLevel.ERROR, message);
    }

    public void debug(String message) {
        log(LogLevel.DEBUG, message);
    }

    public void error(String message, Throwable t) {
        log(LogLevel.ERROR, message + ": " + t.getMessage());
        for (StackTraceElement ste : t.getStackTrace()) {
            log(LogLevel.ERROR, "  at " + ste.toString());
            if (t.getStackTrace().length > 5) {
                log(LogLevel.ERROR, "  ... " + (t.getStackTrace().length - 5) + " more");
                break;
            }
        }
    }

    public void clear() {
        try {
            doc.remove(0, doc.getLength());
        } catch (BadLocationException e) {
            // Ignore
        }
    }

    private void trimLines() {
        String text = textPane.getText();
        int lineCount = text.split("\n").length;
        if (lineCount > maxLines) {
            try {
                int linesToRemove = lineCount - maxLines;
                int removeEnd = 0;
                for (int i = 0; i < linesToRemove && removeEnd < text.length(); i++) {
                    int newline = text.indexOf('\n', removeEnd);
                    if (newline >= 0) {
                        removeEnd = newline + 1;
                    } else {
                        break;
                    }
                }
                if (removeEnd > 0) {
                    doc.remove(0, removeEnd);
                }
            } catch (BadLocationException e) {
                // Ignore
            }
        }
    }

    public void setShowTimestamps(boolean show) {
        this.showTimestamps = show;
    }

    public void setMaxLines(int max) {
        this.maxLines = max;
    }

    public String getText() {
        return textPane.getText();
    }
}
