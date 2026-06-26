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

/**
 * App-side owner of the optional JDI debug session (the control-plane counterpart to
 * {@link com.tonic.ui.live.LiveAttachService}, which owns the instrument agent). Holds the single
 * {@link DebugSession}, marshals its off-EDT callbacks onto the EDT as EventBus events, and exposes
 * connect/disconnect, breakpoint install, resume, and paused-frame inspection. Breakpoints are session-scoped;
 * the {@link BreakpointService} re-installs them on connect and clears them on disconnect.
 */
public final class DebugManager implements DebugListener {

    private static final DebugManager INSTANCE = new DebugManager();

    /** The agent-resident hand-off slot JDI parks object sets into (see com.tonic.live.agent.DropBox). */
    private static final String DROPBOX_CLASS = "com.tonic.live.agent.DropBox";
    private static final String DROPBOX_FIELD = "BOX";

    /** Name prefix of the agent's socket thread, kept running during a freeze so it can work the frozen heap. */
    private static final String AGENT_THREAD_PREFIX = "jstudio-live-java-agent";

    private volatile DebugSession session;
    @Getter
    private volatile DebugLocation pausedLocation;

    /** Classes a synthetic LocalVariableTable injection has already been attempted for this session (once each). */
    private final Set<String> injectedLvtClasses = ConcurrentHashMap.newKeySet();

    private DebugManager() {
    }

    public static DebugManager getInstance() {
        return INSTANCE;
    }

    public boolean isConnected() {
        return session != null;
    }

    public boolean isPaused() {
        DebugSession s = session;
        return s != null && s.isPaused();
    }

    // ---- connection ---------------------------------------------------------------------------------

    /** Connects JDI to a target already serving JDWP at {@code host:port} (a JStudio-launched JVM). */
    public synchronized void connect(String host, int port) throws IOException {
        disconnect();
        session = DebugSession.attach(host, port, this, Settings.getInstance().isDebuggerSuspendAll(),
                AGENT_THREAD_PREFIX);
        BreakpointService.getInstance().reinstall();
        session.start();
        postSession(true);
    }

    /** Like {@link #connect} but retries briefly, since a freshly launched JDWP listener may not be up yet. */
    public void connectWithRetry(String host, int port) throws IOException {
        IOException last = null;
        for (int i = 0; i < 50; i++) {
            try {
                connect(host, port);
                return;
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while connecting debugger", ie);
                }
            }
        }
        throw last;
    }

    /** Late-loads the JDWP agent into an externally-attached {@code pid}, then connects JDI (with retry). */
    public void connectExternal(String pid, int port) throws Exception {
        AttachLauncher.loadJdwp(pid, port);
        connectWithRetry("127.0.0.1", port);
    }

    public synchronized void disconnect() {
        DebugSession s = session;
        if (s != null) {
            session = null;
            pausedLocation = null;
            injectedLvtClasses.clear();
            s.dispose();
            postSession(false);
        }
    }

    // ---- suspend policy -----------------------------------------------------------------------------

    public boolean isSuspendAll() {
        return Settings.getInstance().isDebuggerSuspendAll();
    }

    public void setSuspendAll(boolean suspendAll) {
        Settings.getInstance().setDebuggerSuspendAll(suspendAll);
        DebugSession s = session;
        if (s != null) {
            s.setSuspendAll(suspendAll);
        }
    }

    // ---- breakpoints / control ----------------------------------------------------------------------

    public void addBreakpoint(String className, String methodName, String methodDesc, long pc) {
        DebugSession s = session;
        if (s != null) {
            s.addBreakpoint(className, methodName, methodDesc, pc);
            if (!injectedLvtClasses.contains(className)) {
                final DebugSession session0 = s;
                Thread t = new Thread(() -> maybeInjectSyntheticLvt(session0, className), "jstudio-lvt-inject");
                t.setDaemon(true);
                t.start();
            }
        }
    }

    public void removeBreakpoint(String className, String methodName, String methodDesc, long pc) {
        DebugSession s = session;
        if (s != null) {
            s.removeBreakpoint(className, methodName, methodDesc, pc);
        }
    }

    public void resume() {
        DebugSession s = session;
        if (s != null) {
            s.resume();
        }
    }

    /**
     * Parks (via JDI) up to {@code max} live instances of {@code className} into the agent's dropbox for the
     * agent to consume. Returns the count parked, or -1 if unavailable (so callers fall back to the agent walk).
     */
    public int parkInstances(String className, int max) {
        DebugSession s = session;
        return s != null ? s.parkInstances(DROPBOX_CLASS, DROPBOX_FIELD, className, max) : -1;
    }

    /**
     * Parks (via JDI) up to {@code max} objects held by the target's thread stacks into the agent's dropbox, to
     * be used as extra scan roots. Returns the count parked, or -1 if unavailable.
     */
    public int parkStackRoots(int max) {
        DebugSession s = session;
        return s != null ? s.parkStackRoots(DROPBOX_CLASS, DROPBOX_FIELD, max) : -1;
    }

    public List<DebugFrame> frames() {
        DebugSession s = session;
        return s != null ? s.frames() : Collections.emptyList();
    }

    public List<DebugVariable> variables(int frameIndex) {
        DebugSession s = session;
        return s != null ? s.variables(frameIndex) : Collections.emptyList();
    }

    /** Fields/elements of a reference value handed out by {@link #variables} (click-to-expand). */
    public List<DebugVariable> objectFields(long refHandle) {
        DebugSession s = session;
        return s != null ? s.objectFields(refHandle) : Collections.emptyList();
    }

    /** The first {@code max} elements of an array reference (for the hover preview / element viewer). */
    public List<DebugVariable> arrayElements(long refHandle, int max) {
        DebugSession s = session;
        return s != null ? s.arrayElements(refHandle, max) : Collections.emptyList();
    }

    // ---- DebugListener (event thread) -> EventBus on the EDT -----------------------------------------

    @Override
    public void onPaused(DebugLocation location, List<DebugFrame> frames) {
        this.pausedLocation = location;
        SwingUtilities.invokeLater(() -> EventBus.getInstance().post(new DebugPausedEvent(this, location, frames)));
    }

    @Override
    public void onResumed() {
        this.pausedLocation = null;
        SwingUtilities.invokeLater(() -> EventBus.getInstance().post(new DebugResumedEvent(this)));
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(this::disconnect);
    }

    @Override
    public void onClassPrepared(String className) {
        maybeInjectSyntheticLvt(session, className);
    }

    /**
     * Injects a synthetic LocalVariableTable into a stripped class so the live debugger reports named locals
     * matching the decompilation. Best-effort and once per class per session: skips when the VM can't HotSwap,
     * the class isn't loaded, the project lacks it, or it already carries an LVT (every method), and never
     * disturbs the unchanged bytecode (only the debug attribute is added).
     */
    private void maybeInjectSyntheticLvt(DebugSession s, String className) {
        if (s == null || !s.canRedefineClasses() || !s.isClassLoaded(className)) {
            return;
        }
        if (!injectedLvtClasses.add(className)) {
            return;
        }
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            return;
        }
        ClassEntryModel entry = project.getClass(className.replace('.', '/'));
        if (entry == null) {
            return;
        }
        byte[] augmented = SyntheticLvtInjector.augment(entry.getClassFile());
        if (augmented != null) {
            s.redefineClasses(className, augmented);
        }
    }

    private void postSession(boolean connected) {
        SwingUtilities.invokeLater(() -> EventBus.getInstance().post(new DebugSessionEvent(this, connected)));
    }
}
