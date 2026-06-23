package com.tonic.ui.vm.debugger;

import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.model.MethodEntryModel;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.vm.MethodSelectorPanel;
import com.tonic.ui.vm.VMExecutionService;
import com.tonic.ui.vm.heap.ArgumentConfigPanel;
import com.tonic.analysis.execution.state.ConcreteValue;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

public class DebuggerPanel extends ThemedJPanel implements VMDebugSession.DebugListener {

    private final VMDebugSession session;
    private final BytecodeTableView bytecodeTableView;
    private final StackPanel stackPanel;
    private final LocalsPanel localsPanel;
    private final CallStackPanel callStackPanel;
    private final JLabel statusLabel;
    private final JTextArea outputArea;

    private final DebuggerToolbar toolbar;

    private MethodEntry currentMethod;
    private MethodEntry displayedMethod;
    private final DebuggerSourceView sourceView;
    private final ArgumentConfigPanel argumentConfigPanel;
    private boolean recursiveExecution = true;
    private final BreakpointController breakpointController;
    private final FrameNavigator frameNavigator;
    private final BytecodeDisassembler disassembler = new BytecodeDisassembler();

    private final TraceRecorder traceRecorder;

    private final JTabbedPane bottomTabbedPane;
    private static final int TAB_ARGUMENTS = 0;
    private static final int TAB_OUTPUT = 1;

    public DebuggerPanel() {
        super(BackgroundStyle.PRIMARY, new BorderLayout(5, 5));
        this.session = new VMDebugSession();
        this.session.addListener(this);
        this.bytecodeTableView = new BytecodeTableView(
            this::breakpoints,
            this::toggleBreakpointAtPc,
            this::runToCursorAtPc);
        this.traceRecorder = new TraceRecorder(
            this,
            bytecodeTableView::getInstructionAtPC,
            this::appendOutput,
            this::enableTraceActions,
            this::disableTraceActions,
            this::activateRecording,
            this::deactivateRecording);

        setBorder(BorderFactory.createEmptyBorder(UIConstants.SPACING_LARGE, UIConstants.SPACING_LARGE, UIConstants.SPACING_LARGE, UIConstants.SPACING_LARGE));

        toolbar = new DebuggerToolbar(
            session,
            () -> currentMethod != null,
            this::startDebugging,
            () -> { ensureSessionStarted(); session.stepInto(); },
            () -> { ensureSessionStarted(); session.stepOver(); },
            () -> { ensureSessionStarted(); session.stepOut(); },
            this::onResume,
            this::stopDebugging,
            session::setAnimationDelay,
            selected -> recursiveExecution = selected,
            selected -> traceRecorder.toggleRecording(selected, currentMethod),
            traceRecorder::exportTrace,
            traceRecorder::clearTrace,
            this::reinitializeVM);
        add(toolbar, BorderLayout.NORTH);

        MethodSelectorPanel methodSelector = new MethodSelectorPanel("Method Browser");
        methodSelector.setPreferredSize(new Dimension(250, 0));
        methodSelector.setOnMethodSelected(this::onMethodSelected);

        sourceView = new DebuggerSourceView();
        breakpointController = new BreakpointController(
            session,
            () -> displayedMethod,
            this::appendOutput,
            () -> {
                bytecodeTableView.refresh();
                sourceView.refreshBreakpoints(breakpoints());
            });
        sourceView.setBreakpointToggler(breakpointController::toggleBreakpointAtPc);
        frameNavigator = new FrameNavigator(
            () -> currentMethod,
            m -> currentMethod = m,
            () -> displayedMethod,
            this::loadBytecode,
            bytecodeTableView::highlightInstruction);

        stackPanel = new StackPanel();
        localsPanel = new LocalsPanel();
        callStackPanel = new CallStackPanel();

        localsPanel.setOnValueEdit((slot, value) -> {
            if (session.setLocalValue(slot, value)) {
                appendOutput("Local at slot " + slot + " updated to: " + formatValue(value));
            }
        });

        stackPanel.setOnValueEdit((index, value) -> {
            if (session.setStackValue(index, value)) {
                appendOutput("Stack at index " + index + " updated to: " + formatValue(value));
            }
        });

        localsPanel.setOnObjectFieldEdit((obj, owner, name, desc, value) -> {
            if (session.setObjectFieldValue(obj, owner, name, desc, value)) {
                appendOutput("Field " + name + " updated on object @" + Integer.toHexString(obj.getId()));
            }
        });

        stackPanel.setOnObjectFieldEdit((obj, owner, name, desc, value) -> {
            if (session.setObjectFieldValue(obj, owner, name, desc, value)) {
                appendOutput("Field " + name + " updated on object @" + Integer.toHexString(obj.getId()));
            }
        });

        callStackPanel.setOnFrameSelected(frame -> {
            if (frame != null) {
                frameNavigator.navigateToFrame(frame);
            }
        });

        JSplitPane rightTopSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, stackPanel, localsPanel);
        rightTopSplit.setDividerLocation(180);
        rightTopSplit.setResizeWeight(0.5);
        rightTopSplit.setBackground(JStudioTheme.getBgPrimary());

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, rightTopSplit, callStackPanel);
        rightSplit.setDividerLocation(360);
        rightSplit.setResizeWeight(0.7);
        rightSplit.setBackground(JStudioTheme.getBgPrimary());
        rightSplit.setPreferredSize(new Dimension(280, 0));

        JTabbedPane viewTabs = new JTabbedPane();
        viewTabs.setBackground(JStudioTheme.getBgPrimary());
        viewTabs.addTab("Bytecode", bytecodeTableView.getScrollPane());
        viewTabs.addTab("Source", sourceView);

        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, viewTabs, rightSplit);
        centerSplit.setDividerLocation(450);
        centerSplit.setResizeWeight(0.6);
        centerSplit.setBackground(JStudioTheme.getBgPrimary());

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, methodSelector, centerSplit);
        mainSplit.setDividerLocation(250);
        mainSplit.setResizeWeight(0.0);
        mainSplit.setBackground(JStudioTheme.getBgPrimary());

        argumentConfigPanel = new ArgumentConfigPanel();
        argumentConfigPanel.setPreferredSize(new Dimension(0, 140));
        argumentConfigPanel.setMinimumSize(new Dimension(200, 100));

        outputArea = new JTextArea(4, 50);
        outputArea.setEditable(false);
        outputArea.setBackground(JStudioTheme.getBgSecondary());
        outputArea.setForeground(JStudioTheme.getTextPrimary());
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane outputScroll = new JScrollPane(outputArea);

        bottomTabbedPane = new JTabbedPane();
        bottomTabbedPane.setBackground(JStudioTheme.getBgPrimary());
        bottomTabbedPane.setForeground(JStudioTheme.getTextPrimary());
        bottomTabbedPane.addTab("Arguments", argumentConfigPanel);
        bottomTabbedPane.addTab("Output", outputScroll);
        bottomTabbedPane.setSelectedIndex(TAB_ARGUMENTS);
        bottomTabbedPane.setPreferredSize(new Dimension(0, 160));

        statusLabel = new JLabel("Select a method to debug");
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBackground(JStudioTheme.getBgPrimary());
        bottomPanel.add(statusLabel, BorderLayout.NORTH);
        bottomPanel.add(bottomTabbedPane, BorderLayout.CENTER);

        add(mainSplit, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        toolbar.updateButtonStates();
    }

    private void onMethodSelected(MethodEntryModel methodModel) {
        if (methodModel != null) {
            setMethod(methodModel.getMethodEntry());
        }
    }

    public void setMethod(MethodEntry method) {
        this.currentMethod = method;
        loadMethod(method);
        argumentConfigPanel.setMethod(method);
        statusLabel.setText("Loaded: " + method.getOwnerName() + "." + method.getName() + method.getDesc());
        toolbar.updateButtonStates();
    }

    private void loadMethod(MethodEntry method) {
        this.displayedMethod = method;
        bytecodeTableView.setTitle(method);

        CodeAttribute code = method.getCodeAttribute();
        if (code == null) {
            bytecodeTableView.clearInstructions();
            return;
        }

        BytecodeDisassembler.Result result = disassembler.disassemble(method);
        bytecodeTableView.setInstructions(result.instructions, result.pcToRow);
        sourceView.showMethod(method, breakpoints());
    }

    private void enableTraceActions() {
        toolbar.setTraceActionsEnabled(true);
    }

    private void disableTraceActions() {
        toolbar.setTraceActionsEnabled(false);
    }

    private void activateRecording() {
        toolbar.setRecordingActive(true);
    }

    private void deactivateRecording() {
        toolbar.setRecordingActive(false);
    }

    private void onResume() {
        if (session.isAnimating()) {
            session.stopAnimation();
            toolbar.updateButtonStates();
        } else {
            ensureSessionStarted();
            session.resumeAnimated();
        }
    }

    private void reinitializeVM() {
        try {
            VMExecutionService vmService = VMExecutionService.getInstance();

            if (session.isStarted()) {
                session.stop();
            }

            vmService.reset();
            vmService.initialize();

            appendOutput("VM reinitialized - new methods are now available");
            statusLabel.setText("VM reinitialized successfully");

            if (currentMethod != null) {
                MethodEntry m = frameNavigator.findMethod(
                    currentMethod.getOwnerName(), currentMethod.getName(), currentMethod.getDesc());
                if (m != null) {
                    setMethod(m);
                    appendOutput("Reloaded method: " + m.getName());
                }
            }

            toolbar.updateButtonStates();
        } catch (Exception e) {
            appendOutput("Failed to reinitialize VM: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                "Failed to reinitialize VM: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    public void loadMethod(MethodEntryModel methodModel) {
        if (methodModel == null) return;

        this.currentMethod = methodModel.getMethodEntry();
        loadBytecode(currentMethod);
        argumentConfigPanel.setMethod(currentMethod);

        statusLabel.setText("Method loaded: " + currentMethod.getOwnerName() + "." + currentMethod.getName());
        appendOutput("Loaded method: " + currentMethod.getName() + currentMethod.getDesc());
        toolbar.updateButtonStates();
    }

    private void loadBytecode(MethodEntry method) {
        this.displayedMethod = method;
        bytecodeTableView.setTitle(method);

        breakpointController.clear();

        CodeAttribute code = method.getCodeAttribute();
        if (code == null) {
            appendOutput("Method has no code (abstract or native)");
            bytecodeTableView.clearInstructions();
            return;
        }

        byte[] bytecode = code.getCode();
        if (bytecode == null || bytecode.length == 0) {
            appendOutput("Method has empty bytecode");
            bytecodeTableView.clearInstructions();
            return;
        }

        BytecodeDisassembler.Result result = disassembler.disassemble(method);
        bytecodeTableView.setInstructions(result.instructions, result.pcToRow);
        sourceView.showMethod(method, breakpoints());
    }

    public void startDebugging(Object... args) {
        if (currentMethod == null) {
            JOptionPane.showMessageDialog(this,
                "No method loaded. Select a method first.",
                "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            VMExecutionService vmService = VMExecutionService.getInstance();
            if (!vmService.isInitialized()) {
                vmService.initialize();
            }

            argumentConfigPanel.setHeapManager(vmService.getHeapManager());
            argumentConfigPanel.setClassResolver(vmService.getClassResolver());
            localsPanel.setClassResolver(vmService.getClassResolver());
            stackPanel.setClassResolver(vmService.getClassResolver());

            Object[] vmArgs = args.length > 0 ? args : argumentConfigPanel.getArguments();
            session.start(currentMethod, recursiveExecution, vmArgs);

            toolbar.updateButtonStates();
            String modeStr = recursiveExecution ? " (recursive mode)" : " (stub mode)";
            appendOutput("Started debugging: " + currentMethod.getName() + modeStr);
        } catch (Exception e) {
            appendOutput("Failed to start: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                "Failed to start debugging: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void ensureSessionStarted() {
        if (!session.isStarted() && currentMethod != null) {
            try {
                VMExecutionService vmService = VMExecutionService.getInstance();
                if (!vmService.isInitialized()) {
                    vmService.initialize();
                }

                argumentConfigPanel.setHeapManager(vmService.getHeapManager());
                argumentConfigPanel.setClassResolver(vmService.getClassResolver());
                localsPanel.setClassResolver(vmService.getClassResolver());
                stackPanel.setClassResolver(vmService.getClassResolver());

                ConcreteValue[] vmArgs = argumentConfigPanel.getArguments();
                session.start(currentMethod, recursiveExecution, (Object[]) vmArgs);

                toolbar.updateButtonStates();
                String modeStr = recursiveExecution ? " (recursive mode)" : " (stub mode)";
                appendOutput("Started debugging: " + currentMethod.getName() + modeStr);
            } catch (Exception e) {
                appendOutput("Failed to start: " + e.getMessage());
            }
        }
    }

    public void stopDebugging() {
        session.stop();
        toolbar.updateButtonStates();
        clearHighlight();
        appendOutput("Debugging stopped");

        traceRecorder.onManualStop();
    }

    public boolean isDebugging() {
        return session.isStarted();
    }

    private Set<Integer> breakpoints() {
        return breakpointController.getBreakpoints();
    }

    private void toggleBreakpointAtPc(int pc) {
        breakpointController.toggleBreakpointAtPc(pc);
    }

    private void runToCursorAtPc(int pc) {
        ensureSessionStarted();
        session.runToCursor(pc);
    }

    private void clearHighlight() {
        bytecodeTableView.clearHighlight();
        sourceView.clearExecutionHighlight();
    }

    private void appendOutput(String text) {
        SwingUtilities.invokeLater(() -> {
            outputArea.append(text + "\n");
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
        });
    }

    private String formatValue(ConcreteValue value) {
        if (value == null) {
            return "null";
        }
        switch (value.getTag()) {
            case INT:
                return String.valueOf(value.asInt());
            case LONG:
                return value.asLong() + "L";
            case FLOAT:
                return value.asFloat() + "f";
            case DOUBLE:
                return String.valueOf(value.asDouble());
            case NULL:
                return "null";
            case REFERENCE:
                var ref = value.asReference();
                return ref != null ? ref.toString() : "null";
            default:
                return value.toString();
        }
    }

    @Override
    public void onStateChanged(DebugStateModel state) {
        SwingUtilities.invokeLater(() -> {
            frameNavigator.onMethodMaybeChanged(
                state.getClassName(), state.getMethodName(), state.getDescriptor());

            if (traceRecorder.isRecording()) {
                traceRecorder.captureStep(state);
            }

            bytecodeTableView.highlightInstruction(state.getInstructionIndex());
            sourceView.showExecutionPoint(displayedMethod, state.getInstructionIndex(), breakpoints());
            stackPanel.updateStack(state.getOperandStack());
            localsPanel.updateLocals(state.getLocalVariables());
            callStackPanel.updateCallStack(state.getCallStack());

            statusLabel.setText(String.format("Paused at %s.%s @ PC=%d (Line %d)",
                state.getSimpleClassName(),
                state.getMethodName(),
                state.getInstructionIndex(),
                state.getLineNumber()));

            toolbar.updateButtonStates();
        });
    }

    @Override
    public void onSessionStarted() {
        SwingUtilities.invokeLater(() -> {
            appendOutput("Debug session started");
            toolbar.updateButtonStates();
            bottomTabbedPane.setSelectedIndex(TAB_OUTPUT);

            traceRecorder.onSessionStarted(currentMethod);
        });
    }

    @Override
    public void onSessionStopped(String reason) {
        SwingUtilities.invokeLater(() -> {
            appendOutput("Debug session stopped: " + reason);
            statusLabel.setText("Stopped: " + reason);
            clearHighlight();
            stackPanel.clear();
            localsPanel.clear();
            callStackPanel.clear();
            toolbar.updateButtonStates();
            bottomTabbedPane.setSelectedIndex(TAB_ARGUMENTS);

            traceRecorder.onSessionStopped(reason);
        });
    }

    @Override
    public void onBreakpointHit(String location) {
        SwingUtilities.invokeLater(() -> appendOutput("Breakpoint hit: " + location));
    }

    @Override
    public void onError(String message) {
        SwingUtilities.invokeLater(() -> {
            appendOutput("Error: " + message);
            statusLabel.setText("Error: " + message);
        });
    }
}
