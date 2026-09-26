package com.tonic.ui.vm.debugger;

import com.tonic.parser.MethodEntry;

import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class BreakpointController
{

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
        MethodEntry method = displayedMethod.get();
        if (method == null)
        {
            return Collections.emptySet();
        }
        return session.getBreakpointPcs(method.getOwnerName(), method.getName(), method.getDesc());
    }

    void toggleBreakpointAtPc(int pc)
    {
        MethodEntry method = displayedMethod.get();
        if (method == null)
        {
            return;
        }

        String className = method.getOwnerName();
        String methodName = method.getName();
        String desc = method.getDesc();

        if (getBreakpoints().contains(pc))
        {
            session.removeBreakpoint(className, methodName, desc, pc);
            output.accept("Breakpoint removed at PC " + pc);
        }
        else
        {
            session.addBreakpoint(className, methodName, desc, pc);
            output.accept("Breakpoint set at PC " + pc);
        }
        onChanged.run();
    }
}
