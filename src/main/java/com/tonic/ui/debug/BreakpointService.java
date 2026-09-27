package com.tonic.ui.debug;

import com.tonic.event.EventBus;
import com.tonic.event.events.BreakpointsChangedEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The registry of breakpoints shared by the source and bytecode gutters; settable before a debugger attaches and re-armed on each connect. */
public final class BreakpointService
{

    private static final BreakpointService INSTANCE = new BreakpointService();

    private final Set<Breakpoint> breakpoints = new LinkedHashSet<>();

    private BreakpointService()
    {
    }

    /** @return the shared registry */
    public static BreakpointService getInstance()
    {
        return INSTANCE;
    }

    /**
     * Tells whether a breakpoint is set.
     *
     * @param bp the breakpoint
     * @return true if it is in the registry
     */
    public synchronized boolean contains(Breakpoint bp)
    {
        return breakpoints.contains(bp);
    }

    /**
     * Lists the breakpoints in one class.
     *
     * @param className the dotted class name
     * @return a new list of the class's breakpoints, empty if none
     */
    public synchronized List<Breakpoint> forClass(String className)
    {
        List<Breakpoint> out = new ArrayList<>();
        for (Breakpoint bp : breakpoints)
        {
            if (bp.className.equals(className))
            {
                out.add(bp);
            }
        }
        return out;
    }

    /**
     * Lists every breakpoint in the order it was set.
     *
     * @return a new list of all breakpoints
     */
    public synchronized List<Breakpoint> all()
    {
        return new ArrayList<>(breakpoints);
    }

    /**
     * Toggles a breakpoint, installs or removes it in the live session, and posts a breakpoints-changed event.
     *
     * @param bp the breakpoint
     * @return true if it is now set
     */
    public boolean toggle(Breakpoint bp)
    {
        boolean nowSet;
        synchronized (this)
        {
            if (breakpoints.remove(bp))
            {
                nowSet = false;
            }
            else
            {
                breakpoints.add(bp);
                nowSet = true;
            }
        }
        DebugManager dm = DebugManager.getInstance();
        if (nowSet)
        {
            dm.addBreakpoint(bp.className, bp.methodName, bp.methodDesc, bp.pc);
        }
        else
        {
            dm.removeBreakpoint(bp.className, bp.methodName, bp.methodDesc, bp.pc);
        }
        EventBus.getInstance().post(new BreakpointsChangedEvent(this));
        return nowSet;
    }

    /** Re-installs the registry into a freshly connected session (a no-op while empty, the usual case). */
    public synchronized void reinstall()
    {
        DebugManager dm = DebugManager.getInstance();
        for (Breakpoint bp : breakpoints)
        {
            dm.addBreakpoint(bp.className, bp.methodName, bp.methodDesc, bp.pc);
        }
    }

    /** Removes every breakpoint from the registry and from the live session, and posts a breakpoints-changed event if there were any. */
    public void clear()
    {
        List<Breakpoint> removed;
        synchronized (this)
        {
            if (breakpoints.isEmpty())
            {
                return;
            }
            removed = new ArrayList<>(breakpoints);
            breakpoints.clear();
        }
        DebugManager dm = DebugManager.getInstance();
        for (Breakpoint bp : removed)
        {
            dm.removeBreakpoint(bp.className, bp.methodName, bp.methodDesc, bp.pc);
        }
        EventBus.getInstance().post(new BreakpointsChangedEvent(this));
    }
}
