package com.tonic.ui.live.recorder;

import com.tonic.live.LiveSession;
import com.tonic.live.protocol.LiveProtocol;
import com.tonic.ui.MainFrame;
import com.tonic.ui.core.SwingWorkers;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.component.ThemedJScrollPane;
import com.tonic.ui.live.LiveAttachService;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Right-dock tool (shown only while attached to a JFR-capable JVM): start/stop a Java Flight Recorder recording
 * in the target, snapshot the in-progress buffer, and export the captured {@code .jfr} files. The recordings
 * open in JDK Mission Control today; in-app analysis is a later slice.
 *
 * <p>Event-driven, not polling: the only timer is a client-side clock for the "Recording mm:ss" label, so the
 * (serial) connection stays free. Network calls run off the EDT via {@link SwingWorkers}.
 */
public final class LiveRecorderPanel extends ThemedJPanel {

    /** A base JFR configuration: display label + the {@code jdk.jfr} configuration name. */
    private enum Profile {
        PROFILE("Profile (detailed)", "profile"),
        DEFAULT("Default (low overhead)", "default");

        private final String label;
        private final String config;

        Profile(String label, String config) {
            this.label = label;
            this.config = config;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JComboBox<Profile> profileCombo = new JComboBox<>(Profile.values());
    private final JCheckBox cpu = new JCheckBox("CPU", true);
    private final JCheckBox alloc = new JCheckBox("Allocations", true);
    private final JCheckBox locks = new JCheckBox("Locks");
    private final JCheckBox exceptions = new JCheckBox("Exceptions");
    private final JButton startButton = new JButton("Start", Icons.getIcon("run"));
    private final JButton stopButton = new JButton("Stop");
    private final JButton snapshotButton = new JButton("Snapshot");
    private final JLabel statusLabel = new JLabel("Idle.");

    private final DefaultListModel<CapturedRecording> captured = new DefaultListModel<>();
    private final JList<CapturedRecording> capturedList = new JList<>(captured);
    private final JButton saveAsButton = new JButton("Save As...");
    private final JButton clearButton = new JButton("Clear");

    private final JButton analyzeButton = new JButton("Analyze");

    private final Timer clock = new Timer(1000, e -> tickClock());
    private final MainFrame mainFrame;
    private long startNanos;
    private boolean recording;

    public LiveRecorderPanel(MainFrame mainFrame) {
        super(BackgroundStyle.SECONDARY, new BorderLayout());
        this.mainFrame = mainFrame;

        add(buildControls(), BorderLayout.NORTH);
        add(buildCapturedList(), BorderLayout.CENTER);
        add(buildActions(), BorderLayout.SOUTH);

        startButton.addActionListener(e -> start());
        stopButton.addActionListener(e -> stop());
        snapshotButton.addActionListener(e -> snapshot());
        analyzeButton.addActionListener(e -> analyzeSelected());
        saveAsButton.addActionListener(e -> saveSelectedAs());
        clearButton.addActionListener(e -> clearCaptured());
        capturedList.addListSelectionListener(e -> updateButtons());
        capturedList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    analyzeSelected();
                }
            }
        });

        updateButtons();
    }

    private ThemedJPanel buildControls() {
        ThemedJPanel panel = new ThemedJPanel(BackgroundStyle.PRIMARY, null);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        ThemedJPanel profileRow = new ThemedJPanel(BackgroundStyle.PRIMARY, new FlowLayout(FlowLayout.LEFT, 6, 2));
        JLabel profileLabel = new JLabel("Profile:");
        profileLabel.setForeground(JStudioTheme.getTextSecondary());
        profileRow.add(profileLabel);
        profileRow.add(profileCombo);

        ThemedJPanel eventRow = new ThemedJPanel(BackgroundStyle.PRIMARY, new FlowLayout(FlowLayout.LEFT, 6, 0));
        for (JCheckBox box : new JCheckBox[]{cpu, alloc, locks, exceptions}) {
            box.setOpaque(false);
            box.setForeground(JStudioTheme.getTextPrimary());
            box.setFocusable(false);
            eventRow.add(box);
        }

        ThemedJPanel buttonRow = new ThemedJPanel(BackgroundStyle.PRIMARY, new FlowLayout(FlowLayout.LEFT, 6, 2));
        for (JButton button : new JButton[]{startButton, stopButton, snapshotButton}) {
            button.setFocusable(false);
            buttonRow.add(button);
        }

        ThemedJPanel statusRow = new ThemedJPanel(BackgroundStyle.PRIMARY, new FlowLayout(FlowLayout.LEFT, 6, 2));
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        statusLabel.setFont(JStudioTheme.getUIFont(11));
        statusRow.add(statusLabel);

        panel.add(profileRow);
        panel.add(eventRow);
        panel.add(buttonRow);
        panel.add(statusRow);
        return panel;
    }

    private ThemedJScrollPane buildCapturedList() {
        capturedList.setBackground(JStudioTheme.getBgSecondary());
        capturedList.setForeground(JStudioTheme.getTextPrimary());
        capturedList.setSelectionBackground(JStudioTheme.getSelection());
        capturedList.setSelectionForeground(JStudioTheme.getTextPrimary());
        capturedList.setFont(JStudioTheme.getUIFont(12));
        return new ThemedJScrollPane(capturedList);
    }

    private ThemedJPanel buildActions() {
        ThemedJPanel panel = new ThemedJPanel(BackgroundStyle.PRIMARY, new FlowLayout(FlowLayout.LEFT, 6, 2));
        for (JButton button : new JButton[]{analyzeButton, saveAsButton, clearButton}) {
            button.setFocusable(false);
            panel.add(button);
        }
        analyzeButton.setToolTipText("Open the selected recording in the analysis window (or double-click it)");
        return panel;
    }

    private void analyzeSelected() {
        CapturedRecording rec = capturedList.getSelectedValue();
        if (rec != null) {
            mainFrame.showJfrAnalysis(rec.file);
        }
    }

    private void start() {
        LiveSession session = LiveAttachService.getInstance().getSession();
        if (session == null) {
            statusLabel.setText("Not attached.");
            return;
        }
        String config = ((Profile) Objects.requireNonNull(profileCombo.getSelectedItem())).config;
        int mask = categoryMask();
        setBusy("Starting recording...");
        SwingWorkers.run(
                () -> {
                    session.startRecording(config, mask, 0);
                    return null;
                },
                ignored -> {
                    recording = true;
                    startNanos = System.nanoTime();
                    clock.start();
                    tickClock();
                    updateButtons();
                },
                err -> {
                    statusLabel.setText("Start failed: " + err.getMessage());
                    updateButtons();
                });
    }

    private void stop() {
        LiveSession session = LiveAttachService.getInstance().getSession();
        runCapture(session == null ? null : session::stopRecording, true, "Stopping...");
    }

    private void snapshot() {
        LiveSession session = LiveAttachService.getInstance().getSession();
        runCapture(session == null ? null : session::snapshotRecording, false, "Snapshotting...");
    }

    /** Shared stop/snapshot flow: call the agent, add the resulting file to the list, and (for stop) go idle. */
    private void runCapture(CaptureCall call, boolean stops, String busyText) {
        if (call == null) {
            statusLabel.setText("Not attached.");
            return;
        }
        setBusy(busyText);
        SwingWorkers.run(
                call::invoke,
                path -> {
                    if (stops) {
                        recording = false;
                        clock.stop();
                    }
                    addCaptured(path);
                    updateButtons();
                },
                err -> {
                    statusLabel.setText((stops ? "Stop" : "Snapshot") + " failed: " + err.getMessage());
                    updateButtons();
                });
    }

    private void addCaptured(String path) {
        File file = new File(path);
        CapturedRecording rec = new CapturedRecording(file, LocalTime.now().format(TIME), file.length());
        captured.addElement(rec);
        capturedList.setSelectedValue(rec, true);
        statusLabel.setText("Captured " + rec.file.getName() + " (" + humanBytes(rec.size) + ").");
    }

    private void saveSelectedAs() {
        CapturedRecording rec = capturedList.getSelectedValue();
        if (rec == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(rec.file.getName()));
        chooser.setFileFilter(new FileNameExtensionFilter("Flight Recorder file (*.jfr)", "jfr"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            Files.copy(rec.file.toPath(), chooser.getSelectedFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
            statusLabel.setText("Saved to " + chooser.getSelectedFile().getName() + ".");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save: " + e.getMessage(),
                    "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearCaptured() {
        for (int i = 0; i < captured.size(); i++) {
            try {
                Files.deleteIfExists(captured.get(i).file.toPath());
            } catch (IOException ignored) {
            }
        }
        captured.clear();
        updateButtons();
    }

    private int categoryMask() {
        int mask = 0;
        if (cpu.isSelected()) {
            mask |= LiveProtocol.JFR_CAT_CPU;
        }
        if (alloc.isSelected()) {
            mask |= LiveProtocol.JFR_CAT_ALLOC;
        }
        if (locks.isSelected()) {
            mask |= LiveProtocol.JFR_CAT_LOCKS;
        }
        if (exceptions.isSelected()) {
            mask |= LiveProtocol.JFR_CAT_EXCEPTIONS;
        }
        return mask;
    }

    /** Disables every action button while a request is in flight (the connection is serial). */
    private void setBusy(String text) {
        statusLabel.setText(text);
        startButton.setEnabled(false);
        stopButton.setEnabled(false);
        snapshotButton.setEnabled(false);
    }

    private void updateButtons() {
        startButton.setEnabled(!recording);
        stopButton.setEnabled(recording);
        snapshotButton.setEnabled(recording);
        profileCombo.setEnabled(!recording);
        cpu.setEnabled(!recording);
        alloc.setEnabled(!recording);
        locks.setEnabled(!recording);
        exceptions.setEnabled(!recording);
        boolean hasSelection = capturedList.getSelectedValue() != null;
        analyzeButton.setEnabled(hasSelection);
        saveAsButton.setEnabled(hasSelection);
        clearButton.setEnabled(!captured.isEmpty());
    }

    private void tickClock() {
        long seconds = (System.nanoTime() - startNanos) / 1_000_000_000L;
        statusLabel.setText(String.format("Recording  %d:%02d", seconds / 60, seconds % 60));
    }

    @Override
    public void removeNotify() {
        clock.stop();
        recording = false;
        statusLabel.setText("Idle.");
        updateButtons();
        super.removeNotify();
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return Math.round(kb) + " KB";
        }
        return String.format("%.1f MB", kb / 1024.0);
    }

    /** A captured {@code .jfr} file on the local (target == JStudio host) filesystem. */
    private static final class CapturedRecording {
        private final File file;
        private final String time;
        private final long size;

        CapturedRecording(File file, String time, long size) {
            this.file = file;
            this.time = time;
            this.size = size;
        }

        @Override
        public String toString() {
            return time + "   " + file.getName() + "   (" + humanBytes(size) + ")";
        }
    }

    /** A stop/snapshot agent call that returns the captured file path. */
    @FunctionalInterface
    private interface CaptureCall {
        String invoke() throws IOException;
    }
}
