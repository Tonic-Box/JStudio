package com.tonic.live.debug;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.ClassType;
import com.sun.jdi.Field;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A live JDI debug session: owns the attached {@link VirtualMachine}, pumps its event queue on a daemon thread,
 * installs breakpoints (resolving them now or deferring via class-prepare until the class loads), and exposes
 * resume plus read-only call-stack / variable inspection. Phase 1 scope; stepping and edits come later.
 *
 * <p>All target interaction happens while the VM is suspended at a breakpoint. The event-thread callbacks on
 * {@link DebugListener} are invoked off the EDT.
 */
public final class DebugSession {

    private final VirtualMachine vm;
    private final DebugListener listener;
    private final Thread pump;
    /** Name prefix of the in-process agent's thread(s) to keep running while suspended, so the agent can work
     * against the frozen heap; null disables it. */
    private final String agentThreadPrefix;
    private volatile boolean suspendAll;

    private volatile boolean running = true;
    private volatile ThreadReference pausedThread;
    private volatile boolean pausedAll;

    private final List<BreakpointSpec> breakpoints = new ArrayList<>();
    private final List<BreakpointRequest> installed = new ArrayList<>();
    private final Map<String, ClassPrepareRequest> prepareRequests = new HashMap<>();

    private DebugSession(VirtualMachine vm, DebugListener listener, boolean suspendAll, String agentThreadPrefix) {
        this.vm = vm;
        this.listener = listener;
        this.suspendAll = suspendAll;
        this.agentThreadPrefix = agentThreadPrefix;
        this.pump = new Thread(this::pumpLoop, "jstudio-jdi-events");
        this.pump.setDaemon(true);
        this.pump.start();
    }

    /**
     * Attaches JDI to a target serving JDWP at {@code host:port} and starts pumping events. {@code
     * agentThreadPrefix} names the in-process agent's thread(s) to keep running during a suspend-all pause so
     * the agent can scan/read/edit the frozen heap; pass null to leave them suspended with everything else.
     */
    public static DebugSession attach(String host, int port, DebugListener listener, boolean suspendAll,
                                      String agentThreadPrefix) throws IOException {
        VirtualMachine vm = DebugConnector.attach(host, port);
        return new DebugSession(vm, listener, suspendAll, agentThreadPrefix);
    }

    public boolean isPaused() {
        return pausedThread != null;
    }

    /** Updates the suspend policy applied to breakpoints installed from now on (existing ones keep theirs). */
    public void setSuspendAll(boolean suspendAll) {
        this.suspendAll = suspendAll;
    }

    // ---- JDI-backed object reach (the "dropbox" hand-off to the agent) -------------------------------

    /**
     * Suspends the VM, enumerates up to {@code max} live instances of {@code className}, parks them in the
     * agent's dropbox static field ({@code dropBoxClass#boxField}), and resumes. Returns the count parked, or
     * -1 if JDI can't do it (no instance-info capability, unavailable types) so the caller falls back to the
     * agent walk. The parked array strong-holds the set so it survives the resume until the agent consumes it.
     */
    public synchronized int parkInstances(String dropBoxClass, String boxField, String className, int max) {
        if (!vm.canGetInstanceInfo()) {
            return -1;
        }
        boolean suspended = false;
        try {
            vm.suspend();
            suspended = true;
            List<ObjectReference> refs = new ArrayList<>();
            for (ReferenceType rt : vm.classesByName(className)) {
                if (refs.size() >= max) {
                    break;
                }
                refs.addAll(rt.instances(max - refs.size()));
            }
            return parkArray(dropBoxClass, boxField, refs);
        } catch (Exception e) {
            return -1;
        } finally {
            if (suspended) {
                try {
                    vm.resume();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Suspends the VM, harvests the object references held by every thread's stack frames (the objects the
     * agent's reachability walk can't reach), parks them in the dropbox as extra scan roots, and resumes.
     * Returns the count parked, or -1 on failure (caller falls back to the agent-only roots).
     */
    public synchronized int parkStackRoots(String dropBoxClass, String boxField, int max) {
        boolean suspended = false;
        try {
            vm.suspend();
            suspended = true;
            List<ObjectReference> refs = new ArrayList<>();
            for (ThreadReference t : vm.allThreads()) {
                if (refs.size() >= max) {
                    break;
                }
                List<StackFrame> frames;
                try {
                    frames = t.frames();
                } catch (Exception e) {
                    continue;
                }
                for (StackFrame frame : frames) {
                    if (refs.size() >= max) {
                        break;
                    }
                    harvestFrame(frame, refs, max);
                }
            }
            return parkArray(dropBoxClass, boxField, refs);
        } catch (Exception e) {
            return -1;
        } finally {
            if (suspended) {
                try {
                    vm.resume();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** Adds the object references held by one frame: {@code this}, arguments, and locals (when LVT is present). */
    private void harvestFrame(StackFrame frame, List<ObjectReference> refs, int max) {
        try {
            ObjectReference self = frame.thisObject();
            if (self != null && refs.size() < max) {
                refs.add(self);
            }
        } catch (Exception ignored) {
        }
        try {
            for (Value v : frame.getArgumentValues()) {
                if (v instanceof ObjectReference && refs.size() < max) {
                    refs.add((ObjectReference) v);
                }
            }
        } catch (Exception ignored) {
        }
        try {
            for (Value v : frame.getValues(frame.visibleVariables()).values()) {
                if (v instanceof ObjectReference && refs.size() < max) {
                    refs.add((ObjectReference) v);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** Creates an {@code Object[]} in the target, fills it with {@code refs}, and stores it in the static field. */
    private int parkArray(String dropBoxClass, String boxField, List<ObjectReference> refs) throws Exception {
        List<ReferenceType> arrayTypes = vm.classesByName("java.lang.Object[]");
        if (arrayTypes.isEmpty() || !(arrayTypes.get(0) instanceof ArrayType)) {
            return -1;
        }
        ArrayType objectArrayType = (ArrayType) arrayTypes.get(0);
        ArrayReference array = objectArrayType.newInstance(refs.size());
        if (!refs.isEmpty()) {
            array.setValues(refs);
        }
        List<ReferenceType> boxTypes = vm.classesByName(dropBoxClass);
        if (boxTypes.isEmpty() || !(boxTypes.get(0) instanceof ClassType)) {
            return -1;
        }
        ClassType boxType = (ClassType) boxTypes.get(0);
        Field field = boxType.fieldByName(boxField);
        if (field == null) {
            return -1;
        }
        boxType.setValue(field, array);
        return refs.size();
    }

    // ---- breakpoints --------------------------------------------------------------------------------

    /** Adds a breakpoint at a bytecode offset; installs on already-loaded classes and arms a class-prepare hook. */
    public synchronized void addBreakpoint(String className, String methodName, String methodDesc, long pc) {
        BreakpointSpec spec = new BreakpointSpec(className, methodName, methodDesc, pc);
        breakpoints.add(spec);
        for (ReferenceType rt : vm.classesByName(className)) {
            installSpec(rt, spec);
        }
        prepareRequests.computeIfAbsent(className, cn -> {
            ClassPrepareRequest req = vm.eventRequestManager().createClassPrepareRequest();
            req.addClassFilter(cn);
            req.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
            req.enable();
            return req;
        });
    }

    public synchronized void removeBreakpoint(String className, String methodName, String methodDesc, long pc) {
        breakpoints.removeIf(s -> s.matches(className, methodName, methodDesc, pc));
        List<BreakpointRequest> drop = new ArrayList<>();
        for (BreakpointRequest req : installed) {
            Location loc = req.location();
            if (loc.declaringType().name().equals(className)
                    && loc.method().name().equals(methodName)
                    && loc.method().signature().equals(methodDesc)
                    && loc.codeIndex() == pc) {
                drop.add(req);
            }
        }
        if (!drop.isEmpty()) {
            try {
                vm.eventRequestManager().deleteEventRequests(drop);
            } catch (VMDisconnectedException ignored) {
            }
            installed.removeAll(drop);
        }
    }

    private void installSpec(ReferenceType rt, BreakpointSpec spec) {
        for (Method m : rt.methodsByName(spec.methodName)) {
            if (!m.signature().equals(spec.methodDesc)) {
                continue;
            }
            try {
                Location loc = m.locationOfCodeIndex(spec.pc);
                if (loc == null) {
                    continue;
                }
                BreakpointRequest req = vm.eventRequestManager().createBreakpointRequest(loc);
                req.setSuspendPolicy(suspendAll ? EventRequest.SUSPEND_ALL : EventRequest.SUSPEND_EVENT_THREAD);
                req.enable();
                installed.add(req);
            } catch (Exception ignored) {
            }
        }
    }

    // ---- execution control --------------------------------------------------------------------------

    /** Resumes the target from a breakpoint. */
    public synchronized void resume() {
        ThreadReference t = pausedThread;
        pausedThread = null;
        listener.onResumed();
        try {
            if (pausedAll || t == null) {
                vm.resume();
            } else {
                t.resume();
            }
        } catch (VMDisconnectedException ignored) {
        }
    }

    /** Ends the session: disconnects JDI and stops the event pump. */
    public void dispose() {
        running = false;
        try {
            vm.dispose();
        } catch (Exception ignored) {
        }
        pump.interrupt();
    }

    // ---- inspection (only valid while paused) -------------------------------------------------------

    public List<DebugFrame> frames() {
        List<DebugFrame> out = new ArrayList<>();
        ThreadReference t = pausedThread;
        if (t == null) {
            return out;
        }
        try {
            List<StackFrame> frames = t.frames();
            for (int i = 0; i < frames.size(); i++) {
                DebugLocation loc = toLocation(frames.get(i).location());
                out.add(new DebugFrame(i, loc, frameDisplay(loc)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public List<DebugVariable> variables(int frameIndex) {
        List<DebugVariable> out = new ArrayList<>();
        ThreadReference t = pausedThread;
        if (t == null) {
            return out;
        }
        try {
            StackFrame frame = t.frame(frameIndex);
            ObjectReference self = frame.thisObject();
            if (self != null) {
                out.add(new DebugVariable("this", self.referenceType().signature(), label(self), true));
            }
            try {
                for (LocalVariable lv : frame.visibleVariables()) {
                    out.add(toVar(lv.name(), lv.signature(), frame.getValue(lv)));
                }
            } catch (AbsentInformationException noLvt) {
                List<Value> args = frame.getArgumentValues();
                for (int i = 0; i < args.size(); i++) {
                    out.add(toVar("arg" + i, "", args.get(i)));
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    // ---- event pump ---------------------------------------------------------------------------------

    private void pumpLoop() {
        try {
            EventQueue queue = vm.eventQueue();
            while (running) {
                EventSet set = queue.remove();
                boolean resumeAfter = true;
                for (Event event : set) {
                    if (event instanceof BreakpointEvent) {
                        handlePause(((BreakpointEvent) event).thread(), set.suspendPolicy());
                        resumeAfter = false;
                    } else if (event instanceof ClassPrepareEvent) {
                        installPending(((ClassPrepareEvent) event).referenceType());
                    } else if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) {
                        running = false;
                        resumeAfter = false;
                        listener.onDisconnected();
                    }
                }
                if (resumeAfter && running) {
                    set.resume();
                }
            }
        } catch (VMDisconnectedException e) {
            if (running) {
                running = false;
                listener.onDisconnected();
            }
        } catch (InterruptedException ignored) {
            // shutting down
        }
    }

    private void handlePause(ThreadReference thread, int suspendPolicy) {
        this.pausedThread = thread;
        this.pausedAll = suspendPolicy == EventRequest.SUSPEND_ALL;
        if (pausedAll) {
            resumeAgentThreads();
        }
        listener.onPaused(topLocation(thread), frames());
    }

    /**
     * Resumes just the in-process agent's thread(s) while every application thread stays frozen, so the agent
     * can scan/read/edit a stable, frozen-moment heap. They re-suspend automatically on the next suspend-all
     * pause; the user's Resume releases everything together. The agent does only reflection (no application
     * code, no application locks), so the resumed thread cannot deadlock on a lock a frozen thread holds.
     */
    private void resumeAgentThreads() {
        if (agentThreadPrefix == null) {
            return;
        }
        try {
            for (ThreadReference t : vm.allThreads()) {
                String name = t.name();
                if (name != null && name.startsWith(agentThreadPrefix)) {
                    while (t.suspendCount() > 0) {
                        t.resume();
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private synchronized void installPending(ReferenceType rt) {
        for (BreakpointSpec spec : breakpoints) {
            if (spec.className.equals(rt.name())) {
                installSpec(rt, spec);
            }
        }
    }

    private DebugLocation topLocation(ThreadReference t) {
        try {
            return toLocation(t.frame(0).location());
        } catch (Exception e) {
            return null;
        }
    }

    // ---- marshalling --------------------------------------------------------------------------------

    private static DebugLocation toLocation(Location loc) {
        Method m = loc.method();
        int line;
        try {
            line = loc.lineNumber();
        } catch (Exception e) {
            line = -1;
        }
        return new DebugLocation(loc.declaringType().name(), m.name(), m.signature(), loc.codeIndex(), line);
    }

    private static String frameDisplay(DebugLocation loc) {
        String at = loc.getLineNumber() > 0 ? " : " + loc.getLineNumber() : "";
        return loc.getClassName() + "." + loc.getMethodName() + "()" + at;
    }

    private static DebugVariable toVar(String name, String signature, Value v) {
        boolean ref = v instanceof ObjectReference && !(v instanceof StringReference);
        return new DebugVariable(name, signature, label(v), ref);
    }

    private static String label(Value v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof StringReference) {
            return "\"" + ((StringReference) v).value() + "\"";
        }
        if (v instanceof ObjectReference) {
            ObjectReference o = (ObjectReference) v;
            return o.referenceType().name() + "@" + o.uniqueID();
        }
        return v.toString();
    }

    private static final class BreakpointSpec {
        final String className;
        final String methodName;
        final String methodDesc;
        final long pc;

        BreakpointSpec(String className, String methodName, String methodDesc, long pc) {
            this.className = className;
            this.methodName = methodName;
            this.methodDesc = methodDesc;
            this.pc = pc;
        }

        boolean matches(String c, String n, String d, long p) {
            return className.equals(c) && methodName.equals(n) && methodDesc.equals(d) && pc == p;
        }
    }
}
