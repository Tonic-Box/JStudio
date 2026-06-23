package com.tonic.ui.vm.debugger;

import com.tonic.parser.MethodEntry;

import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.JFileChooser;
import java.awt.Component;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Owns the debugger's execution-trace recording: the on/off toggle, per-step capture, and export/clear of the
 * recorded {@link ExecutionTrace}. UI side effects (status messages, enabling the export/clear actions, the record
 * button's appearance) are delegated through injected callbacks so this stays free of toolbar wiring.
 */
final class TraceRecorder {

    private final Component dialogParent;
    private final IntFunction<String> instructionAtPc;
    private final Consumer<String> output;
    private final Runnable onTraceAvailable;
    private final Runnable onTraceUnavailable;
    private final Runnable onRecordingStarted;
    private final Runnable onRecordingStopped;

    private boolean recording = false;
    private ExecutionTrace currentTrace;
    private List<String> lastStackState = new ArrayList<>();

    TraceRecorder(Component dialogParent,
                  IntFunction<String> instructionAtPc,
                  Consumer<String> output,
                  Runnable onTraceAvailable,
                  Runnable onTraceUnavailable,
                  Runnable onRecordingStarted,
                  Runnable onRecordingStopped) {
        this.dialogParent = dialogParent;
        this.instructionAtPc = instructionAtPc;
        this.output = output;
        this.onTraceAvailable = onTraceAvailable;
        this.onTraceUnavailable = onTraceUnavailable;
        this.onRecordingStarted = onRecordingStarted;
        this.onRecordingStopped = onRecordingStopped;
    }

    boolean isRecording() {
        return recording;
    }

    /** Toggles recording on/off for the supplied method (may be null when no method is loaded yet). */
    void toggleRecording(boolean selected, MethodEntry currentMethod) {
        recording = selected;
        if (recording) {
            if (currentMethod != null) {
                currentTrace = new ExecutionTrace(
                    currentMethod.getOwnerName(),
                    currentMethod.getName(),
                    currentMethod.getDesc()
                );
                lastStackState.clear();
                output.accept("Recording started - execution trace will be captured");
            } else {
                output.accept("Recording enabled - will start capturing when debugging begins");
                currentTrace = null;
            }
            onRecordingStarted.run();
            onTraceUnavailable.run();
        } else {
            if (currentTrace != null && !currentTrace.getSteps().isEmpty()) {
                output.accept("Recording stopped - " + currentTrace.getSteps().size() + " steps captured");
                onTraceAvailable.run();
            } else {
                output.accept("Recording stopped - no steps captured");
            }
            onRecordingStopped.run();
        }
    }

    /** Starts a trace at session start when recording was enabled before a method was available. */
    void onSessionStarted(MethodEntry currentMethod) {
        if (recording && currentTrace == null && currentMethod != null) {
            currentTrace = new ExecutionTrace(
                currentMethod.getOwnerName(),
                currentMethod.getName(),
                currentMethod.getDesc()
            );
            lastStackState.clear();
            output.accept("Recording execution trace...");
        }
    }

    /** Finalizes the trace on a session stop, marking it complete and enabling export. */
    void onSessionStopped(String reason) {
        if (recording && currentTrace != null) {
            boolean normal = reason.toLowerCase().contains("complete") ||
                             reason.toLowerCase().contains("return");
            currentTrace.complete(reason, normal);
            output.accept("Trace recording complete - " + currentTrace.getSteps().size() + " steps captured");
            onTraceAvailable.run();
        }
    }

    /** Finalizes the trace when the user manually stops debugging. */
    void onManualStop() {
        if (recording && currentTrace != null) {
            currentTrace.complete("Session stopped by user", false);
            onTraceAvailable.run();
        }
    }

    void captureStep(DebugStateModel state) {
        if (!recording || currentTrace == null) return;

        ExecutionStep step = new ExecutionStep(
            state.getClassName(),
            state.getMethodName(),
            state.getDescriptor(),
            state.getInstructionIndex(),
            state.getLineNumber(),
            instructionAtPc.apply(state.getInstructionIndex()),
            state.getCallStack().size()
        );

        step.setStackBefore(new ArrayList<>(lastStackState));

        List<String> currentStack = new ArrayList<>();
        for (StackEntry entry : state.getOperandStack()) {
            currentStack.add(entry.toString());
        }
        step.setStackAfter(currentStack);
        lastStackState = new ArrayList<>(currentStack);

        List<String> locals = new ArrayList<>();
        for (LocalEntry entry : state.getLocalVariables()) {
            locals.add("local" + entry.getSlot() + ": " + entry);
        }
        step.setLocals(locals);

        currentTrace.addStep(step);
    }

    void exportTrace() {
        if (currentTrace == null || currentTrace.getSteps().isEmpty()) {
            JOptionPane.showMessageDialog(dialogParent,
                "No execution trace to export",
                "Export Trace",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Execution Trace");
        chooser.setFileFilter(new FileNameExtensionFilter("Markdown files (*.md)", "md"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("Text files (*.txt)", "txt"));

        String defaultName = currentTrace.getMethodName() + "_trace";
        chooser.setSelectedFile(new File(defaultName + ".md"));

        if (chooser.showSaveDialog(dialogParent) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            String path = file.getAbsolutePath();

            boolean isMarkdown = path.endsWith(".md") ||
                (chooser.getFileFilter() instanceof FileNameExtensionFilter &&
                 ((FileNameExtensionFilter)chooser.getFileFilter()).getExtensions()[0].equals("md"));

            if (!path.endsWith(".md") && !path.endsWith(".txt")) {
                path += isMarkdown ? ".md" : ".txt";
                file = new File(path);
            }

            try (FileWriter writer = new FileWriter(file)) {
                if (path.endsWith(".md")) {
                    writer.write(currentTrace.toMarkdown());
                } else {
                    writer.write(currentTrace.toCompactText());
                }
                output.accept("Trace exported to: " + file.getName());
                JOptionPane.showMessageDialog(dialogParent,
                    "Trace exported successfully!\n" + currentTrace.getSteps().size() + " steps saved.",
                    "Export Complete",
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                output.accept("Failed to export trace: " + e.getMessage());
                JOptionPane.showMessageDialog(dialogParent,
                    "Failed to export trace: " + e.getMessage(),
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    void clearTrace() {
        if (currentTrace == null || currentTrace.getSteps().isEmpty()) {
            output.accept("No trace to clear");
            return;
        }

        int stepCount = currentTrace.getSteps().size();
        currentTrace = null;
        lastStackState.clear();
        onTraceUnavailable.run();
        output.accept("Trace cleared (" + stepCount + " steps removed)");
    }
}
