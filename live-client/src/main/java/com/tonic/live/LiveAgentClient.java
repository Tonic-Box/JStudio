package com.tonic.live;

import com.tonic.live.protocol.AgentInfo;
import com.tonic.live.protocol.ContentionEdge;
import com.tonic.live.protocol.LiveEvent;
import com.tonic.live.protocol.LiveField;
import com.tonic.live.protocol.LiveInstance;
import com.tonic.live.protocol.MetricsSnapshot;
import com.tonic.live.protocol.LiveProtocol;
import com.tonic.live.protocol.LoadedClass;
import com.tonic.live.protocol.ScanLocation;
import com.tonic.live.protocol.ScanPage;
import com.tonic.live.protocol.StackFrame;
import com.tonic.live.protocol.StaticField;
import com.tonic.live.protocol.StaticMethod;
import com.tonic.live.protocol.ThreadInfo;
import com.tonic.live.protocol.ThreadStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Client side of the JStudio Live wire protocol against the pure-Java agent. A dedicated reader thread
 * demultiplexes the single TCP stream into <b>responses</b> (handed to the in-flight request) and
 * asynchronous <b>events</b> (runtime class loads / VM death, dispatched to registered listeners).
 * Requests are serialized - one in flight at a time.
 */
public final class LiveAgentClient implements Closeable {

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final Thread reader;
    /** Unbounded so the reader never blocks handing off a response; requests are serialized (one in flight). */
    private final BlockingQueue<byte[]> responses = new LinkedBlockingQueue<>();
    private final CopyOnWriteArrayList<Consumer<LiveEvent>> listeners = new CopyOnWriteArrayList<>();
    /** Events run here, never on the reader thread, so a slow/blocking listener can't wedge the protocol stream. */
    private final ExecutorService eventDispatch = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "live-agent-events");
        t.setDaemon(true);
        return t;
    });
    /** Pushed into {@link #responses} on disconnect/close so an in-flight request fails instead of hanging forever. */
    private static final byte[] POISON = new byte[0];
    /** Last-resort backstop against a wedged-but-connected agent; a real disconnect unblocks immediately. */
    private static final long REQUEST_TIMEOUT_MS = 300_000;
    private volatile boolean closed;

    private LiveAgentClient(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
        this.reader = new Thread(this::readLoop, "live-agent-reader");
        this.reader.setDaemon(true);
        this.reader.start();
    }

    public static LiveAgentClient connect(String host, int port, int timeoutMillis) throws IOException {
        final long deadline = System.currentTimeMillis() + timeoutMillis;
        IOException last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(host, port), 1000);
                return new LiveAgentClient(s);
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while connecting", ie);
                }
            }
        }
        throw new IOException("could not connect to live agent at " + host + ":" + port, last);
    }

    /** Register an event listener; multiple may be registered. */
    public void addEventListener(Consumer<LiveEvent> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeEventListener(Consumer<LiveEvent> listener) {
        listeners.remove(listener);
    }

    public void setEventListener(Consumer<LiveEvent> listener) {
        listeners.clear();
        addEventListener(listener);
    }

    private void emit(LiveEvent event) {
        for (Consumer<LiveEvent> l : listeners) {
            try {
                l.accept(event);
            } catch (RuntimeException ignored) {
            }
        }
    }

    // ---- commands ---------------------------------------------------------------------------------

    public AgentInfo hello() throws IOException {
        DataInputStream r = request(new byte[]{(byte) LiveProtocol.MSG_HELLO});
        skipType(r, LiveProtocol.MSG_HELLO);
        return new AgentInfo(r.readInt(), r.readInt(), r.readInt());
    }

    public List<LoadedClass> listClasses() throws IOException {
        DataInputStream r = request(new byte[]{(byte) LiveProtocol.MSG_LIST_CLASSES});
        skipType(r, LiveProtocol.MSG_LIST_CLASSES);
        int count = r.readInt();
        List<LoadedClass> classes = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            classes.add(new LoadedClass(readString(r), r.readUnsignedShort()));
        }
        return classes;
    }

    public byte[] getClassBytes(String internalName) throws IOException {
        DataInputStream r = request(payload(LiveProtocol.MSG_GET_CLASS_BYTES, b -> writeString(b, internalName)));
        skipType(r, LiveProtocol.MSG_GET_CLASS_BYTES);
        byte[] bytes = new byte[r.readInt()];
        r.readFully(bytes);
        return bytes;
    }

    public List<ThreadInfo> getThreads() throws IOException {
        DataInputStream r = request(new byte[]{(byte) LiveProtocol.MSG_GET_THREADS});
        skipType(r, LiveProtocol.MSG_GET_THREADS);
        int count = r.readInt();
        List<ThreadInfo> threads = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            threads.add(new ThreadInfo(r.readLong(), readString(r), r.readInt()));
        }
        return threads;
    }

    /** Live method-body redefinition: replace {@code internalName}'s bytecode in the target. */
    public void redefineClass(String internalName, byte[] classBytes) throws IOException {
        DataInputStream r = request(payload(LiveProtocol.MSG_REDEFINE_CLASS, b -> {
            writeString(b, internalName);
            b.writeInt(classBytes.length);
            b.write(classBytes);
        }));
        skipType(r, LiveProtocol.MSG_REDEFINE_CLASS);
    }

    /** Arm/disarm streaming of runtime class loads as {@link LiveEvent.Kind#CLASS_LOADED} events. */
    public void setCaptureLoads(boolean on) throws IOException {
        DataInputStream r = request(payload(LiveProtocol.MSG_SET_CAPTURE_LOADS, b -> b.writeByte(on ? 1 : 0)));
        skipType(r, LiveProtocol.MSG_SET_CAPTURE_LOADS);
    }

    /** Triggers a HotSpot heap dump in the target and returns the local file path of the .hprof. */
    public String heapDump() throws IOException {
        DataInputStream r = request(new byte[]{(byte) LiveProtocol.MSG_HEAP_DUMP});
        skipType(r, LiveProtocol.MSG_HEAP_DUMP);
        return readString(r);
    }

    /** Starts a JFR recording (base {@code profile} plus category bits; {@code maxSizeMb} 0 = unbounded). */
    public void jfrStart(String profile, int categoryMask, int maxSizeMb) throws IOException {
        DataInputStream r = request(payload(LiveProtocol.MSG_JFR_START, b -> {
            writeString(b, profile);
            b.writeInt(categoryMask);
            b.writeInt(maxSizeMb);
        }));
        skipType(r, LiveProtocol.MSG_JFR_START);
    }

    /** Stops the active JFR recording, dumping it; returns the local {@code .jfr} path. */
    public String jfrStop() throws IOException {
        DataInputStream r = request(new byte[]{(byte) LiveProtocol.MSG_JFR_STOP});
        skipType(r, LiveProtocol.MSG_JFR_STOP);
        return readString(r);
    }

    /** Dumps the in-progress recording's buffer without stopping it; returns the local {@code .jfr} path. */
    public String jfrSnapshot() throws IOException {
        DataInputStream r = request(new byte[]{(byte) LiveProtocol.MSG_JFR_SNAPSHOT});
        skipType(r, LiveProtocol.MSG_JFR_SNAPSHOT);
        return readString(r);
    }

    /** Reads the live static fields of a class (name, type descriptor, current value, edit kind). */
    public List<StaticField> getStatics(String internalName) throws IOException {
        DataInputStream r = request(payload(LiveProtocol.MSG_GET_STATICS, b -> writeString(b, internalName)));
        skipType(r, LiveProtocol.MSG_GET_STATICS);
        int count = r.readInt();
        List<StaticField> fields = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            fields.add(new StaticField(readString(r), readString(r), readString(r), r.readUnsignedByte()));
        }
        return fields;
    }

    /** Sets a static field's value (or to null); returns the field's value as re-read after the change. */
    public String setStatic(String className, String field, boolean setNull, String value) throws IOException {
        DataInputStream r = request(payload(LiveProtocol.MSG_SET_STATIC, b -> {
            writeString(b, className);
            writeString(b, field);
            b.writeByte(setNull ? 1 : 0);
            writeString(b, value == null ? "" : value);
        }));
        skipType(r, LiveProtocol.MSG_SET_STATIC);
        return readString(r);
    }

    // ---- value scanner ----------------------------------------------------------------------------

    public ScanPage scanFirst(int valueType, int scanKind, String value, String value2, String pkgFilter,
                              boolean userClassesOnly, int maxVisited, int maxMatches, int limit) throws IOException {
        DataInputStream r = request(payload(LiveProtocol.MSG_SCAN_FIRST, b -> {
            b.writeByte(valueType);
            b.writeByte(scanKind);
            writeString(b, value == null ? "" : value);
            writeString(b, value2 == null ? "" : value2);
            writeString(b, pkgFilter == null ? "" : pkgFilter);
            b.writeInt(maxVisited);
            b.writeInt(maxMatches);
            b.writeInt(limit);
            b.writeBoolean(userClassesOnly);
        }));
        skipType(r, LiveProtocol.MSG_SCAN_FIRST);
        return readPage(r);
    }

    public ScanPage scanNext(int comparator, String value, String value2, int offset, int limit) throws IOException {
        DataInputStream r = request(payload(LiveProtocol.MSG_SCAN_NEXT, b -> {
            b.writeByte(comparator);
            writeString(b, value == null ? "" : value);
            writeString(b, value2 == null ? "" : value2);
            b.writeInt(offset);
            b.writeInt(limit);
        }));
        skipType(r, LiveProtocol.MSG_SCAN_NEXT);
        return readPage(r);
    }

    public ScanPage scanRead(boolean pinnedOnly, int offset, int limit) throws IOException {
        DataInputStream r = request(payload(LiveProtocol.MSG_SCAN_READ, b -> {
            b.writeByte(pinnedOnly ? 1 : 0);
            b.writeInt(offset);
            b.writeInt(limit);
        }));
        skipType(r, LiveProtocol.MSG_SCAN_READ);
        return readPage(r);
    }

    public String scanWrite(long id, boolean isNull, String value) throws IOException {
        DataInputStream r = request(payload(LiveProtocol.MSG_SCAN_WRITE, b -> {
            b.writeLong(id);
            b.writeByte(isNull ? 1 : 0);
            writeString(b, value == null ? "" : value);
        }));
        skipType(r, LiveProtocol.MSG_SCAN_WRITE);
        return readString(r);
    }

    public List<LiveInstance> listInstances(String className, int maxInstances, int maxVisited) throws IOException {
        DataInputStream r = request(payload(LiveProtocol.MSG_LIST_INSTANCES, b -> {
            writeString(b, className == null ? "" : className);
            b.writeInt(maxInstances);
            b.writeInt(maxVisited);
        }));
        skipType(r, LiveProtocol.MSG_LIST_INSTANCES);
        int count = r.readInt();
        List<LiveInstance> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long id = r.readLong();
            out.add(new LiveInstance(id, readString(r)));
        }
        return out;
    }

    public List<LiveField> instanceFields(long handleId) throws IOException {
        DataInputStream r = request(payload(LiveProtocol.MSG_INSTANCE_FIELDS, b -> b.writeLong(handleId)));
        skipType(r, LiveProtocol.MSG_INSTANCE_FIELDS);
        int count = r.readInt();
        List<LiveField> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = readString(r);
            String typeDesc = readString(r);
            String display = readString(r);
            long refId = r.readLong();
            boolean editable = r.readUnsignedByte() != 0;
            out.add(new LiveField(name, typeDesc, display, refId, editable));
        }
        return out;
    }

    public String setInstanceField(long handleId, String field, boolean isNull, String value) throws IOException {
        DataInputStream r = request(payload(LiveProtocol.MSG_SET_INSTANCE_FIELD, b -> {
            b.writeLong(handleId);
            writeString(b, field);
            b.writeByte(isNull ? 1 : 0);
            writeString(b, value == null ? "" : value);
        }));
        skipType(r, LiveProtocol.MSG_SET_INSTANCE_FIELD);
        return readString(r);
    }

    public void scanFreeze(long id, boolean on, String value) throws IOException {
        DataInputStream r = request(payload(LiveProtocol.MSG_SCAN_FREEZE, b -> {
            b.writeLong(id);
            b.writeByte(on ? 1 : 0);
            writeString(b, value == null ? "" : value);
        }));
        skipType(r, LiveProtocol.MSG_SCAN_FREEZE);
        r.readUnsignedByte();
    }

    public void scanPin(long id, boolean on) throws IOException {
        DataInputStream r = request(payload(LiveProtocol.MSG_SCAN_PIN, b -> {
            b.writeLong(id);
            b.writeByte(on ? 1 : 0);
        }));
        skipType(r, LiveProtocol.MSG_SCAN_PIN);
        r.readUnsignedByte();
    }

    public void scanClear() throws IOException {
        DataInputStream r = request(payload(LiveProtocol.MSG_SCAN_CLEAR, b -> {
        }));
        skipType(r, LiveProtocol.MSG_SCAN_CLEAR);
        r.readUnsignedByte();
    }

    private ScanPage readPage(DataInputStream r) throws IOException {
        int total = r.readInt();
        boolean truncated = r.readUnsignedByte() != 0;
        int returned = r.readInt();
        List<ScanLocation> locations = new ArrayList<>(Math.max(0, returned));
        for (int i = 0; i < returned; i++) {
            locations.add(new ScanLocation(r.readLong(), readString(r), readString(r), readString(r),
                    readString(r), readString(r), readString(r), r.readUnsignedByte()));
        }
        return new ScanPage(total, truncated, locations);
    }

    /** Lists the static methods of a class (name + JVM descriptor). */
    public List<StaticMethod> listStaticMethods(String internalName) throws IOException {
        DataInputStream r = request(payload(LiveProtocol.MSG_LIST_STATIC_METHODS, b -> writeString(b, internalName)));
        skipType(r, LiveProtocol.MSG_LIST_STATIC_METHODS);
        int count = r.readInt();
        List<StaticMethod> methods = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            methods.add(new StaticMethod(readString(r), readString(r)));
        }
        return methods;
    }

    /**
     * Defines a freshly-compiled snippet (its wrapper class plus any anonymous/local classes) in the target,
     * in a throwaway child of {@code contextClass}'s classloader, invokes the wrapper's {@code static Object
     * run()}, and returns the captured output + result/exception.
     *
     * @param classes        all compiled classes, by binary name (e.g. {@code jstudio.scratch.Scratch_3})
     * @param mainBinaryName the wrapper class to invoke {@code run()} on
     * @param contextClass   internal name of the class whose loader scopes runtime visibility (may be empty)
     */
    public String eval(Map<String, byte[]> classes, String mainBinaryName, String contextClass) throws IOException {
        DataInputStream r = request(payload(LiveProtocol.MSG_EVAL, b -> {
            b.writeInt(classes.size());
            for (Map.Entry<String, byte[]> e : classes.entrySet()) {
                writeString(b, e.getKey());
                b.writeInt(e.getValue().length);
                b.write(e.getValue());
            }
            writeString(b, mainBinaryName);
            writeString(b, contextClass);
        }));
        skipType(r, LiveProtocol.MSG_EVAL);
        return readString(r);
    }

    /** Invokes a static method (args marshalled from strings); returns the formatted result. */
    public String invokeStatic(String className, String name, String desc, List<String> args) throws IOException {
        DataInputStream r = request(payload(LiveProtocol.MSG_INVOKE_STATIC, b -> {
            writeString(b, className);
            writeString(b, name);
            writeString(b, desc);
            b.writeInt(args.size());
            for (String a : args) {
                writeString(b, a == null ? "null" : a);
            }
        }));
        skipType(r, LiveProtocol.MSG_INVOKE_STATIC);
        return readString(r);
    }

    /** Reads a snapshot of the target JVM's runtime metrics (memory, GC, CPU, threads, classes). */
    public MetricsSnapshot getMetrics() throws IOException {
        DataInputStream r = request(new byte[]{(byte) LiveProtocol.MSG_GET_METRICS});
        skipType(r, LiveProtocol.MSG_GET_METRICS);
        long uptime = r.readLong();
        long heapUsed = r.readLong(), heapCommitted = r.readLong(), heapMax = r.readLong();
        long nhUsed = r.readLong(), nhCommitted = r.readLong(), nhMax = r.readLong();
        double procCpu = r.readDouble(), sysCpu = r.readDouble();
        int procs = r.readInt();
        int threads = r.readInt(), daemon = r.readInt(), peak = r.readInt();
        long totalStarted = r.readLong();
        int loaded = r.readInt();
        long totalLoaded = r.readLong(), unloaded = r.readLong();

        int poolCount = r.readInt();
        List<MetricsSnapshot.MemoryPool> pools = new ArrayList<>(Math.max(0, poolCount));
        for (int i = 0; i < poolCount; i++) {
            pools.add(new MetricsSnapshot.MemoryPool(readString(r), r.readLong(), r.readLong(), r.readLong()));
        }
        int gcCount = r.readInt();
        List<MetricsSnapshot.GcStat> gcs = new ArrayList<>(Math.max(0, gcCount));
        for (int i = 0; i < gcCount; i++) {
            gcs.add(new MetricsSnapshot.GcStat(readString(r), r.readLong(), r.readLong()));
        }
        return new MetricsSnapshot(uptime, heapUsed, heapCommitted, heapMax, nhUsed, nhCommitted, nhMax,
                procCpu, sysCpu, procs, threads, daemon, peak, totalStarted, loaded, totalLoaded, unloaded, pools, gcs);
    }

    /** Snapshots all threads with their current stacks (up to {@code maxDepth} frames each). */
    public List<ThreadStack> getThreadStacks(int maxDepth) throws IOException {
        DataInputStream r = request(payload(LiveProtocol.MSG_GET_THREAD_STACKS, b -> b.writeInt(maxDepth)));
        skipType(r, LiveProtocol.MSG_GET_THREAD_STACKS);
        int count = r.readInt();
        List<ThreadStack> threads = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            long id = r.readLong();
            String name = readString(r);
            int state = r.readInt();
            int frameCount = r.readInt();
            List<StackFrame> frames = new ArrayList<>(Math.max(0, frameCount));
            for (int j = 0; j < frameCount; j++) {
                frames.add(new StackFrame(readString(r), readString(r), readString(r), r.readInt()));
            }
            threads.add(new ThreadStack(id, name, state, frames));
        }
        return threads;
    }

    /** Snapshot the wait-for graph (blocked thread -> monitor owner) for deadlock detection. */
    public List<ContentionEdge> getContention() throws IOException {
        DataInputStream r = request(new byte[]{(byte) LiveProtocol.MSG_GET_CONTENTION});
        skipType(r, LiveProtocol.MSG_GET_CONTENTION);
        int count = r.readInt();
        List<ContentionEdge> edges = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            edges.add(new ContentionEdge(r.readLong(), readString(r), readString(r), r.readLong(), readString(r)));
        }
        return edges;
    }

    // ---- framing / reader -------------------------------------------------------------------------

    private void readLoop() {
        try {
            while (!closed) {
                int len = in.readInt();
                if (len < 0 || len > (64 << 20)) {
                    throw new IOException("implausible frame length: " + len);
                }
                byte[] frame = new byte[len];
                in.readFully(frame);
                int type = len > 0 ? (frame[0] & 0xFF) : -1;
                // Async events occupy [0x40, 0x7F); MSG_ERROR (0x7F) is a response to the in-flight request.
                if (type >= 0x40 && type != LiveProtocol.MSG_ERROR) {
                    final byte[] f = frame;
                    dispatchAsync(() -> dispatchEvent(f));
                } else {
                    responses.add(frame);   // unbounded: never blocks the reader
                }
            }
        } catch (EOFException eof) {
            // peer closed
        } catch (IOException e) {
            // connection error
        } finally {
            // Wake any request blocked waiting for a response it will now never get.
            responses.offer(POISON);
            if (!closed) {
                dispatchAsync(() -> emit(LiveEvent.vmDeath()));
            }
        }
    }

    /** Runs an event task on the dedicated dispatch thread; a no-op once dispatch has been shut down. */
    private void dispatchAsync(Runnable task) {
        try {
            eventDispatch.execute(task);
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void dispatchEvent(byte[] frame) {
        if (listeners.isEmpty()) {
            return;
        }
        try {
            DataInputStream r = new DataInputStream(new ByteArrayInputStream(frame));
            int type = r.readUnsignedByte();
            if (type == LiveProtocol.EVT_CLASS_LOADED) {
                String name = readString(r);
                byte[] bytes = new byte[r.readInt()];
                r.readFully(bytes);
                emit(LiveEvent.classLoaded(name, bytes));
            }
        } catch (IOException ignored) {
        }
    }

    private synchronized DataInputStream request(byte[] payload) throws IOException {
        if (closed) {
            throw new IOException("live agent connection is closed");
        }
        responses.clear();   // drop any straggler from a prior timed-out request
        out.writeInt(payload.length);
        out.write(payload);
        out.flush();
        byte[] resp;
        try {
            resp = responses.poll(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted waiting for response", e);
        }
        if (resp == null) {        // backstop timeout: agent wedged - tear the connection down
            closeQuietly();
            throw new IOException("live agent did not respond");
        }
        if (resp.length == 0) {    // POISON: the connection dropped while we were waiting
            throw new IOException("live agent disconnected");
        }
        DataInputStream r = new DataInputStream(new ByteArrayInputStream(resp));
        r.mark(1);
        if ((r.readUnsignedByte()) == LiveProtocol.MSG_ERROR) {
            throw new IOException("agent error: " + readString(r));
        }
        r.reset();
        return r;
    }

    private interface BodyWriter {
        void write(DataOutputStream b) throws IOException;
    }

    private static byte[] payload(int type, BodyWriter body) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        DataOutputStream b = new DataOutputStream(bo);
        b.writeByte(type);
        body.write(b);
        return bo.toByteArray();
    }

    private static void skipType(DataInputStream r, int expected) throws IOException {
        int type = r.readUnsignedByte();
        if (type != expected) {
            throw new IOException("unexpected response type " + type + " (wanted " + expected + ")");
        }
    }

    private static String readString(DataInputStream r) throws IOException {
        byte[] b = new byte[r.readUnsignedShort()];
        r.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static void writeString(DataOutputStream w, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        w.writeShort(b.length);
        w.write(b);
    }

    @Override
    public void close() throws IOException {
        closed = true;
        responses.offer(POISON);     // wake any in-flight request
        eventDispatch.shutdownNow();
        reader.interrupt();
        socket.close();
    }

    private void closeQuietly() {
        try {
            close();
        } catch (IOException ignored) {
        }
    }
}
