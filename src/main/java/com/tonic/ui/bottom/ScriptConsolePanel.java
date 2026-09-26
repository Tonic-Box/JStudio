package com.tonic.ui.bottom;

import com.tonic.event.events.ScriptConsoleEvent;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.component.ThemedJScrollPane;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;

/** The Script Console bottom tab: streams a headless script run's output, with a Clear button and a final modification count; updated on the EDT only. */
public final class ScriptConsolePanel extends ThemedJPanel
{

    private final JTextPane output = new JTextPane();
    private final JLabel status = new JLabel("Idle.");

    /** Creates an idle, empty console. */
    public ScriptConsolePanel()
    {
        super(BackgroundStyle.SECONDARY, new BorderLayout());

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setOpaque(false);
        JButton clearButton = new JButton("Clear");
        clearButton.setFocusable(false);
        toolbar.add(clearButton);
        ThemedJPanel topBar = new ThemedJPanel(BackgroundStyle.PRIMARY, new BorderLayout());
        topBar.add(toolbar, BorderLayout.WEST);
        ThemedJPanel statusWrap = new ThemedJPanel(BackgroundStyle.PRIMARY, new FlowLayout(FlowLayout.LEFT, 8, 4));
        status.setForeground(JStudioTheme.getTextSecondary());
        statusWrap.add(status);
        topBar.add(statusWrap, BorderLayout.CENTER);
        add(topBar, BorderLayout.NORTH);

        output.setEditable(false);
        output.setBackground(JStudioTheme.getBgTertiary());
        output.setFont(JStudioTheme.getCodeFont(12));
        add(new ThemedJScrollPane(output), BorderLayout.CENTER);

        clearButton.addActionListener(e -> output.setText(""));
    }

    /**
     * Applies one streamed console event: a start clears the output, a line is appended (in the error color if it looks like an error), and done shows the modification count.
     *
     * @param event the console event, received on the EDT
     */
    public void handle(ScriptConsoleEvent event)
    {
        switch (event.getKind())
        {
            case START:
                output.setText("");
                status.setForeground(JStudioTheme.getTextSecondary());
                status.setText("Running...");
                append(event.getText() + "\n", JStudioTheme.getTextSecondary());
                break;
            case LINE:
                String line = event.getText();
                append(line, line.startsWith("ERROR") || line.contains("error:") ? JStudioTheme.getError() : JStudioTheme.getTextPrimary());
                break;
            case DONE:
                int mods = event.getModifications();
                String plural = mods == 1 ? "" : "s";
                status.setForeground(JStudioTheme.getSuccess());
                status.setText("Completed: " + mods + " modification" + plural);
                append("\nCompleted with " + mods + " modification" + plural + ".\n", JStudioTheme.getSuccess());
                break;
        }
    }

    private void append(String text, Color color)
    {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setForeground(attrs, color);
        try
        {
            output.getStyledDocument().insertString(output.getStyledDocument().getLength(), text, attrs);
            output.setCaretPosition(output.getStyledDocument().getLength());
        }
        catch (BadLocationException ignored)
        {
        }
    }
}
