package com.tonic.ui.vm.debugger;

import com.tonic.parser.MethodEntry;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Owns the debugger's breakpoint PC set and the toggle logic that registers/unregisters breakpoints against the
 * {@link VMDebugSession} for the currently displayed method. After each change it fires a refresh callback so the
 * bytecode table and source gutter can redraw their breakpoint dots.
 */
final class BreakpointController {

    private final Set<Integer> breakpoints = new HashSet<>();
    private final VMDebugSession session;
    private final Supplier<MethodEntry> displayedMethod;
    private final Consumer<String> output;
    private final Runnable onChanged;

    BreakpointController(VMDebugSession session,
                         Supplier<MethodEntry> displayedMethod,
                         Consumer<String> output,
                         Runnable onChanged) {
        this.session = session;
        this.displayedMethod = displayedMethod;
        this.output = output;
        this.onChanged = onChanged;
    }

    /** The live set of breakpoint PCs; consumed (read-only) by the table renderer and source view. */
    Set<Integer> getBreakpoints() {
        return breakpoints;
    }

    void clear() {
        breakpoints.clear();
    }

    /**
     * Toggles a breakpoint at a PC of the DISPLAYED method (which differs from the entry method while stepping
     * through callees in recursive mode) and fires the refresh callback to sync the table and source gutter dots.
     */
    void toggleBreakpointAtPc(int pc) {
        MethodEntry method = displayedMethod.get();
        if (method == null) return;

        String className = method.getOwnerName();
        String methodName = method.getName();
        String desc = method.getDesc();

        if (breakpoints.contains(pc)) {
            breakpoints.remove(pc);
            session.removeBreakpoint(className, methodName, desc, pc);
            output.accept("Breakpoint removed at PC " + pc);
        } else {
            breakpoints.add(pc);
            session.addBreakpoint(className, methodName, desc, pc);
            output.accept("Breakpoint set at PC " + pc);
        }
        onChanged.run();
    }
}
