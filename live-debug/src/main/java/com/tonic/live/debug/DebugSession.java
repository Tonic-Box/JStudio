package com.tonic.live.debug;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.CharValue;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** A live JDI debug session: owns the attached VM, pumps its events on a daemon thread, installs breakpoints now or when their class loads, and inspects the paused thread. */
public final class DebugSession
{

    private final VirtualMachine vm;
    private final DebugListener listener;
    private final Thread pump;
    private final String agentThreadPrefix;
    private volatile boolean suspendAll;

    private volatile boolean running = true;
    private volatile ThreadReference pausedThread;
    private volatile boolean pausedAll;

    private final Map<Long, ObjectReference> refHandles = new ConcurrentHashMap<>();
    private final AtomicLong refIds = new AtomicLong(1);

    private final List<BreakpointSpec> breakpoints = new ArrayList<>();
    private final List<BreakpointRequest> installed = new ArrayList<>();
    private final Map<String, ClassPrepareRequest> prepareRequests = new HashMap<>();

    private DebugSession(VirtualMachine vm, DebugListener listener, boolean suspendAll, String agentThreadPrefix)
    {
        this.vm = vm;
        this.listener = listener;
        this.suspendAll = suspendAll;
        this.agentThreadPrefix = agentThreadPrefix;
        this.pump = new Thread(this::pumpLoop, "jstudio-jdi-events");
        this.pump.setDaemon(true);
    }

    /** Starts the event pump; call it after installing breakpoints so they catch the startup of a target launched suspended. */
    public void start()
    {
        pump.start();
    }

    /**
     * Attaches JDI to a target serving JDWP; the event pump starts only on start.
     *
     * @param host the target's host
     * @param port the JDWP port
     * @param listener receives the session's events on the event thread
     * @param suspendAll whether new breakpoints suspend every thread rather than only the one that hit
     * @param agentThreadPrefix the name prefix of agent threads to keep running during a suspend-all pause, or null to suspend them too
     * @return the session
     * @throws IOException if the attach fails
     */
    public static DebugSession attach(String host, int port, DebugListener listener, boolean suspendAll, String agentThreadPrefix) throws IOException
    {
        VirtualMachine vm = DebugConnector.attach(host, port);
        return new DebugSession(vm, listener, suspendAll, agentThreadPrefix);
    }

    /**
     * Reports whether the target is paused at a breakpoint.
     *
     * @return true while a thread is paused
     */
    public boolean isPaused()
    {
        return pausedThread != null;
    }

    /**
     * Sets the suspend policy for breakpoints installed from now on; installed ones keep theirs.
     *
     * @param suspendAll whether to suspend every thread rather than only the one that hit
     */
    public void setSuspendAll(boolean suspendAll)
    {
        this.suspendAll = suspendAll;
    }

    /**
     * Suspends the VM, parks up to a cap of a class's live instances in the agent's dropbox field, and resumes.
     *
     * @param dropBoxClass the binary name of the agent's dropbox class
     * @param boxField the static field in it that holds the parked array
     * @param className the binary name of the class whose instances to park
     * @param max the cap on instances parked
     * @return the count parked, or -1 when JDI cannot do it and the caller should fall back to the agent walk
     */
    public synchronized int parkInstances(String dropBoxClass, String boxField, String className, int max)
    {
        if (!vm.canGetInstanceInfo())
        {
            return -1;
        }
        boolean suspended = false;
        try
        {
            vm.suspend();
            suspended = true;
            List<ObjectReference> refs = new ArrayList<>();
            for (ReferenceType rt : vm.classesByName(className))
            {
                if (refs.size() >= max)
                {
                    break;
                }
                refs.addAll(rt.instances(max - refs.size()));
            }
            return parkArray(dropBoxClass, boxField, refs);
        }
        catch (Exception e)
        {
            return -1;
        }
        finally
        {
            if (suspended)
            {
                try
                {
                    vm.resume();
                }
                catch (Exception ignored)
                {
                }
            }
        }
    }

    /**
     * Suspends the VM, parks the objects held by every thread's stack frames in the agent's dropbox field as extra scan roots, and resumes.
     *
     * @param dropBoxClass the binary name of the agent's dropbox class
     * @param boxField the static field in it that holds the parked array
     * @param max the cap on objects parked
     * @return the count parked, or -1 on failure, when the caller should fall back to the agent's own roots
     */
    public synchronized int parkStackRoots(String dropBoxClass, String boxField, int max)
    {
        boolean suspended = false;
        try
        {
            vm.suspend();
            suspended = true;
            List<ObjectReference> refs = new ArrayList<>();
            for (ThreadReference t : vm.allThreads())
            {
                if (refs.size() >= max)
                {
                    break;
                }
                List<StackFrame> frames;
                try
                {
                    frames = t.frames();
                }
                catch (Exception e)
                {
                    continue;
                }
                for (StackFrame frame : frames)
                {
                    if (refs.size() >= max)
                    {
                        break;
                    }
                    harvestFrame(frame, refs, max);
                }
            }
            return parkArray(dropBoxClass, boxField, refs);
        }
        catch (Exception e)
        {
            return -1;
        }
        finally
        {
            if (suspended)
            {
                try
                {
                    vm.resume();
                }
                catch (Exception ignored)
                {
                }
            }
        }
    }

    private void harvestFrame(StackFrame frame, List<ObjectReference> refs, int max)
    {
        try
        {
            ObjectReference self = frame.thisObject();
            if (self != null && refs.size() < max)
            {
                refs.add(self);
            }
        }
        catch (Exception ignored)
        {
        }
        try
        {
            for (Value v : frame.getArgumentValues())
            {
                if (v instanceof ObjectReference && refs.size() < max)
                {
                    refs.add((ObjectReference) v);
                }
            }
        }
        catch (Exception ignored)
        {
        }
        try
        {
            for (Value v : frame.getValues(frame.visibleVariables()).values())
            {
                if (v instanceof ObjectReference && refs.size() < max)
                {
                    refs.add((ObjectReference) v);
                }
            }
        }
        catch (Exception ignored)
        {
        }
    }

    private int parkArray(String dropBoxClass, String boxField, List<ObjectReference> refs) throws Exception
    {
        List<ReferenceType> arrayTypes = vm.classesByName("java.lang.Object[]");
        if (arrayTypes.isEmpty() || !(arrayTypes.get(0) instanceof ArrayType))
        {
            return -1;
        }
        ArrayType objectArrayType = (ArrayType) arrayTypes.get(0);
        ArrayReference array = objectArrayType.newInstance(refs.size());
        if (!refs.isEmpty())
        {
            array.setValues(refs);
        }
        List<ReferenceType> boxTypes = vm.classesByName(dropBoxClass);
        if (boxTypes.isEmpty() || !(boxTypes.get(0) instanceof ClassType))
        {
            return -1;
        }
        ClassType boxType = (ClassType) boxTypes.get(0);
        Field field = boxType.fieldByName(boxField);
        if (field == null)
        {
            return -1;
        }
        boxType.setValue(field, array);
        return refs.size();
    }

    /**
     * Adds a breakpoint at a bytecode offset, installing it on loaded classes and arming a class-prepare hook for later loads.
     *
     * @param className the declaring class's binary name
     * @param methodName the method name
     * @param methodDesc the method's JVM descriptor
     * @param pc the bytecode offset
     */
    public synchronized void addBreakpoint(String className, String methodName, String methodDesc, long pc)
    {
        BreakpointSpec spec = new BreakpointSpec(className, methodName, methodDesc, pc);
        breakpoints.add(spec);
        for (ReferenceType rt : vm.classesByName(className))
        {
            installSpec(rt, spec);
        }
        prepareRequests.computeIfAbsent(className, cn ->
        {
            ClassPrepareRequest req = vm.eventRequestManager().createClassPrepareRequest();
            req.addClassFilter(cn);
            req.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
            req.enable();
            return req;
        });
    }

    /**
     * Removes a breakpoint at a bytecode offset and deletes its installed requests, plus the class-prepare hook once the class has no breakpoints left; requests whose location can no longer be read are deleted too.
     *
     * @param className the declaring class's binary name
     * @param methodName the method name
     * @param methodDesc the method's JVM descriptor
     * @param pc the bytecode offset
     */
    public synchronized void removeBreakpoint(String className, String methodName, String methodDesc, long pc)
    {
        breakpoints.removeIf(s -> s.matches(className, methodName, methodDesc, pc));
        List<BreakpointRequest> drop = new ArrayList<>();
        for (BreakpointRequest req : installed)
        {
            if (isAt(req, className, methodName, methodDesc, pc))
            {
                drop.add(req);
            }
        }
        List<EventRequest> delete = new ArrayList<>(drop);
        installed.removeAll(drop);
        if (breakpoints.stream().noneMatch(s -> s.className.equals(className)))
        {
            ClassPrepareRequest prepare = prepareRequests.remove(className);
            if (prepare != null)
            {
                delete.add(prepare);
            }
        }
        if (!delete.isEmpty())
        {
            try
            {
                vm.eventRequestManager().deleteEventRequests(delete);
            }
            catch (VMDisconnectedException ignored)
            {
            }
        }
    }

    private static boolean isAt(BreakpointRequest req, String className, String methodName, String methodDesc, long pc)
    {
        try
        {
            Location loc = req.location();
            return loc.declaringType().name().equals(className) && loc.method().name().equals(methodName) && loc.method().signature().equals(methodDesc) && loc.codeIndex() == pc;
        }
        catch (RuntimeException unreadable)
        {
            return true;
        }
    }

    private void installSpec(ReferenceType rt, BreakpointSpec spec)
    {
        for (Method m : rt.methodsByName(spec.methodName))
        {
            if (!m.signature().equals(spec.methodDesc))
            {
                continue;
            }
            try
            {
                Location loc = m.locationOfCodeIndex(spec.pc);
                if (loc == null)
                {
                    continue;
                }
                BreakpointRequest req = vm.eventRequestManager().createBreakpointRequest(loc);
                req.setSuspendPolicy(suspendAll ? EventRequest.SUSPEND_ALL : EventRequest.SUSPEND_EVENT_THREAD);
                req.enable();
                installed.add(req);
            }
            catch (Exception ignored)
            {
            }
        }
    }

    /** Resumes the target from a breakpoint. */
    public synchronized void resume()
    {
        ThreadReference t = pausedThread;
        pausedThread = null;
        listener.onResumed();
        try
        {
            if (pausedAll || t == null)
            {
                vm.resume();
            }
            else
            {
                t.resume();
            }
        }
        catch (VMDisconnectedException ignored)
        {
        }
    }

    /** Ends the session: disconnects JDI and stops the event pump. */
    public void dispose()
    {
        running = false;
        try
        {
            vm.dispose();
        }
        catch (Exception ignored)
        {
        }
        pump.interrupt();
    }

    /**
     * Reads the paused thread's call stack.
     *
     * @return the frames, top first; empty when not paused or the stack cannot be read
     */
    public List<DebugFrame> frames()
    {
        List<DebugFrame> out = new ArrayList<>();
        ThreadReference t = pausedThread;
        if (t == null)
        {
            return out;
        }
        try
        {
            List<StackFrame> frames = t.frames();
            for (int i = 0; i < frames.size(); i++)
            {
                DebugLocation loc = toLocation(frames.get(i).location());
                out.add(new DebugFrame(i, loc, frameDisplay(loc)));
            }
        }
        catch (Exception ignored)
        {
        }
        return out;
    }

    /**
     * Reads the variables visible in a frame of the paused thread, falling back to numbered arguments when the method has no local variable table.
     *
     * @param frameIndex the frame's depth, 0 for the top
     * @return this, then the locals or arguments; empty when not paused or the frame cannot be read
     */
    public List<DebugVariable> variables(int frameIndex)
    {
        List<DebugVariable> out = new ArrayList<>();
        ThreadReference t = pausedThread;
        if (t == null)
        {
            return out;
        }
        try
        {
            StackFrame frame = t.frame(frameIndex);
            ObjectReference self = frame.thisObject();
            if (self != null)
            {
                out.add(new DebugVariable("this", self.referenceType().signature(), label(self), true, register(self), false, 0));
            }
            try
            {
                for (LocalVariable lv : frame.visibleVariables())
                {
                    out.add(toVar(lv.name(), lv.signature(), frame.getValue(lv)));
                }
            }
            catch (AbsentInformationException noLvt)
            {
                List<Value> args = frame.getArgumentValues();
                for (int i = 0; i < args.size(); i++)
                {
                    out.add(toVar("arg" + i, "", args.get(i)));
                }
            }
        }
        catch (Exception ignored)
        {
        }
        return out;
    }

    private void pumpLoop()
    {
        try
        {
            EventQueue queue = vm.eventQueue();
            while (running)
            {
                EventSet set = queue.remove();
                boolean resumeAfter = true;
                for (Event event : set)
                {
                    if (event instanceof BreakpointEvent)
                    {
                        handlePause(((BreakpointEvent) event).thread(), set.suspendPolicy());
                        resumeAfter = false;
                    }
                    else if (event instanceof ClassPrepareEvent)
                    {
                        ReferenceType prepared = ((ClassPrepareEvent) event).referenceType();
                        if (installPending(prepared))
                        {
                            listener.onClassPrepared(prepared.name());
                        }
                    }
                    else if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent)
                    {
                        running = false;
                        resumeAfter = false;
                        listener.onDisconnected();
                    }
                }
                if (resumeAfter && running)
                {
                    set.resume();
                }
            }
        }
        catch (VMDisconnectedException e)
        {
            if (running)
            {
                running = false;
                listener.onDisconnected();
            }
        }
        catch (InterruptedException ignored)
        {
        }
    }

    private void handlePause(ThreadReference thread, int suspendPolicy)
    {
        this.pausedThread = thread;
        this.pausedAll = suspendPolicy == EventRequest.SUSPEND_ALL;
        refHandles.clear();
        if (pausedAll)
        {
            resumeAgentThreads();
        }
        listener.onPaused(topLocation(thread), frames());
    }

    private void resumeAgentThreads()
    {
        if (agentThreadPrefix == null)
        {
            return;
        }
        try
        {
            for (ThreadReference t : vm.allThreads())
            {
                String name = t.name();
                if (name != null && name.startsWith(agentThreadPrefix))
                {
                    while (t.suspendCount() > 0)
                    {
                        t.resume();
                    }
                }
            }
        }
        catch (Exception ignored)
        {
        }
    }

    private synchronized boolean installPending(ReferenceType rt)
    {
        boolean hadBreakpoint = false;
        for (BreakpointSpec spec : breakpoints)
        {
            if (spec.className.equals(rt.name()))
            {
                installSpec(rt, spec);
                hadBreakpoint = true;
            }
        }
        return hadBreakpoint;
    }

    /**
     * Reports whether the target supports HotSwap class redefinition.
     *
     * @return true when the VM can redefine classes
     */
    public boolean canRedefineClasses()
    {
        return vm.canRedefineClasses();
    }

    /**
     * Reports whether a class is loaded in the target.
     *
     * @param className the class's binary name, with dots
     * @return true when at least one loaded type has the name
     */
    public boolean isClassLoaded(String className)
    {
        return !vm.classesByName(className).isEmpty();
    }

    /**
     * Redefines every loaded type with a name by HotSwap and reinstalls its breakpoints; used to install a synthetic local variable table.
     *
     * @param className the class's binary name, with dots
     * @param bytes the new class file bytes
     * @return true on success; false when the VM cannot redefine, the class is not loaded, or JDI rejects the bytes
     */
    public synchronized boolean redefineClasses(String className, byte[] bytes)
    {
        if (bytes == null || !vm.canRedefineClasses())
        {
            return false;
        }
        List<ReferenceType> types = vm.classesByName(className);
        if (types.isEmpty())
        {
            return false;
        }
        try
        {
            Map<ReferenceType, byte[]> redefs = new HashMap<>();
            for (ReferenceType rt : types)
            {
                redefs.put(rt, bytes);
            }
            vm.redefineClasses(redefs);
            reinstallBreakpoints(className, types);
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private void reinstallBreakpoints(String className, List<ReferenceType> types)
    {
        List<BreakpointRequest> stale = new ArrayList<>();
        for (BreakpointRequest req : installed)
        {
            boolean forClass;
            try
            {
                forClass = req.location().declaringType().name().equals(className);
            }
            catch (Exception obsolete)
            {
                forClass = true;
            }
            if (forClass)
            {
                stale.add(req);
            }
        }
        if (!stale.isEmpty())
        {
            try
            {
                vm.eventRequestManager().deleteEventRequests(stale);
            }
            catch (Exception ignored)
            {
            }
            installed.removeAll(stale);
        }
        for (ReferenceType rt : types)
        {
            for (BreakpointSpec spec : breakpoints)
            {
                if (spec.className.equals(className))
                {
                    installSpec(rt, spec);
                }
            }
        }
    }

    private DebugLocation topLocation(ThreadReference t)
    {
        try
        {
            return toLocation(t.frame(0).location());
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static DebugLocation toLocation(Location loc)
    {
        Method m = loc.method();
        int line;
        try
        {
            line = loc.lineNumber();
        }
        catch (Exception e)
        {
            line = -1;
        }
        return new DebugLocation(loc.declaringType().name(), m.name(), m.signature(), loc.codeIndex(), line);
    }

    private static String frameDisplay(DebugLocation loc)
    {
        String at = loc.getLineNumber() > 0 ? " : " + loc.getLineNumber() : "";
        return loc.getClassName() + "." + loc.getMethodName() + "()" + at;
    }

    private DebugVariable toVar(String name, String signature, Value v)
    {
        boolean ref = v instanceof ObjectReference && !(v instanceof StringReference);
        long handle = ref ? register((ObjectReference) v) : 0;
        boolean array = false;
        int arrayLength = 0;
        if (v instanceof ArrayReference && !"char[]".equals(((ArrayReference) v).referenceType().name()))
        {
            array = true;
            arrayLength = ((ArrayReference) v).length();
        }
        return new DebugVariable(name, signature, label(v), ref, handle, array, arrayLength);
    }

    private long register(ObjectReference o)
    {
        long id = refIds.getAndIncrement();
        refHandles.put(id, o);
        return id;
    }

    /**
     * Reads the instance fields, or the first 200 elements, of a reference handed out since the last pause.
     *
     * @param handle the reference's handle
     * @return the fields or elements; empty when the handle is unknown or cannot be read
     */
    public List<DebugVariable> objectFields(long handle)
    {
        List<DebugVariable> out = new ArrayList<>();
        ObjectReference o = refHandles.get(handle);
        if (o == null)
        {
            return out;
        }
        try
        {
            if (o instanceof ArrayReference)
            {
                return arrayElements(handle, 200);
            }
            List<Field> fields = new ArrayList<>();
            for (Field f : o.referenceType().allFields())
            {
                if (!f.isStatic())
                {
                    fields.add(f);
                }
            }
            Map<Field, Value> values = o.getValues(fields);
            for (Field f : fields)
            {
                out.add(toVar(f.name(), f.signature(), values.get(f)));
            }
        }
        catch (Exception ignored)
        {
        }
        return out;
    }

    /**
     * Reads the first elements of an array reference, labelled by index.
     *
     * @param handle the array's handle
     * @param max the cap on elements read
     * @return the elements; empty when the handle is unknown, not an array, or cannot be read
     */
    public List<DebugVariable> arrayElements(long handle, int max)
    {
        List<DebugVariable> out = new ArrayList<>();
        ObjectReference o = refHandles.get(handle);
        if (!(o instanceof ArrayReference))
        {
            return out;
        }
        try
        {
            ArrayReference arr = (ArrayReference) o;
            int n = Math.min(arr.length(), Math.max(0, max));
            if (n > 0)
            {
                List<Value> vals = arr.getValues(0, n);
                for (int i = 0; i < vals.size(); i++)
                {
                    out.add(toVar("[" + i + "]", "", vals.get(i)));
                }
            }
        }
        catch (Exception ignored)
        {
        }
        return out;
    }

    private static String label(Value v)
    {
        if (v == null)
        {
            return "null";
        }
        if (v instanceof StringReference)
        {
            return "\"" + ((StringReference) v).value() + "\"";
        }
        if (v instanceof ArrayReference)
        {
            ArrayReference arr = (ArrayReference) v;
            if ("char[]".equals(arr.referenceType().name()))
            {
                return charArrayString(arr);
            }
            return arr.referenceType().name() + " (len " + arr.length() + ")";
        }
        if (v instanceof ObjectReference)
        {
            ObjectReference o = (ObjectReference) v;
            return o.referenceType().name() + "@" + o.uniqueID();
        }
        return v.toString();
    }

    private static String charArrayString(ArrayReference arr)
    {
        int len = arr.length();
        int cap = Math.min(len, 200);
        StringBuilder sb = new StringBuilder("\"");
        if (cap > 0)
        {
            for (Value cv : arr.getValues(0, cap))
            {
                if (cv instanceof CharValue)
                {
                    sb.append(((CharValue) cv).value());
                }
            }
        }
        sb.append('"');
        if (len > cap)
        {
            sb.append(" (").append(len).append(" chars)");
        }
        return sb.toString();
    }

    private static final class BreakpointSpec
    {
        final String className;
        final String methodName;
        final String methodDesc;
        final long pc;

        BreakpointSpec(String className, String methodName, String methodDesc, long pc)
        {
            this.className = className;
            this.methodName = methodName;
            this.methodDesc = methodDesc;
            this.pc = pc;
        }

        boolean matches(String c, String n, String d, long p)
        {
            return className.equals(c) && methodName.equals(n) && methodDesc.equals(d) && pc == p;
        }
    }
}
