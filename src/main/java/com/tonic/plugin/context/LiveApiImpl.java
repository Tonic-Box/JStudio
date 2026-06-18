package com.tonic.plugin.context;

import com.tonic.live.Deadlocks;
import com.tonic.live.LiveSession;
import com.tonic.live.protocol.ContentionEdge;
import com.tonic.live.protocol.MetricsSnapshot;
import com.tonic.live.protocol.StackFrame;
import com.tonic.live.protocol.ThreadStack;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.plugin.api.LiveApi;
import com.tonic.service.ProjectService;
import com.tonic.ui.live.LiveAttachService;
import com.tonic.ui.live.LiveHeapService;
import com.tonic.ui.live.eval.ProjectClasspath;
import com.tonic.ui.live.eval.SnippetCompiler;
import com.tonic.ui.live.heap.HprofSnapshot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The plugin-facing {@link LiveApi}: wraps {@link LiveAttachService} / {@link LiveSession} (and, for eval/redefine,
 * the Scratch Pad compiler + the loaded project) and maps live-client protocol types to the public DTOs. Attach
 * state is read per call, so attach/detach is reflected immediately.
 *
 * <p>The {@link LiveSession} is borrowed from {@link LiveAttachService} and never closed here - it owns the
 * connection's lifecycle (closing it would detach the whole app) - hence the class-level {@code resource}
 * suppression.
 */
@SuppressWarnings("resource")
public class LiveApiImpl implements LiveApi {

    @Override
    public boolean isAttached() {
        LiveAttachService service = LiveAttachService.getInstance();
        return service.isAttached() && service.getSession() != null;
    }

    @Override
    public String attachInfo() {
        if (!isAttached()) {
            return "not attached";
        }
        return "attached to pid " + LiveAttachService.getInstance().getSession().getPid();
    }

    private LiveSession session() {
        if (!isAttached()) {
            throw new IllegalStateException("Not attached to a live JVM.");
        }
        return LiveAttachService.getInstance().getSession();
    }

    @Override
    public Metrics metrics() {
        try {
            MetricsSnapshot m = session().getMetrics();
            List<Gc> gc = new ArrayList<>();
            for (MetricsSnapshot.GcStat g : m.gcStats) {
                gc.add(new Gc(g.name, g.collectionCount, g.collectionTimeMs));
            }
            List<Pool> pools = new ArrayList<>();
            for (MetricsSnapshot.MemoryPool p : m.memoryPools) {
                pools.add(new Pool(p.name, p.used, p.max));
            }
            return new Metrics(m.uptimeMs, m.heapUsed, m.heapMax, m.nonHeapUsed, m.nonHeapMax,
                    m.processCpuLoad, m.systemCpuLoad, m.availableProcessors, m.threadCount, m.daemonThreadCount,
                    m.peakThreadCount, m.loadedClassCount, m.totalLoadedClassCount, gc, pools);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public List<ThreadDump> threads(int maxDepth) {
        try {
            List<ThreadDump> out = new ArrayList<>();
            for (ThreadStack ts : session().getThreadStacks(maxDepth)) {
                List<Frame> frames = new ArrayList<>();
                for (StackFrame f : ts.getFrames()) {
                    frames.add(new Frame(f.getDeclaringClass(), f.getMethod(), f.getFile(), f.getLine()));
                }
                out.add(new ThreadDump(ts.getId(), ts.getName(), ts.getStateEnum().name(), frames));
            }
            return out;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public List<Deadlock> deadlocks() {
        try {
            List<Deadlock> out = new ArrayList<>();
            for (List<ContentionEdge> cycle : Deadlocks.find(session().getContention())) {
                List<String> edges = new ArrayList<>();
                for (ContentionEdge e : cycle) {
                    edges.add(e.getThreadName() + " waits on " + e.getMonitorClass()
                            + " held by " + e.getOwnerThreadName());
                }
                out.add(new Deadlock(edges));
            }
            return out;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public List<StaticField> statics(String className) {
        try {
            List<StaticField> out = new ArrayList<>();
            for (com.tonic.live.protocol.StaticField f : session().getStatics(className.replace('.', '/'))) {
                out.add(new StaticField(f.getName(), f.getTypeDesc(), f.getValue()));
            }
            return out;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public String jfr(int seconds, int categoryMask) {
        LiveSession s = session();
        if (!s.supportsJfr()) {
            return "JFR is not available on the target JVM.";
        }
        int secs = Math.max(1, Math.min(30, seconds));
        try {
            s.startRecording("profile", categoryMask, 0);
            try {
                Thread.sleep(secs * 1000L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            return JfrSummary.summarize(s.stopRecording());
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public Instances instances(String className, int offset, int limit, boolean refresh) {
        LiveSession s = session();
        try {
            HprofSnapshot snap = refresh
                    ? LiveHeapService.get().snapshot(s) : LiveHeapService.get().ensureSnapshot(s);
            List<Long> ids = snap.instancesOf(className.replace('.', '/'));
            List<InstanceRef> page = new ArrayList<>();
            for (int i = Math.max(0, offset); i < Math.min(ids.size(), offset + limit); i++) {
                long id = ids.get(i);
                page.add(new InstanceRef(hex(id), snap.labelFor(id)));
            }
            return new Instances(className.replace('.', '/'), ids.size(), page);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public InstanceInfo instance(String id) {
        session();
        HprofSnapshot snap = LiveHeapService.get().getSnapshot();
        if (snap == null) {
            throw new IllegalStateException("No heap snapshot yet - enumerate a class with live_instances first.");
        }
        long objId = parseHex(id);
        try {
            HprofSnapshot.InstanceData data = snap.decode(objId);
            List<Field> fields = new ArrayList<>();
            for (HprofSnapshot.FieldValue f : data.fields) {
                String refId = "ref".equals(f.type) && f.refId != 0 ? hex(f.refId) : null;
                fields.add(new Field(f.name, f.type, f.display, refId));
            }
            return new InstanceInfo(hex(objId), data.className, fields);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static String hex(long id) {
        return "0x" + Long.toHexString(id);
    }

    private static long parseHex(String id) {
        String s = id.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        try {
            return Long.parseUnsignedLong(s, 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not a valid instance id: " + id);
        }
    }

    @Override
    public EvalResult eval(String code, String contextClass) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            return new EvalResult(false, "No project is loaded.");
        }
        LiveSession s = session();
        try {
            SnippetCompiler compiler = new SnippetCompiler(new ProjectClasspath(project), targetRelease(project));
            SnippetCompiler.Result result = compiler.compile(code);
            if (!result.isSuccess()) {
                return new EvalResult(false, "Compilation failed:\n" + String.join("\n", result.getMessages()));
            }
            String context = contextClass != null && !contextClass.trim().isEmpty()
                    ? contextClass.replace('/', '.') : defaultContext(project);
            return new EvalResult(true, s.eval(result.getClasses(), result.getMainBinaryName(), context));
        } catch (IOException e) {
            return new EvalResult(false, e.getMessage());
        }
    }

    @Override
    public String setStatic(String className, String field, boolean setNull, String value) {
        try {
            return session().setStatic(className.replace('.', '/'), field, setNull, value);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public String invokeStatic(String className, String method, String descriptor, List<String> args) {
        try {
            return session().invokeStatic(className.replace('.', '/'), method, descriptor,
                    args != null ? args : Collections.emptyList());
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public void redefineClass(String className) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            throw new IllegalStateException("No project is loaded.");
        }
        String internal = className.replace('.', '/');
        ClassEntryModel entry = project.findClassByName(internal);
        if (entry == null) {
            throw new IllegalArgumentException("Class not in the loaded project: " + className);
        }
        try {
            session().redefineClass(internal, entry.getClassFile().write());
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static int targetRelease(ProjectModel project) {
        int maxMajor = 0;
        for (ClassEntryModel entry : project.getAllClasses()) {
            int major = entry.getClassFile().getMajorVersion();
            if (major > maxMajor) {
                maxMajor = major;
            }
        }
        return SnippetCompiler.releaseForMajorVersion(maxMajor);
    }

    private static String defaultContext(ProjectModel project) {
        for (ClassEntryModel entry : project.getAllClasses()) {
            return entry.getClassName().replace('/', '.');
        }
        return "java.lang.Object";
    }
}
