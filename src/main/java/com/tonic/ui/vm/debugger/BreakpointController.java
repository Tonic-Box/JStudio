package com.tonic.ui.vm.debugger;

import com.tonic.parser.MethodEntry;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class BreakpointController
{

    private final Set<Integer> breakpoints = new HashSet<>();
    private final VMDebugSession session;
    private final Supplier<MethodEntry> displayedMethod;
    private final Consumer<String> output;
    private final Runnable onChanged;

    BreakpointController(VMDebugSession session, Supplier<MethodEntry> displayedMethod, Consumer<String> output, Runnable onChanged)
    {
        this.session = session;
        this.displayedMethod = displayedMethod;
        this.output = output;
        this.onChanged = onChanged;
    }

    Set<Integer> getBreakpoints()
    {
        return breakpoints;
    }

    void clear()
    {
        breakpoints.clear();
    }

    void toggleBreakpointAtPc(int pc)
    {
        MethodEntry method = displayedMethod.get();
        if (method == null) return;

        String className = method.getOwnerName();
        String methodName = method.getName();
        String desc = method.getDesc();

        if (breakpoints.contains(pc))
        {
            breakpoints.remove(pc);
            session.removeBreakpoint(className, methodName, desc, pc);
            output.accept("Breakpoint removed at PC " + pc);
        }
        else
        {
            breakpoints.add(pc);
            session.addBreakpoint(className, methodName, desc, pc);
            output.accept("Breakpoint set at PC " + pc);
        }
        onChanged.run();
    }
}
