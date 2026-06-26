package com.tonic.ui.debug;

import com.tonic.event.EventBus;
import com.tonic.event.events.BreakpointsChangedEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Registry of breakpoints (keyed by class + method + bytecode offset). Breakpoints are settable any time -
 * including before a debugger is attached - and persist across sessions: {@link #reinstall} arms them whenever
 * a session connects. The source and bytecode gutters render from this single set, so a breakpoint set in one
 * view appears in the other; mutations install/remove in the live session immediately and post a
 * {@link BreakpointsChangedEvent} so the gutters refresh.
 */
public final class BreakpointService {

    private static final BreakpointService INSTANCE = new BreakpointService();

    private final Set<Breakpoint> breakpoints = new LinkedHashSet<>();

    private BreakpointService() {
    }

    public static BreakpointService getInstance() {
        return INSTANCE;
    }

    public synchronized boolean contains(Breakpoint bp) {
        return breakpoints.contains(bp);
    }

    public synchronized List<Breakpoint> forClass(String className) {
        List<Breakpoint> out = new ArrayList<>();
        for (Breakpoint bp : breakpoints) {
            if (bp.className.equals(className)) {
                out.add(bp);
            }
        }
        return out;
    }

    public synchronized List<Breakpoint> all() {
        return new ArrayList<>(breakpoints);
    }

    /** Toggles a breakpoint; returns true if it is now set. Installs/removes in the live session immediately. */
    public boolean toggle(Breakpoint bp) {
        boolean nowSet;
        synchronized (this) {
            if (breakpoints.remove(bp)) {
                nowSet = false;
            } else {
                breakpoints.add(bp);
                nowSet = true;
            }
        }
        DebugManager dm = DebugManager.getInstance();
        if (nowSet) {
            dm.addBreakpoint(bp.className, bp.methodName, bp.methodDesc, bp.pc);
        } else {
            dm.removeBreakpoint(bp.className, bp.methodName, bp.methodDesc, bp.pc);
        }
        EventBus.getInstance().post(new BreakpointsChangedEvent(this));
        return nowSet;
    }

    /** Re-installs the registry into a freshly connected session (a no-op while empty, the usual case). */
    public synchronized void reinstall() {
        DebugManager dm = DebugManager.getInstance();
        for (Breakpoint bp : breakpoints) {
            dm.addBreakpoint(bp.className, bp.methodName, bp.methodDesc, bp.pc);
        }
    }

    public void clear() {
        synchronized (this) {
            if (breakpoints.isEmpty()) {
                return;
            }
            breakpoints.clear();
        }
        EventBus.getInstance().post(new BreakpointsChangedEvent(this));
    }
}
