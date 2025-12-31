package com.tonic.ui;

import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.StatusMessageEvent;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * Status bar component showing messages, progress, and memory usage.
 */
public class StatusBar extends ThemedJPanel {

    private final JLabel messageLabel;
    private final JLabel positionLabel;
    private final JLabel modeLabel;
    private final JLabel memoryLabel;
    private final JProgressBar progressBar;

    private final Timer memoryTimer;
    private Timer clearMessageTimer;

    public StatusBar() {
        super(BackgroundStyle.SECONDARY, new BorderLayout());
        setPreferredSize(new Dimension(0, UIConstants.STATUSBAR_HEIGHT));
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_TINY));
        leftPanel.setOpaque(false);

        messageLabel = createLabel("");
        positionLabel = createLabel("Ready");
        modeLabel = createLabel("");

        leftPanel.add(messageLabel);
        leftPanel.add(createSeparator());
        leftPanel.add(positionLabel);

        progressBar = new JProgressBar();
        progressBar.setPreferredSize(new Dimension(150, 12));
        progressBar.setVisible(false);
        progressBar.setStringPainted(true);
        progressBar.setForeground(JStudioTheme.getAccent());
        progressBar.setBackground(JStudioTheme.getBgTertiary());
        progressBar.setBorderPainted(false);

        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, UIConstants.SPACING_SMALL));
        centerPanel.setOpaque(false);
        centerPanel.add(progressBar);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_TINY));
        rightPanel.setOpaque(false);

        memoryLabel = createLabel("");
        rightPanel.add(modeLabel);
        rightPanel.add(createSeparator());
        rightPanel.add(memoryLabel);

        add(leftPanel, BorderLayout.WEST);
        add(centerPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);

        memoryTimer = new Timer(2000, e -> updateMemory());
        memoryTimer.start();
        updateMemory();

        EventBus.getInstance().register(StatusMessageEvent.class, this::handleStatusMessage);

        clearMessageTimer = new Timer(5000, e -> {
            messageLabel.setText("");
            clearMessageTimer.stop();
        });
        clearMessageTimer.setRepeats(false);
    }

    @Override
    protected void applyChildThemes() {
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JStudioTheme.getBorder()));

        messageLabel.setForeground(JStudioTheme.getTextSecondary());
        messageLabel.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));

        positionLabel.setForeground(JStudioTheme.getTextSecondary());
        positionLabel.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));

        modeLabel.setForeground(JStudioTheme.getTextSecondary());
        modeLabel.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));

        memoryLabel.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));

        progressBar.setForeground(JStudioTheme.getAccent());
        progressBar.setBackground(JStudioTheme.getBgTertiary());
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(JStudioTheme.getTextSecondary());
        label.setFont(JStudioTheme.getUIFont(UIConstants.FONT_SIZE_CODE));
        return label;
    }

    private JSeparator createSeparator() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, UIConstants.ICON_SIZE_SMALL));
        sep.setForeground(JStudioTheme.getBorder());
        return sep;
    }

    private String sanitize(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private void handleStatusMessage(StatusMessageEvent event) {
        setMessage(event.getMessage(), event.getType());
    }

    /**
     * Set the status message.
     */
    public void setMessage(String message) {
        setMessage(message, StatusMessageEvent.MessageType.INFO);
    }

    /**
     * Set the status message with type.
     */
    public void setMessage(String message, StatusMessageEvent.MessageType type) {
        messageLabel.setText(sanitize(message));

        switch (type) {
            case WARNING:
                messageLabel.setForeground(JStudioTheme.getWarning());
                break;
            case ERROR:
                messageLabel.setForeground(JStudioTheme.getError());
                break;
            default:
                messageLabel.setForeground(JStudioTheme.getTextSecondary());
                break;
        }

        // Auto-clear after 5 seconds
        clearMessageTimer.restart();
    }

    /**
     * Set the position label (e.g., "Line 42, Col 10").
     */
    public void setPosition(String position) {
        positionLabel.setText(position);
    }

    /**
     * Set the mode label (e.g., "Source", "Bytecode", "IR").
     */
    public void setMode(String mode) {
        modeLabel.setText(mode);
    }

    /**
     * Show the progress bar with indeterminate state.
     */
    public void showProgress(String message) {
        progressBar.setIndeterminate(true);
        progressBar.setString(sanitize(message));
        progressBar.setVisible(true);
    }

    /**
     * Show the progress bar with determinate state.
     */
    public void showProgress(int current, int total, String message) {
        progressBar.setIndeterminate(false);
        progressBar.setMaximum(total);
        progressBar.setValue(current);
        progressBar.setString(sanitize(message) + " (" + current + "/" + total + ")");
        progressBar.setVisible(true);
    }

    /**
     * Hide the progress bar.
     */
    public void hideProgress() {
        progressBar.setVisible(false);
    }

    private void updateMemory() {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long max = rt.maxMemory() / (1024 * 1024);
        memoryLabel.setText(used + " / " + max + " MB");

        // Change color based on usage
        double usage = (double) used / max;
        if (usage > 0.85) {
            memoryLabel.setForeground(JStudioTheme.getError());
        } else if (usage > 0.70) {
            memoryLabel.setForeground(JStudioTheme.getWarning());
        } else {
            memoryLabel.setForeground(JStudioTheme.getTextSecondary());
        }
    }

    /**
     * Clean up timers when the status bar is no longer needed.
     */
    public void dispose() {
        if (memoryTimer != null) {
            memoryTimer.stop();
        }
        if (clearMessageTimer != null) {
            clearMessageTimer.stop();
        }
    }
}
