package com.tonic.plugin.context;

import com.tonic.analysis.execution.core.BytecodeResult;
import com.tonic.analysis.execution.debug.DebugSession;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.MethodEntry;
import com.tonic.plugin.api.VmDebugApi;
import com.tonic.ui.vm.VMExecutionService;
import com.tonic.ui.vm.VmInstance;
import com.tonic.ui.vm.debugger.DebugStateModel;
import com.tonic.ui.vm.debugger.FrameEntry;
import com.tonic.ui.vm.debugger.LocalEntry;
import com.tonic.ui.vm.debugger.StackEntry;
import com.tonic.ui.vm.debugger.VMDebugSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives JStudio's bytecode interpreter/debugger for plugins. Each {@link #start} mints a fresh isolated
 * {@link VmInstance} (its own heap over a defensive bytecode snapshot) wrapped in a {@link VMDebugSession}, keyed by
 * an opaque handle, so concurrent callers (e.g. subagents) run independent sessions that don't disturb each other or
 * the Bytecode Debugger UI. Maps YABR debug state to the public DTOs.
 */
public class VmDebugApiImpl implements VmDebugApi {

    private static final int MAX_SESSIONS = 16;

    private final Map<String, Session> sessions = new LinkedHashMap<>();

    private static final class Session {
        final VmInstance instance;
        final VMDebugSession debug;

        Session(VmInstance instance, VMDebugSession debug) {
            this.instance = instance;
            this.debug = debug;
        }
    }

    @Override
    public synchronized DebugState start(String className, String methodName, String descriptor,
                                         List<ArgSpec> args, boolean recursive) {
        VmInstance instance = VMExecutionService.getInstance().createSnapshotInstance();
        MethodEntry method = instance.findMethod(className, methodName, descriptor);
        if (method == null) {
            instance.dispose();
            throw new IllegalArgumentException("Method not found: " + className + "." + methodName + descriptor);
        }
        Object[] built = VmValueBuilder.build(instance, args);
        VMDebugSession debug = new VMDebugSession(instance);
        debug.start(method, recursive, built);

        evictIfFull();
        String handle = UUID.randomUUID().toString();
        sessions.put(handle, new Session(instance, debug));
        return toState(handle, debug);
    }

    @Override
    public synchronized DebugState step(String handle, StepMode mode) {
        Session s = sessions.get(handle);
        if (s == null || !s.debug.isStarted()) {
            return inactive(handle);
        }
        switch (mode) {
            case OVER:
                s.debug.stepOver();
                break;
            case OUT:
                s.debug.stepOut();
                break;
            default:
                s.debug.stepInto();
                break;
        }
        return toState(handle, s.debug);
    }

    @Override
    public synchronized DebugState current(String handle) {
        Session s = sessions.get(handle);
        return s != null ? toState(handle, s.debug) : inactive(handle);
    }

    @Override
    public synchronized boolean isActive(String handle) {
        Session s = sessions.get(handle);
        return s != null && s.debug.isStarted() && !s.debug.isStopped();
    }

    @Override
    public synchronized void stop(String handle) {
        Session s = sessions.remove(handle);
        if (s != null) {
            s.debug.stop();
            s.instance.dispose();
        }
    }

    /** Disposes the oldest session(s) when the live-session cap is reached (backstop against leaked handles). */
    private void evictIfFull() {
        while (sessions.size() >= MAX_SESSIONS) {
            Iterator<Map.Entry<String, Session>> it = sessions.entrySet().iterator();
            if (!it.hasNext()) {
                return;
            }
            Session oldest = it.next().getValue();
            it.remove();
            oldest.debug.stop();
            oldest.instance.dispose();
        }
    }

    private static DebugState inactive(String handle) {
        return new DebugState(handle, false, false, null, "", "", "", -1, -1,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private static DebugState toState(String handle, VMDebugSession s) {
        boolean terminated = s.isStopped();
        boolean active = s.isStarted() && !terminated;
        DebugResult result = terminated ? terminalResult(s) : null;

        DebugStateModel m = s.getLastState();
        if (m == null) {
            return new DebugState(handle, active, terminated, result, "", "", "", -1, -1,
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }

        List<StackSlot> stack = new ArrayList<>();
        for (StackEntry e : m.getOperandStack()) {
            stack.add(new StackSlot(e.getIndex(), e.getValue(), e.getTypeName(), e.isWide()));
        }
        List<Local> locals = new ArrayList<>();
        for (LocalEntry e : m.getLocalVariables()) {
            locals.add(new Local(e.getSlot(), e.getName(), e.getTypeName(), e.getValue()));
        }
        List<Frame> frames = new ArrayList<>();
        for (FrameEntry f : m.getCallStack()) {
            frames.add(new Frame(f.getClassName(), f.getMethodName(), f.getDescriptor(),
                    f.getInstructionIndex(), f.getLineNumber(), f.isCurrent()));
        }
        return new DebugState(handle, active, terminated, result, m.getClassName(), m.getMethodName(),
                m.getDescriptor(), m.getInstructionIndex(), m.getLineNumber(), stack, locals, frames);
    }

    private static DebugResult terminalResult(VMDebugSession s) {
        DebugSession yabr = s.getYabrSession();
        if (yabr == null) {
            return null;
        }
        BytecodeResult result = yabr.getResult();
        if (result == null) {
            return null;
        }
        String returnValue = null;
        String exception = null;
        if (result.hasException()) {
            exception = String.valueOf(result.getException());
        } else if (result.getReturnValue() != null) {
            returnValue = formatValue(result.getReturnValue());
        }
        return new DebugResult(result.isSuccess(), returnValue, exception, result.getInstructionsExecuted());
    }

    private static String formatValue(ConcreteValue v) {
        if (v == null || v.isNull()) {
            return "null";
        }
        switch (v.getTag()) {
            case INT:
                return String.valueOf(v.asInt());
            case LONG:
                return v.asLong() + "L";
            case FLOAT:
                return v.asFloat() + "f";
            case DOUBLE:
                return String.valueOf(v.asDouble());
            case REFERENCE:
                return v.asReference() != null ? v.asReference().toString() : "null";
            default:
                return v.toString();
        }
    }
}
