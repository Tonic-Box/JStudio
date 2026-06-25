package com.tonic.live.agent;

import com.tonic.live.protocol.LiveProtocol;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The JStudio Live agent, built on {@link java.lang.instrument} - pure Java, so it works on any OS/arch
 * with no native build. It supports class browse, get-bytes, live method-body patch (redefine), runtime
 * class capture, the thread list, and deadlock detection. JStudio's {@code LiveAgentClient} speaks the same
 * wire protocol.
 *
 * <p>Loaded via {@code VirtualMachine.loadAgent} (agentmain, the attach path) or
 * {@code -javaagent:live-agent.jar=port=N} (premain). Bundled in JStudio.jar as {@code agent/live-agent.bin}.
 */
public final class JavaAgent {

    private static volatile Instrumentation inst;
    private static volatile Socket client;
    private static final Object WRITE_LOCK = new Object();

    private static volatile String captureTargetName;
    private static volatile byte[] capturedBytes;
    private static volatile boolean captureLoadsArmed;
    private static final java.util.concurrent.atomic.AtomicInteger heapDumpCounter =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final JfrController jfr = new JfrController();

    private JavaAgent() {
    }

    public static void premain(String args, Instrumentation instrumentation) {
        start(args, instrumentation);
    }

    public static void agentmain(String args, Instrumentation instrumentation) {
        start(args, instrumentation);
    }

    private static void start(String args, Instrumentation instrumentation) {
        inst = instrumentation;
        openAllModules(instrumentation);
        int port = parsePort(args);
        if (port <= 0) {
            log("no/invalid port in agent args: " + args);
            return;
        }
        instrumentation.addTransformer(new CaptureTransformer(), instrumentation.isRetransformClassesSupported());
        Thread t = new Thread(() -> serve(port), "jstudio-live-java-agent");
        t.setDaemon(true);
        t.start();
        log("started on 127.0.0.1:" + port);
    }

    /**
     * Opens every boot-layer module's packages to this agent's module so the heap scanner can read fields
     * of JDK objects (Swing/AWT component trees, collection internals, etc.) via reflection. Without this,
     * {@code setAccessible(true)} throws {@code InaccessibleObjectException} on Java 17+ for closed modules,
     * the object-graph walk dies at the first JDK object, and app values held inside JDK containers are
     * never visited (e.g. a button/list string reachable only through a Swing window's component tree).
     */
    private static void openAllModules(Instrumentation inst) {
        try {
            java.util.Set<Module> agent = java.util.Collections.singleton(JavaAgent.class.getModule());
            for (Module m : ModuleLayer.boot().modules()) {
                if (!inst.isModifiableModule(m)) {
                    continue;
                }
                java.util.Map<String, java.util.Set<Module>> opens = new java.util.HashMap<>();
                for (String pkg : m.getPackages()) {
                    opens.put(pkg, agent);
                }
                inst.redefineModule(m, java.util.Collections.emptySet(), java.util.Collections.emptyMap(),
                        opens, java.util.Collections.emptySet(), java.util.Collections.emptyMap());
            }
        } catch (Throwable t) {
            log("openAllModules failed (heap scan may miss values inside JDK objects): " + t);
        }
    }

    private static int parsePort(String args) {
        if (args == null) {
            return -1;
        }
        for (String part : args.split(",")) {
            part = part.trim();
            if (part.startsWith("port=")) {
                try {
                    return Integer.parseInt(part.substring(5).trim());
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }

    // ---- server ------------------------------------------------------------------------------------

    private static void serve(int port) {
        try (ServerSocket server = new ServerSocket(port, 4, InetAddress.getByName("127.0.0.1"))) {
            log("listening on 127.0.0.1:" + port);
            while (true) {
                Socket s = server.accept();
                try (s) {
                    client = s;
                    serveConnection(s);
                } catch (IOException ignored) {
                    // peer closed
                } finally {
                    client = null;
                    jfr.discard();
                }
            }
        } catch (IOException e) {
            log("server error: " + e.getMessage());
        }
    }

    private static void serveConnection(Socket s) throws IOException {
        DataInputStream in = new DataInputStream(s.getInputStream());
        try {
            while (true) {
                int len = in.readInt();
                if (len < 0 || len > (64 << 20)) {
                    return;
                }
                byte[] frame = new byte[len];
                in.readFully(frame);
                if (frame.length == 0) {
                    continue;
                }
                DataInputStream body = new DataInputStream(new java.io.ByteArrayInputStream(frame, 1, frame.length - 1));
                byte[] response = dispatch(frame[0] & 0xFF, body);
                if (response != null) {
                    sendFrame(response);
                }
            }
        } finally {
            SCAN.clear();
        }
    }

    private static byte[] dispatch(int type, DataInputStream in) throws IOException {
        switch (type) {
            case LiveProtocol.MSG_HELLO:
                return handleHello();
            case LiveProtocol.MSG_LIST_CLASSES:
                return handleListClasses();
            case LiveProtocol.MSG_GET_CLASS_BYTES:
                return handleGetClassBytes(readString(in));
            case LiveProtocol.MSG_GET_THREADS:
                return handleGetThreads();
            case LiveProtocol.MSG_REDEFINE_CLASS:
                return handleRedefine(in);
            case LiveProtocol.MSG_SET_CAPTURE_LOADS:
                captureLoadsArmed = in.readUnsignedByte() != 0;
                return resp(LiveProtocol.MSG_SET_CAPTURE_LOADS, 1);
            case LiveProtocol.MSG_GET_CONTENTION:
                return handleContention();
            case LiveProtocol.MSG_HEAP_DUMP:
                return handleHeapDump();
            case LiveProtocol.MSG_GET_STATICS:
                return handleGetStatics(readString(in));
            case LiveProtocol.MSG_SET_STATIC:
                return handleSetStatic(in);
            case LiveProtocol.MSG_LIST_STATIC_METHODS:
                return handleListStaticMethods(readString(in));
            case LiveProtocol.MSG_INVOKE_STATIC:
                return handleInvokeStatic(in);
            case LiveProtocol.MSG_GET_THREAD_STACKS:
                return handleGetThreadStacks(in);
            case LiveProtocol.MSG_GET_METRICS:
                return handleGetMetrics();
            case LiveProtocol.MSG_EVAL:
                return handleEval(in);
            case LiveProtocol.MSG_JFR_START:
                return handleJfrStart(in);
            case LiveProtocol.MSG_JFR_STOP:
                return handleJfrStop();
            case LiveProtocol.MSG_JFR_SNAPSHOT:
                return handleJfrSnapshot();
            case LiveProtocol.MSG_SCAN_FIRST:
                return handleScanFirst(in);
            case LiveProtocol.MSG_SCAN_NEXT:
                return handleScanNext(in);
            case LiveProtocol.MSG_SCAN_READ:
                return handleScanRead(in);
            case LiveProtocol.MSG_SCAN_WRITE:
                return handleScanWrite(in);
            case LiveProtocol.MSG_SCAN_FREEZE:
                return handleScanFreeze(in);
            case LiveProtocol.MSG_SCAN_PIN:
                return handleScanPin(in);
            case LiveProtocol.MSG_SCAN_CLEAR:
                return handleScanClear();
            case LiveProtocol.MSG_LIST_INSTANCES:
                return handleListInstances(in);
            case LiveProtocol.MSG_INSTANCE_FIELDS:
                return handleInstanceFields(in);
            case LiveProtocol.MSG_SET_INSTANCE_FIELD:
                return handleSetInstanceField(in);
            default:
                return error("operation not supported by the JStudio Live agent");
        }
    }

    private static byte[] handleHello() throws IOException {
        Buf b = new Buf();
        b.u8(LiveProtocol.MSG_HELLO);
        b.u32(0); // version marker (unused)
        int caps = LiveProtocol.CAP_REDEFINE | LiveProtocol.CAP_RETRANSFORM | LiveProtocol.CAP_BYTECODES;
        if (JfrController.isAvailable()) {
            caps |= LiveProtocol.CAP_JFR;
        }
        b.u32(caps);
        b.u32(inst.getAllLoadedClasses().length);
        return b.toBytes();
    }

    private static byte[] handleGetMetrics() throws IOException {
        Buf b = new Buf();
        b.u8(LiveProtocol.MSG_GET_METRICS);

        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        ClassLoadingMXBean classes = ManagementFactory.getClassLoadingMXBean();
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();

        b.u64(runtime.getUptime());
        b.u64(heap.getUsed());
        b.u64(heap.getCommitted());
        b.u64(heap.getMax());
        b.u64(nonHeap.getUsed());
        b.u64(nonHeap.getCommitted());
        b.u64(nonHeap.getMax());

        // getProcessCpuLoad/getSystemCpuLoad are on the HotSpot/OpenJDK extension bean; -1 when unavailable.
        double processCpu = -1;
        double systemCpu = -1;
        if (os instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean ext = (com.sun.management.OperatingSystemMXBean) os;
            processCpu = ext.getProcessCpuLoad();
            systemCpu = ext.getSystemCpuLoad();
        }
        b.f64(processCpu);
        b.f64(systemCpu);
        b.u32(os.getAvailableProcessors());

        b.u32(threads.getThreadCount());
        b.u32(threads.getDaemonThreadCount());
        b.u32(threads.getPeakThreadCount());
        b.u64(threads.getTotalStartedThreadCount());

        b.u32(classes.getLoadedClassCount());
        b.u64(classes.getTotalLoadedClassCount());
        b.u64(classes.getUnloadedClassCount());

        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        b.u32(pools.size());
        for (MemoryPoolMXBean pool : pools) {
            MemoryUsage usage = pool.getUsage();
            b.str(pool.getName());
            b.u64(usage != null ? usage.getUsed() : -1);
            b.u64(usage != null ? usage.getCommitted() : -1);
            b.u64(usage != null ? usage.getMax() : -1);
        }

        List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
        b.u32(collectors.size());
        for (GarbageCollectorMXBean gc : collectors) {
            b.str(gc.getName());
            b.u64(gc.getCollectionCount());
            b.u64(gc.getCollectionTime());
        }
        return b.toBytes();
    }

    private static byte[] handleListClasses() throws IOException {
        Class<?>[] classes = inst.getAllLoadedClasses();
        Buf b = new Buf();
        b.u8(LiveProtocol.MSG_LIST_CLASSES);
        ByteArrayOutputStream tmp = new ByteArrayOutputStream();
        DataOutputStream td = new DataOutputStream(tmp);
        int count = 0;
        for (Class<?> c : classes) {
            if (c.isPrimitive() || c.isArray()) {
                continue;
            }
            String internal = c.getName().replace('.', '/');
            if (isAgentClass(internal)) {
                continue;
            }
            writeString(td, internal);
            td.writeShort(c.getModifiers() & 0xFFFF);
            count++;
        }
        b.u32(count);
        b.raw(tmp.toByteArray());
        return b.toBytes();
    }

    private static byte[] handleGetClassBytes(String internalName) throws IOException {
        Class<?> target = findLoaded(internalName);
        if (target == null) {
            return error("class not loaded: " + internalName);
        }
        if (!inst.isModifiableClass(target) || !inst.isRetransformClassesSupported()) {
            return error("class not retransformable: " + internalName);
        }
        synchronized (JavaAgent.class) {
            captureTargetName = internalName;
            capturedBytes = null;
            try {
                inst.retransformClasses(target);
            } catch (Throwable t) {
                captureTargetName = null;
                return error("retransform failed: " + t.getMessage());
            }
            byte[] bytes = capturedBytes;
            captureTargetName = null;
            if (bytes == null) {
                return error("capture produced no bytes for " + internalName);
            }
            Buf b = new Buf();
            b.u8(LiveProtocol.MSG_GET_CLASS_BYTES);
            b.u32(bytes.length);
            b.raw(bytes);
            return b.toBytes();
        }
    }

    private static byte[] handleGetThreads() throws IOException {
        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        long[] ids = mx.getAllThreadIds();
        ThreadInfo[] infos = mx.getThreadInfo(ids);
        Buf b = new Buf();
        b.u8(LiveProtocol.MSG_GET_THREADS);
        ByteArrayOutputStream tmp = new ByteArrayOutputStream();
        DataOutputStream td = new DataOutputStream(tmp);
        int count = 0;
        for (ThreadInfo ti : infos) {
            if (ti == null) {
                continue;
            }
            td.writeLong(ti.getThreadId());
            writeString(td, ti.getThreadName());
            td.writeInt(ti.getThreadState().ordinal());
            count++;
        }
        b.u32(count);
        b.raw(tmp.toByteArray());
        return b.toBytes();
    }

    private static byte[] handleRedefine(DataInputStream in) throws IOException {
        String name = readString(in);
        byte[] bytes = new byte[in.readInt()];
        in.readFully(bytes);
        Class<?> target = findLoaded(name);
        if (target == null) {
            return error("class not loaded: " + name);
        }
        try {
            inst.redefineClasses(new ClassDefinition(target, bytes));
        } catch (UnsupportedOperationException t) {
            return error("redefine rejected (unsupported change): " + describe(t)
                    + ". HotSpot only allows method-body changes - adding/removing methods or fields"
                    + " (often introduced by decompile/recompile of synthetic members) is not redefinable.");
        } catch (Throwable t) {
            return error("redefine failed: " + describe(t));
        }
        return resp(LiveProtocol.MSG_REDEFINE_CLASS, 1);
    }

    /** Describes a throwable usefully even when its message is null (common for JVM redefine errors). */
    private static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder(t.getClass().getName());
        if (t.getMessage() != null) {
            sb.append(": ").append(t.getMessage());
        }
        Throwable cause = t.getCause();
        if (cause != null && cause != t) {
            sb.append(" (cause: ").append(cause.getClass().getName());
            if (cause.getMessage() != null) {
                sb.append(": ").append(cause.getMessage());
            }
            sb.append(')');
        }
        return sb.toString();
    }

    private static byte[] handleContention() throws IOException {
        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        ThreadInfo[] infos = mx.getThreadInfo(mx.getAllThreadIds());
        Buf b = new Buf();
        b.u8(LiveProtocol.MSG_GET_CONTENTION);
        ByteArrayOutputStream tmp = new ByteArrayOutputStream();
        DataOutputStream td = new DataOutputStream(tmp);
        int count = 0;
        for (ThreadInfo ti : infos) {
            if (ti == null || ti.getLockInfo() == null || ti.getLockOwnerId() < 0) {
                continue;
            }
            td.writeLong(ti.getThreadId());
            writeString(td, ti.getThreadName());
            writeString(td, ti.getLockInfo().getClassName().replace('.', '/'));
            td.writeLong(ti.getLockOwnerId());
            writeString(td, ti.getLockOwnerName() == null ? "" : ti.getLockOwnerName());
            count++;
        }
        b.u32(count);
        b.raw(tmp.toByteArray());
        return b.toBytes();
    }

    private static byte[] handleHeapDump() throws IOException {
        try {
            com.sun.management.HotSpotDiagnosticMXBean mx =
                    ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class);
            if (mx == null) {
                return error("heap dump unsupported on this JVM (no HotSpotDiagnosticMXBean)");
            }
            File out = new File(System.getProperty("java.io.tmpdir"),
                    "jstudio-heap-" + heapDumpCounter.incrementAndGet() + ".hprof");
            if (out.exists() && !out.delete()) {
                return error("could not clear previous heap dump file: " + out);
            }
            mx.dumpHeap(out.getAbsolutePath(), true);
            Buf b = new Buf();
            b.u8(LiveProtocol.MSG_HEAP_DUMP);
            b.str(out.getAbsolutePath());
            return b.toBytes();
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            return error("heap dump failed: " + cause.getMessage());
        }
    }

    // ---- transformer (get-bytes capture + runtime class-load streaming) ----------------------------

    private static final class CaptureTransformer implements ClassFileTransformer {
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                ProtectionDomain pd, byte[] classfileBuffer) {
            if (className == null) {
                return null;
            }
            if (classBeingRedefined != null) {
                if (className.equals(captureTargetName)) {
                    capturedBytes = classfileBuffer.clone();
                }
                return null;
            }
            if (captureLoadsArmed && loader != null && !isAgentClass(className)) {
                try {
                    Buf b = new Buf();
                    b.u8(LiveProtocol.EVT_CLASS_LOADED);
                    b.str(className);
                    b.u32(classfileBuffer.length);
                    b.raw(classfileBuffer);
                    sendFrame(b.toBytes());
                } catch (IOException ignored) {
                }
            }
            return null;
        }
    }

    // ---- live statics / method invoke ---------------------------------------------------------------

    private static byte[] handleGetStatics(String internalName) throws IOException {
        Class<?> c = findLoaded(internalName);
        if (c == null) {
            return error("class not loaded: " + internalName);
        }
        Buf b = new Buf();
        b.u8(LiveProtocol.MSG_GET_STATICS);
        ByteArrayOutputStream tmp = new ByteArrayOutputStream();
        DataOutputStream td = new DataOutputStream(tmp);
        int count = 0;
        for (Field f : c.getDeclaredFields()) {
            int mods = f.getModifiers();
            if (!Modifier.isStatic(mods)) {
                continue;
            }
            Class<?> type = f.getType();
            String valueStr;
            try {
                f.setAccessible(true);
                valueStr = formatValue(f.get(null), type);
            } catch (Throwable t) {
                valueStr = "<inaccessible>";
            }
            int kind;
            if (Modifier.isFinal(mods)) {
                kind = LiveProtocol.STATIC_READONLY;
            } else if (type.isPrimitive()) {
                kind = LiveProtocol.STATIC_PRIMITIVE;
            } else if (type == String.class) {
                kind = LiveProtocol.STATIC_STRING;
            } else {
                kind = LiveProtocol.STATIC_REFERENCE;
            }
            writeString(td, f.getName());
            writeString(td, descriptor(type));
            writeString(td, valueStr);
            td.writeByte(kind);
            count++;
        }
        b.u32(count);
        b.raw(tmp.toByteArray());
        return b.toBytes();
    }

    private static byte[] handleSetStatic(DataInputStream in) throws IOException {
        String className = readString(in);
        String fieldName = readString(in);
        boolean setNull = in.readUnsignedByte() != 0;
        String value = readString(in);
        Class<?> c = findLoaded(className);
        if (c == null) {
            return error("class not loaded: " + className);
        }
        Field f = findField(c, fieldName);
        if (f == null) {
            return error("no such static field: " + fieldName);
        }
        int mods = f.getModifiers();
        if (!Modifier.isStatic(mods)) {
            return error(fieldName + " is not static");
        }
        if (Modifier.isFinal(mods)) {
            return error(fieldName + " is final");
        }
        Class<?> type = f.getType();
        try {
            f.setAccessible(true);
            if (setNull) {
                if (type.isPrimitive()) {
                    return error("cannot set a primitive field to null");
                }
                f.set(null, null);
            } else {
                f.set(null, parseValue(value, type));
            }
            return strResp(LiveProtocol.MSG_SET_STATIC, formatValue(f.get(null), type));
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (Throwable t) {
            return error("set failed: " + t.getMessage());
        }
    }

    private static byte[] handleListStaticMethods(String internalName) throws IOException {
        Class<?> c = findLoaded(internalName);
        if (c == null) {
            return error("class not loaded: " + internalName);
        }
        Buf b = new Buf();
        b.u8(LiveProtocol.MSG_LIST_STATIC_METHODS);
        ByteArrayOutputStream tmp = new ByteArrayOutputStream();
        DataOutputStream td = new DataOutputStream(tmp);
        int count = 0;
        for (Method m : c.getDeclaredMethods()) {
            if (!Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            writeString(td, m.getName());
            writeString(td, methodDescriptor(m));
            count++;
        }
        b.u32(count);
        b.raw(tmp.toByteArray());
        return b.toBytes();
    }

    private static byte[] handleInvokeStatic(DataInputStream in) throws IOException {
        String className = readString(in);
        String name = readString(in);
        String desc = readString(in);
        int argc = in.readInt();
        String[] args = new String[Math.max(0, argc)];
        for (int i = 0; i < argc; i++) {
            args[i] = readString(in);
        }
        Class<?> c = findLoaded(className);
        if (c == null) {
            return error("class not loaded: " + className);
        }
        Method target = null;
        for (Method m : c.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) && m.getName().equals(name)
                    && methodDescriptor(m).equals(desc)) {
                target = m;
                break;
            }
        }
        if (target == null) {
            return error("no matching static method: " + name + desc);
        }
        Class<?>[] paramTypes = target.getParameterTypes();
        if (paramTypes.length != args.length) {
            return error("argument count mismatch");
        }
        Object[] values = new Object[paramTypes.length];
        try {
            for (int i = 0; i < paramTypes.length; i++) {
                values[i] = parseArg(args[i], paramTypes[i]);
            }
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
        try {
            target.setAccessible(true);
            Object ret = target.invoke(null, values);
            String result = target.getReturnType() == void.class ? "(void)" : formatValue(ret, target.getReturnType());
            return strResp(LiveProtocol.MSG_INVOKE_STATIC, result);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return error("threw " + cause.getClass().getName() + ": " + cause.getMessage());
        } catch (Throwable t) {
            return error("invoke failed: " + t.getMessage());
        }
    }

    private static byte[] handleGetThreadStacks(DataInputStream in) throws IOException {
        int maxDepth = in.readInt();
        if (maxDepth <= 0) {
            maxDepth = 64;
        }
        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        ThreadInfo[] infos = mx.getThreadInfo(mx.getAllThreadIds(), maxDepth);
        Buf b = new Buf();
        b.u8(LiveProtocol.MSG_GET_THREAD_STACKS);
        ByteArrayOutputStream tmp = new ByteArrayOutputStream();
        DataOutputStream td = new DataOutputStream(tmp);
        int count = 0;
        for (ThreadInfo ti : infos) {
            if (ti == null) {
                continue;
            }
            td.writeLong(ti.getThreadId());
            writeString(td, ti.getThreadName());
            td.writeInt(ti.getThreadState().ordinal());
            StackTraceElement[] stack = ti.getStackTrace();
            td.writeInt(stack.length);
            for (StackTraceElement e : stack) {
                writeString(td, e.getClassName().replace('.', '/'));
                writeString(td, e.getMethodName());
                writeString(td, e.getFileName() == null ? "" : e.getFileName());
                td.writeInt(e.getLineNumber());
            }
            count++;
        }
        b.u32(count);
        b.raw(tmp.toByteArray());
        return b.toBytes();
    }

    private static String formatValue(Object value, Class<?> type) {
        if (value == null) {
            return "null";
        }
        if (type.isPrimitive() || value instanceof Number || value instanceof Boolean || value instanceof Character) {
            return String.valueOf(value);
        }
        if (value instanceof String) {
            return (String) value;
        }
        return value.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(value));
    }

    private static Object parseArg(String s, Class<?> type) {
        if (!type.isPrimitive() && "null".equals(s)) {
            return null;
        }
        return parseValue(s, type);
    }

    private static Object parseValue(String s, Class<?> type) {
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(s.trim());
        }
        if (type == byte.class || type == Byte.class) {
            return Byte.parseByte(s.trim());
        }
        if (type == short.class || type == Short.class) {
            return Short.parseShort(s.trim());
        }
        if (type == int.class || type == Integer.class) {
            return Integer.parseInt(s.trim());
        }
        if (type == long.class || type == Long.class) {
            return Long.parseLong(s.trim());
        }
        if (type == float.class || type == Float.class) {
            return Float.parseFloat(s.trim());
        }
        if (type == double.class || type == Double.class) {
            return Double.parseDouble(s.trim());
        }
        if (type == char.class || type == Character.class) {
            if (s.isEmpty()) {
                throw new IllegalArgumentException("empty char value");
            }
            return s.charAt(0);
        }
        if (type == String.class) {
            return s;
        }
        throw new IllegalArgumentException("only primitives, String, or null are supported for " + type.getName());
    }

    private static Field findField(Class<?> c, String name) {
        for (Field f : c.getDeclaredFields()) {
            if (f.getName().equals(name)) {
                return f;
            }
        }
        return null;
    }

    private static String methodDescriptor(Method m) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : m.getParameterTypes()) {
            sb.append(descriptor(p));
        }
        return sb.append(')').append(descriptor(m.getReturnType())).toString();
    }

    private static String descriptor(Class<?> c) {
        if (c == void.class) return "V";
        if (c == boolean.class) return "Z";
        if (c == byte.class) return "B";
        if (c == char.class) return "C";
        if (c == short.class) return "S";
        if (c == int.class) return "I";
        if (c == long.class) return "J";
        if (c == float.class) return "F";
        if (c == double.class) return "D";
        if (c.isArray()) return "[" + descriptor(c.getComponentType());
        return "L" + c.getName().replace('.', '/') + ";";
    }

    // ---- value scanner ----------------------------------------------------------------------------

    private static final ScanEngine SCAN = new ScanEngine();

    private static byte[] handleScanFirst(DataInputStream in) throws IOException {
        int valueType = in.readUnsignedByte();
        int scanKind = in.readUnsignedByte();
        String value = readString(in);
        String value2 = readString(in);
        String pkgFilter = readString(in);
        int maxVisited = in.readInt();
        int maxMatches = in.readInt();
        int limit = in.readInt();
        boolean userClassesOnly = in.readBoolean();
        try {
            SCAN.firstScan(inst, valueType, scanKind, value, value2, pkgFilter, userClassesOnly, maxVisited, maxMatches);
            return SCAN.page(LiveProtocol.MSG_SCAN_FIRST, false, 0, limit);
        } catch (NumberFormatException e) {
            return error("invalid scan value: " + e.getMessage());
        }
    }

    private static byte[] handleScanNext(DataInputStream in) throws IOException {
        int comparator = in.readUnsignedByte();
        String value = readString(in);
        String value2 = readString(in);
        int offset = in.readInt();
        int limit = in.readInt();
        SCAN.nextScan(comparator, value, value2);
        return SCAN.page(LiveProtocol.MSG_SCAN_NEXT, false, offset, limit);
    }

    private static byte[] handleScanRead(DataInputStream in) throws IOException {
        boolean pinnedOnly = in.readUnsignedByte() != 0;
        int offset = in.readInt();
        int limit = in.readInt();
        return SCAN.page(LiveProtocol.MSG_SCAN_READ, pinnedOnly, offset, limit);
    }

    private static byte[] handleScanWrite(DataInputStream in) throws IOException {
        long id = in.readLong();
        boolean isNull = in.readUnsignedByte() != 0;
        String value = readString(in);
        try {
            return strResp(LiveProtocol.MSG_SCAN_WRITE, SCAN.write(id, isNull, value));
        } catch (RuntimeException e) {
            return error(e.getMessage());
        }
    }

    private static byte[] handleListInstances(DataInputStream in) throws IOException {
        String className = readString(in).replace('/', '.');
        int maxInstances = in.readInt();
        int maxVisited = in.readInt();
        try {
            List<Object[]> rows = SCAN.collectInstances(inst, className, maxInstances, maxVisited);
            Buf b = new Buf();
            b.u8(LiveProtocol.MSG_LIST_INSTANCES);
            ByteArrayOutputStream tmp = new ByteArrayOutputStream();
            DataOutputStream td = new DataOutputStream(tmp);
            for (Object[] r : rows) {
                td.writeLong((Long) r[0]);
                writeString(td, (String) r[1]);
            }
            b.u32(rows.size());
            b.raw(tmp.toByteArray());
            return b.toBytes();
        } catch (RuntimeException e) {
            return error(e.getMessage());
        }
    }

    private static byte[] handleInstanceFields(DataInputStream in) throws IOException {
        long id = in.readLong();
        try {
            List<Object[]> rows = SCAN.instanceFields(id);
            Buf b = new Buf();
            b.u8(LiveProtocol.MSG_INSTANCE_FIELDS);
            ByteArrayOutputStream tmp = new ByteArrayOutputStream();
            DataOutputStream td = new DataOutputStream(tmp);
            for (Object[] r : rows) {
                writeString(td, (String) r[0]);
                writeString(td, (String) r[1]);
                writeString(td, (String) r[2]);
                td.writeLong((Long) r[3]);
                td.writeByte(((Boolean) r[4]) ? 1 : 0);
            }
            b.u32(rows.size());
            b.raw(tmp.toByteArray());
            return b.toBytes();
        } catch (RuntimeException e) {
            return error(e.getMessage());
        }
    }

    private static byte[] handleSetInstanceField(DataInputStream in) throws IOException {
        long id = in.readLong();
        String field = readString(in);
        boolean isNull = in.readUnsignedByte() != 0;
        String value = readString(in);
        try {
            return strResp(LiveProtocol.MSG_SET_INSTANCE_FIELD, SCAN.setInstanceField(id, field, isNull, value));
        } catch (RuntimeException e) {
            return error(e.getMessage());
        }
    }

    private static byte[] handleScanFreeze(DataInputStream in) throws IOException {
        long id = in.readLong();
        boolean on = in.readUnsignedByte() != 0;
        String value = readString(in);
        try {
            SCAN.freeze(id, on, value);
            return resp(LiveProtocol.MSG_SCAN_FREEZE, 1);
        } catch (RuntimeException e) {
            return error(e.getMessage());
        }
    }

    private static byte[] handleScanPin(DataInputStream in) throws IOException {
        long id = in.readLong();
        boolean on = in.readUnsignedByte() != 0;
        SCAN.pin(id, on);
        return resp(LiveProtocol.MSG_SCAN_PIN, 1);
    }

    private static byte[] handleScanClear() throws IOException {
        SCAN.clear();
        return resp(LiveProtocol.MSG_SCAN_CLEAR, 1);
    }

    // ---- helpers -----------------------------------------------------------------------------------

    /** The agent's own classes (loaded into the target by attaching) - never report them to JStudio. */
    private static boolean isAgentClass(String internalName) {
        return internalName.startsWith("com/tonic/live/agent/")
                || internalName.startsWith("com/tonic/live/protocol/");
    }

    /**
     * Defines a freshly-compiled snippet class in the target and invokes its {@code static Object run()}. The
     * class is defined in a throwaway loader whose parent is the chosen context class's loader, so it links
     * against exactly what that class sees, and is discarded after the call (no class accumulation, and each
     * run gets its own namespace - re-running the same name never collides).
     */
    private static byte[] handleEval(DataInputStream in) throws IOException {
        int classCount = in.readInt();
        Map<String, byte[]> classes = new LinkedHashMap<>();
        for (int i = 0; i < classCount; i++) {
            String name = readString(in);
            byte[] bytes = new byte[in.readInt()];
            in.readFully(bytes);
            classes.put(name, bytes);
        }
        String mainBinaryName = readString(in);
        String contextClassInternal = readString(in);
        // Define all of the snippet's classes (the wrapper plus any anonymous/local classes) into one
        // throwaway loader so cross-references between them resolve, then invoke the wrapper's run(). The
        // loader is closed once run() has returned (its result already stringified) - already-defined classes
        // remain usable, this just releases the loader's resource handles.
        try (ScratchLoader loader = new ScratchLoader(resolveContextLoader(contextClassInternal))) {
            Class<?> main = null;
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                Class<?> defined = loader.define(entry.getKey(), entry.getValue());
                if (entry.getKey().equals(mainBinaryName)) {
                    main = defined;
                }
            }
            if (main == null) {
                return strResp(LiveProtocol.MSG_EVAL, "eval setup failed: main class " + mainBinaryName + " missing");
            }
            Method run = main.getDeclaredMethod("run");
            run.setAccessible(true);
            return strResp(LiveProtocol.MSG_EVAL, invokeCapturing(run));
        } catch (Throwable t) {
            return strResp(LiveProtocol.MSG_EVAL, "eval setup failed: " + t);
        }
    }

    // ---- JFR recorder -------------------------------------------------------------------------------

    private static byte[] handleJfrStart(DataInputStream in) throws IOException {
        String profile = readString(in);
        int categoryMask = in.readInt();
        int maxSizeMb = in.readInt();
        try {
            jfr.start(profile, categoryMask, maxSizeMb);
            return resp(LiveProtocol.MSG_JFR_START, 1);
        } catch (Throwable t) {
            return error("JFR start failed: " + describe(t));
        }
    }

    private static byte[] handleJfrStop() throws IOException {
        try {
            return strResp(LiveProtocol.MSG_JFR_STOP, jfr.stop());
        } catch (Throwable t) {
            return error("JFR stop failed: " + describe(t));
        }
    }

    private static byte[] handleJfrSnapshot() throws IOException {
        try {
            return strResp(LiveProtocol.MSG_JFR_SNAPSHOT, jfr.snapshot());
        } catch (Throwable t) {
            return error("JFR snapshot failed: " + describe(t));
        }
    }

    /** The classloader of the chosen context class (so the snippet sees what it sees); system loader otherwise. */
    private static ClassLoader resolveContextLoader(String contextClassInternal) {
        if (contextClassInternal != null && !contextClassInternal.isEmpty()) {
            Class<?> ctx = findLoaded(contextClassInternal);
            if (ctx != null && ctx.getClassLoader() != null) {
                return ctx.getClassLoader();
            }
        }
        return ClassLoader.getSystemClassLoader();
    }

    /** Invokes {@code run()}, teeing stdout/stderr into a buffer, and returns the output plus result or trace. */
    private static String invokeCapturing(Method run) {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream tee = new PrintStream(captured, true, StandardCharsets.UTF_8);
        PrintStream prevOut = System.out;
        PrintStream prevErr = System.err;
        System.setOut(tee);
        System.setErr(tee);
        try {
            Object ret = run.invoke(null);
            tee.flush();
            return captured.toString(StandardCharsets.UTF_8) + "=> " + ret;
        } catch (Throwable t) {
            tee.flush();
            Throwable cause = (t instanceof InvocationTargetException && t.getCause() != null) ? t.getCause() : t;
            StringWriter sw = new StringWriter();
            cause.printStackTrace(new PrintWriter(sw));
            return captured.toString(StandardCharsets.UTF_8) + "threw " + sw;
        } finally {
            System.setOut(prevOut);
            System.setErr(prevErr);
        }
    }

    /**
     * A throwaway classloader for one eval: defines the snippet class with full visibility into the context
     * loader (its parent). As an agent-loaded subclass it can call the protected {@code defineClass} directly,
     * so no JDK-internal reflection / {@code --add-opens} is needed.
     */
    private static final class ScratchLoader extends URLClassLoader {
        ScratchLoader(ClassLoader parent) {
            super(new URL[0], parent);
        }

        Class<?> define(String binaryName, byte[] bytes) {
            return defineClass(binaryName, bytes, 0, bytes.length);
        }
    }

    private static Class<?> findLoaded(String internalName) {
        String binary = internalName.replace('/', '.');
        for (Class<?> c : inst.getAllLoadedClasses()) {
            if (c.getName().equals(binary)) {
                return c;
            }
        }
        return null;
    }

    private static void sendFrame(byte[] payload) throws IOException {
        Socket s = client;
        if (s == null) {
            return;
        }
        synchronized (WRITE_LOCK) {
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            out.writeInt(payload.length);
            out.write(payload);
            out.flush();
        }
    }

    private static byte[] resp(int type, int ok) throws IOException {
        Buf b = new Buf();
        b.u8(type);
        b.u8(ok);
        return b.toBytes();
    }

    private static byte[] strResp(int type, String value) throws IOException {
        Buf b = new Buf();
        b.u8(type);
        b.str(value);
        return b.toBytes();
    }

    private static byte[] error(String message) throws IOException {
        Buf b = new Buf();
        b.u8(LiveProtocol.MSG_ERROR);
        b.str(message);
        return b.toBytes();
    }

    private static String readString(DataInputStream in) throws IOException {
        byte[] b = new byte[in.readUnsignedShort()];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.writeShort(b.length);
        out.write(b);
    }

    private static void log(String message) {
        System.err.println("[jstudio-live-java] " + message);
    }

    /** Minimal big-endian payload builder mirroring the native agent's Writer. */
    private static final class Buf {
        private final ByteArrayOutputStream bo = new ByteArrayOutputStream();
        private final DataOutputStream d = new DataOutputStream(bo);

        void u8(int v) throws IOException {
            d.writeByte(v);
        }

        void u32(int v) throws IOException {
            d.writeInt(v);
        }

        void u64(long v) throws IOException {
            d.writeLong(v);
        }

        void f64(double v) throws IOException {
            d.writeDouble(v);
        }

        void str(String s) throws IOException {
            writeString(d, s);
        }

        void raw(byte[] b) throws IOException {
            d.write(b);
        }

        byte[] toBytes() throws IOException {
            d.flush();
            return bo.toByteArray();
        }
    }
}
