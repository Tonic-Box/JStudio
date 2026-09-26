package com.tonic.ui.debug;

import com.tonic.event.EventBus;
import com.tonic.event.events.DebugPausedEvent;
import com.tonic.event.events.DebugResumedEvent;
import com.tonic.event.events.DebugSessionEvent;
import com.tonic.live.AttachLauncher;
import com.tonic.live.debug.DebugFrame;
import com.tonic.live.debug.DebugListener;
import com.tonic.live.debug.DebugLocation;
import com.tonic.live.debug.DebugSession;
import com.tonic.live.debug.DebugVariable;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.service.ProjectService;
import com.tonic.service.SyntheticLvtInjector;
import com.tonic.util.Settings;
import lombok.Getter;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** The owner of the single optional JDI debug session; it relays the session's callbacks onto the EDT as EventBus events. */
public final class DebugManager implements DebugListener
{

    private static final DebugManager INSTANCE = new DebugManager();

    private static final String DROPBOX_CLASS = "com.tonic.live.agent.DropBox";
    private static final String DROPBOX_FIELD = "BOX";

    private static final String AGENT_THREAD_PREFIX = "jstudio-live-java-agent";

    private volatile DebugSession session;
    @Getter
    private volatile DebugLocation pausedLocation;

    private final Set<String> injectedLvtClasses = ConcurrentHashMap.newKeySet();

    private DebugManager()
    {
    }

    /** @return the shared manager */
    public static DebugManager getInstance()
    {
        return INSTANCE;
    }

    /**
     * Tells whether a debug session is open.
     *
     * @return true if connected
     */
    public boolean isConnected()
    {
        return session != null;
    }

    /**
     * Tells whether the target is suspended at a breakpoint.
     *
     * @return true if connected and paused
     */
    public boolean isPaused()
    {
        DebugSession s = session;
        return s != null && s.isPaused();
    }

    /**
     * Drops any current session, connects JDI to a target already serving JDWP, and installs the registered breakpoints.
     *
     * @param host the target's host
     * @param port the target's JDWP port
     * @throws IOException if the connection fails
     */
    public synchronized void connect(String host, int port) throws IOException
    {
        disconnect();
        session = DebugSession.attach(host, port, this, Settings.getInstance().isDebuggerSuspendAll(), AGENT_THREAD_PREFIX);
        BreakpointService.getInstance().reinstall();
        session.start();
        postSession(true);
    }

    /**
     * Connects like connect, retrying for about ten seconds while a freshly launched JDWP listener comes up.
     *
     * @param host the target's host
     * @param port the target's JDWP port
     * @throws IOException if every attempt fails, or if interrupted while waiting
     */
    public void connectWithRetry(String host, int port) throws IOException
    {
        IOException last = null;
        for (int i = 0; i < 50; i++)
        {
            try
            {
                connect(host, port);
                return;
            }
            catch (IOException e)
            {
                last = e;
                try
                {
                    Thread.sleep(200);
                }
                catch (InterruptedException ie)
                {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while connecting debugger", ie);
                }
            }
        }
        throw last;
    }

    /**
     * Loads the JDWP agent into an already running JVM, then connects JDI with retry.
     *
     * @param pid the target's process id
     * @param port the port for the JDWP agent to listen on
     * @throws Exception if loading the agent or connecting fails
     */
    public void connectExternal(String pid, int port) throws Exception
    {
        AttachLauncher.loadJdwp(pid, port);
        connectWithRetry("127.0.0.1", port);
    }

    /** Disposes the session and posts a disconnected event; does nothing when not connected. */
    public synchronized void disconnect()
    {
        DebugSession s = session;
        if (s != null)
        {
            session = null;
            pausedLocation = null;
            injectedLvtClasses.clear();
            s.dispose();
            postSession(false);
        }
    }

    /**
     * Tells whether a breakpoint hit suspends every thread rather than just the hitting one.
     *
     * @return the saved suspend-all setting
     */
    public boolean isSuspendAll()
    {
        return Settings.getInstance().isDebuggerSuspendAll();
    }

    /**
     * Saves the suspend-all setting and applies it to the open session, if any.
     *
     * @param suspendAll true to suspend every thread on a breakpoint hit
     */
    public void setSuspendAll(boolean suspendAll)
    {
        Settings.getInstance().setDebuggerSuspendAll(suspendAll);
        DebugSession s = session;
        if (s != null)
        {
            s.setSuspendAll(suspendAll);
        }
    }

    /**
     * Installs a breakpoint in the open session and, once per class, injects a synthetic local variable table in the background; does nothing when not connected.
     *
     * @param className the declaring class, dotted
     * @param methodName the method's name
     * @param methodDesc the method's JVM descriptor
     * @param pc the bytecode offset within the method
     */
    public void addBreakpoint(String className, String methodName, String methodDesc, long pc)
    {
        DebugSession s = session;
        if (s != null)
        {
            s.addBreakpoint(className, methodName, methodDesc, pc);
            if (!injectedLvtClasses.contains(className))
            {
                final DebugSession session0 = s;
                Thread t = new Thread(() -> maybeInjectSyntheticLvt(session0, className), "jstudio-lvt-inject");
                t.setDaemon(true);
                t.start();
            }
        }
    }

    /**
     * Removes a breakpoint from the open session; does nothing when not connected.
     *
     * @param className the declaring class, dotted
     * @param methodName the method's name
     * @param methodDesc the method's JVM descriptor
     * @param pc the bytecode offset within the method
     */
    public void removeBreakpoint(String className, String methodName, String methodDesc, long pc)
    {
        DebugSession s = session;
        if (s != null)
        {
            s.removeBreakpoint(className, methodName, methodDesc, pc);
        }
    }

    /** Resumes the paused target; does nothing when not connected. */
    public void resume()
    {
        DebugSession s = session;
        if (s != null)
        {
            s.resume();
        }
    }

    /**
     * Parks live instances of a class into the agent's dropbox through JDI for the agent to consume.
     *
     * @param className the class, dotted
     * @param max the most instances to park
     * @return the number parked, or -1 if not connected or parking failed
     */
    public int parkInstances(String className, int max)
    {
        DebugSession s = session;
        return s != null ? s.parkInstances(DROPBOX_CLASS, DROPBOX_FIELD, className, max) : -1;
    }

    /**
     * Parks objects held by the target's thread stacks into the agent's dropbox through JDI, for use as extra scan roots.
     *
     * @param max the most objects to park
     * @return the number parked, or -1 if not connected or parking failed
     */
    public int parkStackRoots(int max)
    {
        DebugSession s = session;
        return s != null ? s.parkStackRoots(DROPBOX_CLASS, DROPBOX_FIELD, max) : -1;
    }

    /**
     * Lists the paused thread's call stack.
     *
     * @return the frames, or an empty list when not connected
     */
    public List<DebugFrame> frames()
    {
        DebugSession s = session;
        return s != null ? s.frames() : Collections.emptyList();
    }

    /**
     * Lists the visible variables of one paused frame.
     *
     * @param frameIndex the frame's index in the call stack
     * @return the variables, or an empty list when not connected
     */
    public List<DebugVariable> variables(int frameIndex)
    {
        DebugSession s = session;
        return s != null ? s.variables(frameIndex) : Collections.emptyList();
    }

    /**
     * Lists the fields or elements of a reference value from an earlier variables call.
     *
     * @param refHandle the value's reference handle
     * @return the fields or elements, or an empty list when not connected
     */
    public List<DebugVariable> objectFields(long refHandle)
    {
        DebugSession s = session;
        return s != null ? s.objectFields(refHandle) : Collections.emptyList();
    }

    /**
     * Lists the leading elements of an array reference.
     *
     * @param refHandle the array's reference handle
     * @param max the most elements to return
     * @return the elements, or an empty list when not connected
     */
    public List<DebugVariable> arrayElements(long refHandle, int max)
    {
        DebugSession s = session;
        return s != null ? s.arrayElements(refHandle, max) : Collections.emptyList();
    }

    @Override
    public void onPaused(DebugLocation location, List<DebugFrame> frames)
    {
        this.pausedLocation = location;
        SwingUtilities.invokeLater(() -> EventBus.getInstance().post(new DebugPausedEvent(this, location, frames)));
    }

    @Override
    public void onResumed()
    {
        this.pausedLocation = null;
        SwingUtilities.invokeLater(() -> EventBus.getInstance().post(new DebugResumedEvent(this)));
    }

    @Override
    public void onDisconnected()
    {
        SwingUtilities.invokeLater(this::disconnect);
    }

    @Override
    public void onClassPrepared(String className)
    {
        maybeInjectSyntheticLvt(session, className);
    }

    private void maybeInjectSyntheticLvt(DebugSession s, String className)
    {
        if (s == null || !s.canRedefineClasses() || !s.isClassLoaded(className))
        {
            return;
        }
        if (!injectedLvtClasses.add(className))
        {
            return;
        }
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null)
        {
            return;
        }
        ClassEntryModel entry = project.getClass(className.replace('.', '/'));
        if (entry == null)
        {
            return;
        }
        byte[] augmented = SyntheticLvtInjector.augment(entry.getClassFile());
        if (augmented != null)
        {
            s.redefineClasses(className, augmented);
        }
    }

    private void postSession(boolean connected)
    {
        SwingUtilities.invokeLater(() -> EventBus.getInstance().post(new DebugSessionEvent(this, connected)));
    }
}
