package com.tonic.ui.vm.debugger;

import com.tonic.analysis.execution.core.BytecodeContext;
import com.tonic.analysis.execution.core.BytecodeResult;
import com.tonic.analysis.execution.debug.*;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.vm.VMExecutionService;

import javax.swing.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class VMDebugSession {

    public interface DebugListener {
        void onStateChanged(DebugStateModel state);
        void onSessionStarted();
        void onSessionStopped(String reason);
        void onBreakpointHit(String location);
        void onError(String message);
    }

    private int stepDelayMs = 300;

    private final List<DebugListener> listeners = new CopyOnWriteArrayList<>();
    private DebugSession yabrSession;
    private MethodEntry currentMethod;
    private DebugStateModel lastState;
    private boolean started;
    private Timer animationTimer;

    public VMDebugSession() {
        this.started = false;
    }

    public void start(MethodEntry method, boolean recursive, Object... args) {
        if (started) {
            throw new IllegalStateException("Session already started");
        }

        VMExecutionService vmService = VMExecutionService.getInstance();
        if (!vmService.isInitialized()) {
            vmService.initialize();
        }

        this.currentMethod = method;

        yabrSession = vmService.createDebugSession(
            method.getOwnerName(),
            method.getName(),
            method.getDesc(),
            recursive,
            args
        );

        yabrSession.addListener(new YabrDebugListener());
        started = true;

        notifySessionStarted();
        updateState();
    }

    public void stop() {
        stopAnimation();

        if (!started) {
            return;
        }

        if (yabrSession != null && !yabrSession.isStopped()) {
            yabrSession.stop();
        }

        started = false;
        notifySessionStopped("User stopped session");
    }

    public void stepInto() {
        if (!canStep()) {
            return;
        }

        try {
            yabrSession.stepInto();

            if (yabrSession.isStopped()) {
                started = false;
                String reason = "Execution completed";
                BytecodeResult result = yabrSession.getResult();
                if (result != null) {
                    if (result.hasException()) {
                        reason = "Exception: " + result.getException();
                    } else {
                        String returnVal = formatReturnValue(result);
                        if (returnVal != null) {
                            reason = "Execution completed - Return: " + returnVal;
                        }
                    }
                }
                notifySessionStopped(reason);
            } else {
                updateState();
            }
        } catch (StackOverflowError e) {
            notifyError("Stack overflow: " + e.getMessage());
            handleExecutionError(e);
        } catch (Exception e) {
            notifyError("Step Into failed: " + e.getMessage());
            handleExecutionError(e);
        }
    }

    public void stepOver() {
        if (!canStep()) return;

        try {
            yabrSession.stepOver();
            if (yabrSession.isStopped()) {
                started = false;
                String reason = "Execution completed";
                BytecodeResult result = yabrSession.getResult();
                if (result != null) {
                    if (result.hasException()) {
                        reason = "Exception: " + result.getException();
                    } else {
                        String returnVal = formatReturnValue(result);
                        if (returnVal != null) {
                            reason = "Execution completed - Return: " + returnVal;
                        }
                    }
                }
                notifySessionStopped(reason);
            } else {
                updateState();
            }
        } catch (StackOverflowError e) {
            notifyError("Stack overflow: " + e.getMessage());
            handleExecutionError(e);
        } catch (Exception e) {
            notifyError("Step Over failed: " + e.getMessage());
            handleExecutionError(e);
        }
    }

    public void stepOut() {
        if (!canStep()) return;

        try {
            yabrSession.stepOut();
            if (yabrSession.isStopped()) {
                started = false;
                String reason = "Execution completed";
                BytecodeResult result = yabrSession.getResult();
                if (result != null) {
                    if (result.hasException()) {
                        reason = "Exception: " + result.getException();
                    } else {
                        String returnVal = formatReturnValue(result);
                        if (returnVal != null) {
                            reason = "Execution completed - Return: " + returnVal;
                        }
                    }
                }
                notifySessionStopped(reason);
            } else {
                updateState();
            }
        } catch (StackOverflowError e) {
            notifyError("Stack overflow: " + e.getMessage());
            handleExecutionError(e);
        } catch (Exception e) {
            notifyError("Step Out failed: " + e.getMessage());
            handleExecutionError(e);
        }
    }

    public void resume() {
        if (!canStep()) return;

        try {
            yabrSession.resume();

            if (yabrSession.isStopped()) {
                started = false;
                String reason = "Execution completed";
                BytecodeResult result = yabrSession.getResult();
                if (result != null) {
                    if (result.hasException()) {
                        reason = "Exception: " + result.getException();
                    } else {
                        String returnVal = formatReturnValue(result);
                        if (returnVal != null) {
                            reason = "Execution completed - Return: " + returnVal;
                        }
                    }
                }
                notifySessionStopped(reason);
            } else {
                updateState();
            }
        } catch (StackOverflowError e) {
            notifyError("Stack overflow: " + e.getMessage());
            handleExecutionError(e);
        } catch (Exception e) {
            handleExecutionError(e);
        }
    }

    public void resumeAnimated() {
        if (!canStep()) return;

        stopAnimation();

        animationTimer = new Timer(stepDelayMs, e -> {
            if (yabrSession == null || yabrSession.isStopped()) {
                stopAnimation();
                started = false;
                String reason = "Execution completed";
                if (yabrSession != null) {
                    BytecodeResult result = yabrSession.getResult();
                    if (result != null) {
                        if (result.hasException()) {
                            reason = "Exception: " + result.getException();
                        } else {
                            String returnVal = formatReturnValue(result);
                            if (returnVal != null) {
                                reason = "Execution completed - Return: " + returnVal;
                            }
                        }
                    }
                }
                notifySessionStopped(reason);
                return;
            }

            if (!yabrSession.isPaused()) {
                return;
            }

            try {
                yabrSession.stepInto();
                updateState();

                if (yabrSession.isStopped()) {
                    stopAnimation();
                    started = false;
                    String reason = "Execution completed";
                    BytecodeResult result = yabrSession.getResult();
                    if (result != null) {
                        if (result.hasException()) {
                            reason = "Exception: " + result.getException();
                        } else {
                            String returnVal = formatReturnValue(result);
                            if (returnVal != null) {
                                reason = "Execution completed - Return: " + returnVal;
                            }
                        }
                    }
                    notifySessionStopped(reason);
                }
            } catch (StackOverflowError ex) {
                notifyError("Stack overflow: " + ex.getMessage());
                handleExecutionError(ex);
            } catch (Exception ex) {
                notifyError("Execution error: " + ex.getMessage());
                handleExecutionError(ex);
            }
        });

        animationTimer.start();
    }

    public void stopAnimation() {
        if (animationTimer != null) {
            animationTimer.stop();
            animationTimer = null;
        }
    }

    public boolean isAnimating() {
        return animationTimer != null && animationTimer.isRunning();
    }

    public void setAnimationDelay(int delayMs) {
        this.stepDelayMs = delayMs;
        if (animationTimer != null && animationTimer.isRunning()) {
            animationTimer.setDelay(delayMs);
        }
    }

    public int getAnimationDelay() {
        return stepDelayMs;
    }

    private void handleExecutionError() {
        handleExecutionError(null);
    }

    private void handleExecutionError(Throwable error) {
        stopAnimation();

        StringBuilder details = new StringBuilder("Execution error");

        if (yabrSession != null) {
            try {
                DebugState state = yabrSession.getCurrentState();
                if (state != null) {
                    details.append("\n  Method: ").append(state.getCurrentMethod());
                    details.append("\n  PC: ").append(state.getCurrentPC());
                    details.append("\n  Line: ").append(state.getCurrentLine());
                    details.append("\n  Call depth: ").append(state.getCallDepth());
                    details.append("\n  Instructions executed: ").append(state.getInstructionCount());

                    List<StackFrameInfo> callStack = state.getCallStack();
                    if (callStack != null && !callStack.isEmpty()) {
                        details.append("\n  Call stack:");
                        for (int i = 0; i < Math.min(callStack.size(), 10); i++) {
                            StackFrameInfo frame = callStack.get(i);
                            details.append("\n    ").append(i).append(": ")
                                   .append(frame.getMethodSignature())
                                   .append(" @ PC ").append(frame.getPC());
                        }
                        if (callStack.size() > 10) {
                            details.append("\n    ... and ").append(callStack.size() - 10).append(" more frames");
                        }
                    }
                }
            } catch (Exception e) {
                details.append("\n  (Could not get state: ").append(e.getMessage()).append(")");
            }

            if (!yabrSession.isStopped()) {
                try {
                    yabrSession.stop();
                } catch (Exception ignored) {
                }
            }
        }

        if (error != null) {
            details.append("\n  Error: ").append(error.getClass().getSimpleName())
                   .append(": ").append(error.getMessage());
            error.printStackTrace();
        }

        started = false;
        notifySessionStopped(details.toString());
    }

    public void runToCursor(int pc) {
        if (!canStep()) return;

        try {
            yabrSession.runToCursor(pc);
            updateState();
        } catch (Exception e) {
            notifyError("Run to cursor failed: " + e.getMessage());
        }
    }

    public void addBreakpoint(String className, String methodName, String descriptor, int pc) {
        if (yabrSession != null) {
            yabrSession.addBreakpoint(new Breakpoint(className, methodName, descriptor, pc));
        }
    }

    public void removeBreakpoint(String className, String methodName, String descriptor, int pc) {
        if (yabrSession != null) {
            Breakpoint bp = new Breakpoint(className, methodName, descriptor, pc);
            yabrSession.removeBreakpoint(bp);
        }
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isPaused() {
        return started && yabrSession != null && yabrSession.isPaused();
    }

    public boolean isStopped() {
        return yabrSession != null && yabrSession.isStopped();
    }

    public DebugStateModel getLastState() {
        return lastState;
    }

    public MethodEntry getCurrentMethod() {
        return currentMethod;
    }

    public void addListener(DebugListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(DebugListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private boolean canStep() {
        if (!started) {
            notifyError("Cannot step: Session not started");
            return false;
        }
        if (yabrSession == null) {
            notifyError("Cannot step: No debug session");
            return false;
        }
        if (yabrSession.isStopped()) {
            notifyError("Cannot step: Session already stopped (state=" + yabrSession.getState() + ")");
            return false;
        }
        if (!yabrSession.isPaused()) {
            notifyError("Cannot step: Session not paused (state=" + yabrSession.getState() + ")");
            return false;
        }
        return true;
    }

    private void updateState() {
        if (yabrSession == null) return;

        DebugState yabrState = yabrSession.getCurrentState();
        if (yabrState == null) return;

        lastState = convertToDebugStateModel(yabrState);
        notifyStateChanged(lastState);
    }

    private DebugStateModel convertToDebugStateModel(DebugState yabrState) {
        DebugStateModel.Builder builder = DebugStateModel.builder();

        String methodSig = yabrState.getCurrentMethod();
        if (methodSig != null) {
            String[] parts = parseMethodSignature(methodSig);
            builder.className(parts[0])
                   .methodName(parts[1])
                   .descriptor(parts[2]);
        }

        builder.instructionIndex(yabrState.getCurrentPC())
               .lineNumber(yabrState.getCurrentLine());

        List<StackEntry> stackEntries = new ArrayList<>();
        StackSnapshot stackSnapshot = yabrState.getOperandStack();
        if (stackSnapshot != null) {
            List<ValueInfo> values = stackSnapshot.getValues();
            for (int i = 0; i < values.size(); i++) {
                ValueInfo info = values.get(i);
                stackEntries.add(new StackEntry(
                    i,
                    info.getValueString(),
                    info.getType(),
                    "",
                    "LONG".equals(info.getType()) || "DOUBLE".equals(info.getType())
                ));
            }
        }
        builder.operandStack(stackEntries);

        List<LocalEntry> localEntries = new ArrayList<>();
        LocalsSnapshot localsSnapshot = yabrState.getLocals();
        if (localsSnapshot != null) {
            Map<Integer, ValueInfo> values = localsSnapshot.getValues();
            for (Map.Entry<Integer, ValueInfo> entry : values.entrySet()) {
                ValueInfo info = entry.getValue();
                localEntries.add(new LocalEntry(
                    entry.getKey(),
                    "local" + entry.getKey(),
                    info.getType(),
                    info.getValueString(),
                    false
                ));
            }
        }
        builder.localVariables(localEntries);

        List<FrameEntry> frameEntries = new ArrayList<>();
        List<StackFrameInfo> callStack = yabrState.getCallStack();
        if (callStack != null) {
            for (int i = 0; i < callStack.size(); i++) {
                StackFrameInfo frameInfo = callStack.get(i);
                String[] frameParts = parseMethodSignature(frameInfo.getMethodSignature());
                frameEntries.add(new FrameEntry(
                    frameParts[0],
                    frameParts[1],
                    frameParts[2],
                    frameInfo.getPC(),
                    frameInfo.getLineNumber(),
                    i == 0
                ));
            }
        }
        builder.callStack(frameEntries);

        return builder.build();
    }

    private String[] parseMethodSignature(String signature) {
        if (signature == null) {
            return new String[]{"", "", ""};
        }

        int dotIndex = signature.lastIndexOf('.');
        int parenIndex = signature.indexOf('(');

        if (dotIndex < 0 || parenIndex < 0) {
            return new String[]{signature, "", ""};
        }

        String className = signature.substring(0, dotIndex);
        String methodName = signature.substring(dotIndex + 1, parenIndex);
        String descriptor = signature.substring(parenIndex);

        return new String[]{className, methodName, descriptor};
    }

    private String formatReturnValue(BytecodeResult result) {
        if (result == null || !result.isSuccess()) return null;

        ConcreteValue returnValue = result.getReturnValue();
        if (returnValue == null) return null;

        String formatted;
        String typeLabel;

        switch (returnValue.getTag()) {
            case INT:
                formatted = String.valueOf(returnValue.asInt());
                typeLabel = "int";
                break;
            case LONG:
                formatted = returnValue.asLong() + "L";
                typeLabel = "long";
                break;
            case FLOAT:
                formatted = returnValue.asFloat() + "f";
                typeLabel = "float";
                break;
            case DOUBLE:
                formatted = String.valueOf(returnValue.asDouble());
                typeLabel = "double";
                break;
            case NULL:
                formatted = "null";
                typeLabel = "reference";
                break;
            case REFERENCE:
                var ref = returnValue.asReference();
                if (ref != null) {
                    String className = ref.getClassName();
                    formatted = ref.toString();
                    typeLabel = className.replace('/', '.');
                } else {
                    formatted = "null";
                    typeLabel = "reference";
                }
                break;
            default:
                return null;
        }

        return formatted + " (" + typeLabel + ")";
    }

    private void notifyStateChanged(DebugStateModel state) {
        for (DebugListener listener : listeners) {
            try {
                listener.onStateChanged(state);
            } catch (Exception e) {
                System.err.println("[DEBUG] Listener exception in onStateChanged: " + e.getMessage());
            }
        }
    }

    private void notifySessionStarted() {
        for (DebugListener listener : listeners) {
            try {
                listener.onSessionStarted();
            } catch (Exception e) {
                System.err.println("[DEBUG] Listener exception in onSessionStarted: " + e.getMessage());
            }
        }
    }

    private void notifySessionStopped(String reason) {
        for (DebugListener listener : listeners) {
            try {
                listener.onSessionStopped(reason);
            } catch (Exception e) {
                System.err.println("[DEBUG] Listener exception in onSessionStopped: " + e.getMessage());
            }
        }
    }

    private void notifyBreakpointHit(String location) {
        for (DebugListener listener : listeners) {
            try {
                listener.onBreakpointHit(location);
            } catch (Exception e) {
                System.err.println("[DEBUG] Listener exception in onBreakpointHit: " + e.getMessage());
            }
        }
    }

    private void notifyError(String message) {
        for (DebugListener listener : listeners) {
            try {
                listener.onError(message);
            } catch (Exception e) {
                System.err.println("[DEBUG] Listener exception in onError: " + e.getMessage());
            }
        }
    }

    private class YabrDebugListener implements DebugEventListener {
        @Override
        public void onSessionStart(DebugSession session) {
        }

        @Override
        public void onSessionStop(DebugSession session, BytecodeResult result) {
            started = false;
            String reason;
            if (result.isSuccess()) {
                String returnVal = formatReturnValue(result);
                reason = returnVal != null
                    ? "Completed - Return: " + returnVal
                    : "Completed successfully";
            } else {
                reason = "Execution failed";
            }
            notifySessionStopped(reason);
        }

        @Override
        public void onBreakpointHit(DebugSession session, Breakpoint breakpoint) {
            updateState();
            notifyBreakpointHit(breakpoint.getClassName() + "." + breakpoint.getMethodName() + " @ " + breakpoint.getPC());
        }

        @Override
        public void onStepComplete(DebugSession session, DebugState state) {
            updateState();
        }

        @Override
        public void onException(DebugSession session, com.tonic.analysis.execution.heap.ObjectInstance exception) {
            notifyError("Exception: " + exception.toString());
        }

        @Override
        public void onStateChange(DebugSession session, DebugSessionState oldState, DebugSessionState newState) {
        }
    }
}
