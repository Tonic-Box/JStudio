package com.tonic.ui.vm.heap;

import com.tonic.analysis.execution.core.BytecodeContext;
import com.tonic.analysis.execution.core.BytecodeEngine;
import com.tonic.analysis.execution.core.BytecodeResult;
import com.tonic.analysis.execution.core.ExecutionMode;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.service.ProjectService;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.vm.MethodSelectorPanel;
import com.tonic.ui.vm.heap.model.*;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.List;

public class HeapForensicsPanel extends JPanel implements HeapForensicsTracker.ForensicsEventListener {

    private HeapForensicsTracker tracker;
    private HeapForensicsListener listener;

    private final MethodSelectorPanel methodSelector;
    private final ArgumentConfigPanel argumentConfigPanel;
    private final ClassSummaryPanel classSummaryPanel;
    private final ObjectListPanel objectListPanel;
    private final ObjectDetailPanel objectDetailPanel;

    private JLabel statusLabel;
    private JButton runBtn;
    private JButton snapshotBtn;
    private JButton refreshBtn;
    private JButton clearBtn;
    private JButton exportBtn;
    private JToggleButton trackingToggle;
    private JSpinner provenanceSpinner;
    private JCheckBox trackMutationsCheck;

    private HeapSnapshot lastSnapshot;
    private MethodEntryModel selectedMethod;
    private boolean isRunning = false;

    public HeapForensicsPanel() {
        SimpleHeapManager heapManager = new SimpleHeapManager();
        this.tracker = new HeapForensicsTracker(heapManager);
        this.listener = new HeapForensicsListener(tracker);
        this.tracker.addListener(this);

        setLayout(new BorderLayout(5, 5));
        setBackground(JStudioTheme.getBgPrimary());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel toolbarPanel = createToolbar();
        add(toolbarPanel, BorderLayout.NORTH);

        methodSelector = new MethodSelectorPanel("Select Method");
        methodSelector.setPreferredSize(new Dimension(250, 0));
        methodSelector.setOnMethodSelected(this::onMethodSelected);

        argumentConfigPanel = new ArgumentConfigPanel();
        argumentConfigPanel.setPreferredSize(new Dimension(0, 160));
        argumentConfigPanel.setMinimumSize(new Dimension(200, 120));

        classSummaryPanel = new ClassSummaryPanel();
        objectListPanel = new ObjectListPanel();
        objectListPanel.setOnObjectSelected(this::onObjectSelected);

        objectDetailPanel = new ObjectDetailPanel();
        objectDetailPanel.setTracker(tracker);

        JSplitPane detailSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            wrapWithTitle(objectListPanel, "Objects"),
            wrapWithTitle(objectDetailPanel, "Object Details"));
        detailSplit.setDividerLocation(200);
        detailSplit.setResizeWeight(0.4);
        detailSplit.setBackground(JStudioTheme.getBgPrimary());

        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            wrapWithTitle(classSummaryPanel, "Class Summary"),
            detailSplit);
        rightSplit.setDividerLocation(200);
        rightSplit.setResizeWeight(0.25);
        rightSplit.setBackground(JStudioTheme.getBgPrimary());

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            methodSelector,
            rightSplit);
        mainSplit.setDividerLocation(280);
        mainSplit.setResizeWeight(0.25);
        mainSplit.setBackground(JStudioTheme.getBgPrimary());

        JPanel centerPanel = new JPanel(new BorderLayout(0, 5));
        centerPanel.setBackground(JStudioTheme.getBgPrimary());
        centerPanel.add(mainSplit, BorderLayout.CENTER);
        centerPanel.add(argumentConfigPanel, BorderLayout.SOUTH);

        add(centerPanel, BorderLayout.CENTER);

        statusLabel = new JLabel("Select a method and click 'Run Analysis' to begin");
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(statusLabel, BorderLayout.SOUTH);

        updateButtonStates();
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        toolbar.setBackground(JStudioTheme.getBgSecondary());
        toolbar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        runBtn = new JButton("Run Analysis", Icons.getIcon("run"));
        runBtn.setEnabled(false);
        runBtn.addActionListener(e -> runAnalysis());
        toolbar.add(runBtn);

        toolbar.add(Box.createHorizontalStrut(10));

        trackingToggle = new JToggleButton("Tracking", true);
        trackingToggle.setSelected(true);
        trackingToggle.addActionListener(e -> {
            tracker.setTracking(trackingToggle.isSelected());
            updateStatus();
        });
        toolbar.add(trackingToggle);

        trackMutationsCheck = new JCheckBox("Track Mutations", true);
        trackMutationsCheck.setBackground(JStudioTheme.getBgSecondary());
        trackMutationsCheck.setForeground(JStudioTheme.getTextPrimary());
        trackMutationsCheck.addActionListener(e ->
            listener.setTrackMutations(trackMutationsCheck.isSelected()));
        toolbar.add(trackMutationsCheck);

        toolbar.add(new JLabel("Provenance:"));
        provenanceSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 10, 1));
        provenanceSpinner.setPreferredSize(new Dimension(50, 25));
        provenanceSpinner.addChangeListener(e ->
            listener.setProvenanceDepth((Integer) provenanceSpinner.getValue()));
        toolbar.add(provenanceSpinner);

        toolbar.add(Box.createHorizontalStrut(15));

        snapshotBtn = new JButton("Snapshot");
        snapshotBtn.addActionListener(e -> takeSnapshot());
        toolbar.add(snapshotBtn);

        refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refresh());
        toolbar.add(refreshBtn);

        clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> reset());
        toolbar.add(clearBtn);

        exportBtn = new JButton("Export...");
        exportBtn.addActionListener(e -> showExportDialog());
        toolbar.add(exportBtn);

        return toolbar;
    }

    private JScrollPane wrapWithTitle(JComponent component, String title) {
        JScrollPane scroll = new JScrollPane(component);
        scroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()),
            title,
            TitledBorder.LEFT,
            TitledBorder.TOP,
            null,
            JStudioTheme.getTextPrimary()
        ));
        scroll.getViewport().setBackground(JStudioTheme.getBgSecondary());
        return scroll;
    }

    private void onMethodSelected(MethodEntryModel method) {
        this.selectedMethod = method;
        updateButtonStates();
        if (method != null) {
            String ownerName = method.getOwner() != null ? method.getOwner().getClassName() : "?";
            statusLabel.setText("Selected: " + ownerName + "." + method.getName() + method.getDescriptor());
            argumentConfigPanel.setMethod(method.getMethodEntry());
        } else {
            argumentConfigPanel.setMethod(null);
        }
    }

    private void updateButtonStates() {
        runBtn.setEnabled(selectedMethod != null && !isRunning);
    }

    private void runAnalysis() {
        if (selectedMethod == null || isRunning) return;

        MethodEntry method = selectedMethod.getMethodEntry();
        if (method == null) {
            JOptionPane.showMessageDialog(this,
                "Cannot resolve method entry",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null || project.getClassPool() == null) {
            JOptionPane.showMessageDialog(this,
                "No project loaded. Load a JAR or class file first.",
                "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        reset();

        isRunning = true;
        updateButtonStates();

        ClassPool classPool = project.getClassPool();
        ClassResolver classResolver = new ClassResolver(classPool);
        SimpleHeapManager heapManager = (SimpleHeapManager) tracker.getHeapManager();
        heapManager.setClassResolver(classResolver);
        argumentConfigPanel.setHeapManager(heapManager);

        ConcreteValue[] args = argumentConfigPanel.getArguments();
        String argsDesc = argumentConfigPanel.getCurrentArgsDescription();
        statusLabel.setText("Running: " + selectedMethod.getName() + argsDesc + "...");

        SwingWorker<ExecutionResultWrapper, Void> worker = new SwingWorker<>() {
            @Override
            protected ExecutionResultWrapper doInBackground() {
                try {
                    BytecodeContext ctx = new BytecodeContext.Builder()
                        .heapManager(heapManager)
                        .classResolver(classResolver)
                        .mode(ExecutionMode.RECURSIVE)
                        .maxCallDepth(100)
                        .maxInstructions(1_000_000)
                        .build();

                    BytecodeEngine engine = new BytecodeEngine(ctx);
                    engine.addListener(listener);

                    BytecodeResult result = engine.execute(method, args);

                    return new ExecutionResultWrapper(result, null);
                } catch (Exception e) {
                    return new ExecutionResultWrapper(null, e);
                }
            }

            @Override
            protected void done() {
                isRunning = false;
                updateButtonStates();
                try {
                    ExecutionResultWrapper wrapper = get();
                    if (wrapper.result != null && wrapper.result.isSuccess()) {
                        String resultText = "Completed successfully";
                        ConcreteValue retVal = wrapper.result.getReturnValue();
                        if (retVal != null && !retVal.isNull()) {
                            resultText += " | Return: " + formatReturnValue(retVal);
                        }
                        resultText += " | Instructions: " + wrapper.result.getInstructionsExecuted();
                        statusLabel.setText(resultText + " | " + getStatsText());
                    } else {
                        String error = "Unknown error";
                        if (wrapper.exception != null) {
                            error = wrapper.exception.getMessage();
                        } else if (wrapper.result != null && wrapper.result.getException() != null) {
                            error = wrapper.result.getException().toString();
                        }
                        statusLabel.setText("Execution failed: " + error);
                    }
                } catch (Exception e) {
                    statusLabel.setText("Error: " + e.getMessage());
                }
                refresh();
            }
        };

        worker.execute();
    }

    private String formatReturnValue(ConcreteValue value) {
        if (value == null || value.isNull()) return "null";
        switch (value.getTag()) {
            case INT: return String.valueOf(value.asInt());
            case LONG: return String.valueOf(value.asLong());
            case FLOAT: return String.valueOf(value.asFloat());
            case DOUBLE: return String.valueOf(value.asDouble());
            case REFERENCE:
                ObjectInstance ref = value.asReference();
                if (ref != null) {
                    SimpleHeapManager hm = (SimpleHeapManager) tracker.getHeapManager();
                    String str = hm.extractString(ref);
                    if (str != null) return "\"" + str + "\"";
                    return ref.getClassName() + "@" + ref.getId();
                }
                return "ref";
            default: return value.toString();
        }
    }

    private static class ExecutionResultWrapper {
        final BytecodeResult result;
        final Exception exception;
        ExecutionResultWrapper(BytecodeResult result, Exception exception) {
            this.result = result;
            this.exception = exception;
        }
    }

    private String getStatsText() {
        return String.format("Objects: %d | Allocs: %d | Mutations: %d",
            tracker.getTotalObjectCount(),
            tracker.getTotalAllocationCount(),
            tracker.getTotalMutationCount());
    }

    private void onClassSelected(String className) {
        if (className == null) {
            objectListPanel.setObjects(List.of());
            return;
        }

        List<HeapObject> objects = tracker.getObjectsByClass(className);
        objectListPanel.setObjects(objects);
        updateStatus();
    }

    private void onObjectSelected(HeapObject object) {
        objectDetailPanel.setObject(object);
    }

    private void takeSnapshot() {
        String label = JOptionPane.showInputDialog(this,
            "Snapshot label:", "Snapshot " + (tracker.getSnapshots().size() + 1));
        if (label == null) return;

        lastSnapshot = tracker.takeSnapshot(label);
        updateStatus();
        JOptionPane.showMessageDialog(this,
            "Snapshot taken: " + lastSnapshot.getTotalObjects() + " objects",
            "Snapshot", JOptionPane.INFORMATION_MESSAGE);
    }

    public void refresh() {
        classSummaryPanel.update(tracker.getClassCounts(), lastSnapshot);
        classSummaryPanel.setOnClassSelected(this::onClassSelected);
        classSummaryPanel.selectFirstRow();
        updateStatus();
    }

    private void showExportDialog() {
        String[] options = {"JSON", "CSV", "HTML Report", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this,
            "Select export format:",
            "Export Heap Data",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null, options, options[0]);

        if (choice >= 0 && choice < 3) {
            JFileChooser chooser = new JFileChooser();
            String ext = choice == 0 ? ".json" : choice == 1 ? ".csv" : ".html";
            chooser.setSelectedFile(new java.io.File("heap_export" + ext));
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                exportData(chooser.getSelectedFile(), choice);
            }
        }
    }

    private void exportData(java.io.File file, int format) {
        statusLabel.setText("Export not yet implemented");
    }

    private void updateStatus() {
        if (!isRunning) {
            statusLabel.setText(getStatsText() +
                " | Snapshots: " + tracker.getSnapshots().size() +
                " | " + (tracker.isTracking() ? "Tracking" : "Paused"));
        }
    }

    public HeapForensicsTracker getTracker() {
        return tracker;
    }

    public HeapForensicsListener getListener() {
        return listener;
    }

    @Override
    public void onAllocationRecorded(AllocationEvent event) {
        SwingUtilities.invokeLater(() -> {
            classSummaryPanel.incrementClass(event.getClassName());
        });
    }

    @Override
    public void onMutationRecorded(MutationEvent event) {
    }

    @Override
    public void onSnapshotTaken(HeapSnapshot snapshot) {
        SwingUtilities.invokeLater(() -> {
            lastSnapshot = snapshot;
            refresh();
        });
    }

    @Override
    public void onExecutionEnded(long instructionCount) {
        SwingUtilities.invokeLater(this::refresh);
    }

    public void reset() {
        SimpleHeapManager newHeap = new SimpleHeapManager();
        tracker = new HeapForensicsTracker(newHeap);
        listener = new HeapForensicsListener(tracker);
        tracker.addListener(this);
        objectDetailPanel.setTracker(tracker);

        classSummaryPanel.clear();
        objectListPanel.setObjects(List.of());
        objectDetailPanel.setObject(null);
        lastSnapshot = null;
        updateStatus();
    }
}
