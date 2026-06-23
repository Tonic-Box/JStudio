package com.tonic.ui.run;

import com.tonic.service.run.RunService;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.component.ThemedJScrollPane;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;

/**
 * The dedicated "Run" output panel (a bottom-panel tab): streams a launched process's stdout (normal) and
 * stderr (red), with Terminate / Rerun / Clear controls and the final exit code. Implements
 * {@link RunService.RunOutput}; all callbacks marshal to the EDT.
 */
public final class RunConsolePanel extends ThemedJPanel implements RunService.RunOutput {

    private final JTextPane output = new JTextPane();
    private final JButton terminateButton = new JButton("Terminate", Icons.getIcon("close"));
    private final JButton rerunButton = new JButton("Rerun", Icons.getIcon("run"));
    private final JLabel status = new JLabel("Idle.");

    private Process process;
    private Runnable rerunAction;

    public RunConsolePanel() {
        super(BackgroundStyle.SECONDARY, new BorderLayout());

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setOpaque(false);
        JButton clearButton = new JButton("Clear");
        for (JButton button : new JButton[]{terminateButton, rerunButton, clearButton}) {
            button.setFocusable(false);
            toolbar.add(button);
        }
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

        terminateButton.addActionListener(e -> RunService.terminate(process));
        rerunButton.addActionListener(e -> {
            if (process == null && rerunAction != null) {
                rerunAction.run();
            }
        });
        clearButton.addActionListener(e -> output.setText(""));
        updateButtons();
    }

    /** Sets the action that relaunches with the same configuration (used by the Rerun button). */
    public void setRerunAction(Runnable action) {
        this.rerunAction = action;
    }

    /** Binds the live process to this panel (enables Terminate while it runs). */
    public void setProcess(Process process) {
        this.process = process;
        updateButtons();
    }

    @Override
    public void onStarted(String commandLine) {
        SwingUtilities.invokeLater(() -> {
            append("> " + commandLine + "\n", JStudioTheme.getTextSecondary());
            status.setText("Running...");
            updateButtons();
        });
    }

    @Override
    public void onStdout(String line) {
        SwingUtilities.invokeLater(() -> append(line + "\n", JStudioTheme.getTextPrimary()));
    }

    @Override
    public void onStderr(String line) {
        SwingUtilities.invokeLater(() -> append(line + "\n", JStudioTheme.getError()));
    }

    @Override
    public void onFinished(int exitCode) {
        SwingUtilities.invokeLater(() -> {
            process = null;
            status.setForeground(exitCode == 0 ? JStudioTheme.getSuccess() : JStudioTheme.getError());
            status.setText("Exited with code " + exitCode);
            append("\nProcess finished with exit code " + exitCode + "\n",
                    exitCode == 0 ? JStudioTheme.getSuccess() : JStudioTheme.getError());
            updateButtons();
        });
    }

    @Override
    public void onError(String message) {
        SwingUtilities.invokeLater(() -> {
            process = null;
            append(message + "\n", JStudioTheme.getError());
            status.setForeground(JStudioTheme.getError());
            status.setText("Failed.");
            updateButtons();
        });
    }

    private void append(String text, Color color) {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setForeground(attrs, color);
        try {
            output.getStyledDocument().insertString(output.getStyledDocument().getLength(), text, attrs);
            output.setCaretPosition(output.getStyledDocument().getLength());
        } catch (BadLocationException ignored) {
        }
    }

    private void updateButtons() {
        boolean running = process != null;
        terminateButton.setEnabled(running);
        rerunButton.setEnabled(!running && rerunAction != null);
        status.setForeground(running ? JStudioTheme.getTextSecondary() : status.getForeground());
    }
}
