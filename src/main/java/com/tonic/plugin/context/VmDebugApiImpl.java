package com.tonic.plugin.context;

import com.tonic.analysis.execution.core.BytecodeResult;
import com.tonic.analysis.execution.debug.DebugSession;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.MethodEntry;
import com.tonic.plugin.api.VmDebugApi;
import com.tonic.ui.vm.VMExecutionService;
import com.tonic.ui.vm.debugger.DebugStateModel;
import com.tonic.ui.vm.debugger.FrameEntry;
import com.tonic.ui.vm.debugger.LocalEntry;
import com.tonic.ui.vm.debugger.StackEntry;
import com.tonic.ui.vm.debugger.VMDebugSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Drives JStudio's bytecode interpreter/debugger ({@link VMExecutionService} + YABR {@code DebugSession}) for
 * plugins. The VM is a single global session shared with the Bytecode Debugger UI, so the active wrapper is held
 * statically; {@link #start} replaces any running session ("AI takes over"). Reuses {@link VMDebugSession} for
 * stepping and its YABR-state-&gt;model conversion, then maps to the public DTOs.
 */
public class VmDebugApiImpl implements VmDebugApi {

    private static VMDebugSession session;

    @Override
    public DebugState start(String className, String methodName, String descriptor,
                            List<ArgSpec> args, boolean recursive) {
        VMExecutionService vm = VMExecutionService.getInstance();
        if (!vm.isInitialized()) {
            vm.initialize();
        }
        MethodEntry method = vm.findMethod(className, methodName, descriptor);
        if (method == null) {
            throw new IllegalArgumentException("Method not found: " + className + "." + methodName + descriptor);
        }
        Object[] built = VmValueBuilder.build(vm, args);
        VMDebugSession fresh = new VMDebugSession();
        fresh.start(method, recursive, built);
        session = fresh;
        return toState(fresh);
    }

    @Override
    public DebugState step(StepMode mode) {
        VMDebugSession current = session;
        if (current == null || !current.isStarted()) {
            return inactive();
        }
        switch (mode) {
            case OVER:
                current.stepOver();
                break;
            case OUT:
                current.stepOut();
                break;
            default:
                current.stepInto();
                break;
        }
        return toState(current);
    }

    @Override
    public DebugState current() {
        VMDebugSession current = session;
        return current != null ? toState(current) : inactive();
    }

    @Override
    public boolean isActive() {
        VMDebugSession current = session;
        return current != null && current.isStarted() && !current.isStopped();
    }

    @Override
    public void stop() {
        VMDebugSession current = session;
        if (current != null) {
            current.stop();
        }
    }

    private static DebugState inactive() {
        return new DebugState(false, false, null, "", "", "", -1, -1,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private static DebugState toState(VMDebugSession s) {
        boolean terminated = s.isStopped();
        boolean active = s.isStarted() && !terminated;
        DebugResult result = terminated ? terminalResult() : null;

        DebugStateModel m = s.getLastState();
        if (m == null) {
            return new DebugState(active, terminated, result, "", "", "", -1, -1,
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
        return new DebugState(active, terminated, result, m.getClassName(), m.getMethodName(),
                m.getDescriptor(), m.getInstructionIndex(), m.getLineNumber(), stack, locals, frames);
    }

    private static DebugResult terminalResult() {
        DebugSession yabr = VMExecutionService.getInstance().getCurrentDebugSession();
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
