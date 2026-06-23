package com.tonic.ui.vm.debugger;

import com.tonic.ui.theme.JStudioTheme;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * The debugger's control toolbar: start/step/run/stop buttons (with keyboard shortcuts), the animation-speed
 * selector, the recursive-execution checkbox, the trace record/export/clear controls, and the VM reinit button.
 * All actions are delegated through injected callbacks; {@link #updateButtonStates()} derives enabled state from
 * the supplied {@link VMDebugSession} and method-loaded predicate.
 */
final class DebuggerToolbar extends JPanel {

    /** Animation delays (ms) corresponding to the speed selector entries. */
    private static final int[] DELAYS = {5, 10, 20, 50, 100, 300};

    private final VMDebugSession session;
    private final BooleanSupplier hasMethod;

    private final JButton startBtn;
    private final JButton stepIntoBtn;
    private final JButton stepOverBtn;
    private final JButton stepOutBtn;
    private final JButton resumeBtn;
    private final JButton stopBtn;
    private final JComboBox<String> speedSelector;
    private final JCheckBox recursiveCheckbox;
    private final JToggleButton recordBtn;
    private final JButton exportTraceBtn;
    private final JButton clearTraceBtn;

    DebuggerToolbar(VMDebugSession session,
                    BooleanSupplier hasMethod,
                    Runnable onStart,
                    Runnable onStepInto,
                    Runnable onStepOver,
                    Runnable onStepOut,
                    Runnable onResume,
                    Runnable onStop,
                    IntConsumer onSpeedChange,
                    Consumer<Boolean> onRecursiveChange,
                    Consumer<Boolean> onRecordToggle,
                    Runnable onExportTrace,
                    Runnable onClearTrace,
                    Runnable onReinit) {
        super(new FlowLayout(FlowLayout.LEFT, 5, 5));
        this.session = session;
        this.hasMethod = hasMethod;
        setBackground(JStudioTheme.getBgPrimary());

        startBtn = createToolButton("Start", null, e -> onStart.run());
        stepIntoBtn = createToolButton("Step Into (F7)", "F7", e -> onStepInto.run());
        stepOverBtn = createToolButton("Step Over (F8)", "F8", e -> onStepOver.run());
        stepOutBtn = createToolButton("Step Out (Shift+F8)", "shift F8", e -> onStepOut.run());
        resumeBtn = createToolButton("Run (F9)", "F9", e -> onResume.run());
        stopBtn = createToolButton("Stop", null, e -> onStop.run());

        speedSelector = new JComboBox<>(new String[]{"5ms", "10ms", "20ms", "50ms", "100ms", "300ms"});
        speedSelector.setSelectedIndex(4);
        speedSelector.setBackground(JStudioTheme.getBgTertiary());
        speedSelector.setForeground(JStudioTheme.getTextPrimary());
        speedSelector.setMaximumSize(new Dimension(80, 28));
        speedSelector.setToolTipText("Animation speed for Run mode");
        onSpeedChange.accept(DELAYS[speedSelector.getSelectedIndex()]);
        speedSelector.addActionListener(e -> onSpeedChange.accept(DELAYS[speedSelector.getSelectedIndex()]));

        recursiveCheckbox = new JCheckBox("Recursive Calls");
        recursiveCheckbox.setSelected(true);
        recursiveCheckbox.setBackground(JStudioTheme.getBgPrimary());
        recursiveCheckbox.setForeground(JStudioTheme.getTextPrimary());
        recursiveCheckbox.setToolTipText("Execute called methods recursively (vs stub with defaults)");
        recursiveCheckbox.addActionListener(e -> onRecursiveChange.accept(recursiveCheckbox.isSelected()));

        add(startBtn);
        add(Box.createHorizontalStrut(10));
        add(stepIntoBtn);
        add(stepOverBtn);
        add(stepOutBtn);
        add(Box.createHorizontalStrut(10));
        add(resumeBtn);
        add(stopBtn);
        add(Box.createHorizontalStrut(10));
        add(speedSelector);
        add(Box.createHorizontalStrut(10));
        add(recursiveCheckbox);
        add(Box.createHorizontalStrut(20));

        recordBtn = new JToggleButton("Record");
        recordBtn.setBackground(JStudioTheme.getBgSecondary());
        recordBtn.setForeground(JStudioTheme.getTextPrimary());
        recordBtn.setFocusPainted(false);
        recordBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        recordBtn.setToolTipText("Record execution trace");
        recordBtn.addActionListener(e -> onRecordToggle.accept(recordBtn.isSelected()));

        exportTraceBtn = createToolButton("Export Trace", null, e -> onExportTrace.run());
        exportTraceBtn.setEnabled(false);
        exportTraceBtn.setToolTipText("Export recorded execution trace");

        clearTraceBtn = createToolButton("Clear Trace", null, e -> onClearTrace.run());
        clearTraceBtn.setEnabled(false);
        clearTraceBtn.setToolTipText("Clear recorded execution trace");

        add(recordBtn);
        add(exportTraceBtn);
        add(clearTraceBtn);

        add(Box.createHorizontalStrut(20));

        JButton reinitBtn = createToolButton("Reinit VM", null, e -> onReinit.run());
        reinitBtn.setToolTipText("Reinitialize VM to pick up newly compiled methods");
        add(reinitBtn);
    }

    /** Whether the user has the record toggle pressed. */
    boolean isRecordSelected() {
        return recordBtn.isSelected();
    }

    /** Sets the record button's active (red) or idle appearance. */
    void setRecordingActive(boolean active) {
        if (active) {
            recordBtn.setBackground(JStudioTheme.getError().darker());
        } else {
            recordBtn.setBackground(JStudioTheme.getBgSecondary());
        }
        recordBtn.setForeground(JStudioTheme.getTextPrimary());
    }

    /** Enables/disables the export and clear trace buttons together. */
    void setTraceActionsEnabled(boolean enabled) {
        exportTraceBtn.setEnabled(enabled);
        clearTraceBtn.setEnabled(enabled);
    }

    void updateButtonStates() {
        boolean methodLoaded = hasMethod.getAsBoolean();
        boolean canStep = session.isPaused();
        boolean isRunning = session.isStarted() && !session.isStopped();
        boolean isAnimating = session.isAnimating();
        boolean canStartStepping = methodLoaded && (!session.isStarted() || canStep) && !isAnimating;

        startBtn.setEnabled(methodLoaded && !isRunning);
        stepIntoBtn.setEnabled(canStartStepping);
        stepOverBtn.setEnabled(canStartStepping);
        stepOutBtn.setEnabled(canStartStepping);
        resumeBtn.setEnabled(canStartStepping || isAnimating);
        resumeBtn.setText(isAnimating ? "Pause (F9)" : "Run (F9)");
        stopBtn.setEnabled(isRunning || isAnimating);
    }

    private JButton createToolButton(String text, String shortcut, ActionListener action) {
        JButton button = new JButton(text);
        button.setBackground(JStudioTheme.getBgSecondary());
        button.setForeground(JStudioTheme.getTextPrimary());
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        button.addActionListener(action);

        if (shortcut != null) {
            KeyStroke keyStroke = KeyStroke.getKeyStroke(shortcut);
            if (keyStroke != null) {
                button.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(keyStroke, shortcut);
                button.getActionMap().put(shortcut, new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (button.isEnabled()) {
                            action.actionPerformed(e);
                        }
                    }
                });
            }
        }

        return button;
    }
}
