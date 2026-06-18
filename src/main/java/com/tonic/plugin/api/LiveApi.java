package com.tonic.plugin.api;

import lombok.Getter;

import java.util.List;

/**
 * Observe and (when explicitly enabled) act on a JVM that JStudio is attached to via its Live feature. All calls
 * require an active attachment ({@link #isAttached()}); methods throw {@link IllegalStateException} otherwise.
 * The observe methods are read-only; {@link #eval}, {@link #setStatic}, {@link #invokeStatic}, and
 * {@link #redefineClass} mutate / run code in the live process. Returns plain DTOs so callers need no live-client
 * dependency.
 */
public interface LiveApi {

    /** JFR category bits for {@link #jfr} (combine with {@code |}); consumed by plugins. */
    @SuppressWarnings("unused")
    int JFR_CPU = 1;
    @SuppressWarnings("unused")
    int JFR_ALLOC = 1 << 1;
    @SuppressWarnings("unused")
    int JFR_LOCKS = 1 << 2;
    @SuppressWarnings("unused")
    int JFR_EXCEPTIONS = 1 << 3;

    /** True when JStudio currently holds a live session to a target JVM. */
    boolean isAttached();

    /** A short description of the attachment (pid + agent info), or "not attached". */
    String attachInfo();

    // ---- observe (read-only) ----------------------------------------------

    Metrics metrics();

    /** Thread stacks (up to {@code maxDepth} frames each). */
    List<ThreadDump> threads(int maxDepth);

    /** Detected deadlock cycles (empty when none). */
    List<Deadlock> deadlocks();

    /** A class's static fields with their current live values. */
    List<StaticField> statics(String className);

    /** Records for {@code seconds} (categories from the {@code JFR_*} bits) and returns a compact text summary. */
    String jfr(int seconds, int categoryMask);

    /**
     * Enumerates live instances of {@code className} from a heap snapshot (paginated). {@code refresh} takes a
     * fresh heap dump (expensive); otherwise the cached snapshot is reused (or taken once if none exists).
     */
    Instances instances(String className, int offset, int limit, boolean refresh);

    /** Inspects one instance's fields by its object id (from {@link #instances}), using the cached snapshot. */
    InstanceInfo instance(String id);

    // ---- execute / mutate -------------------------------------------------

    /** Compiles and runs Java in the attached JVM (the Scratch Pad); {@code contextClass} is the load context. */
    EvalResult eval(String code, String contextClass);

    /** Sets a static field (or to null); returns the re-read value. */
    String setStatic(String className, String field, boolean setNull, String value);

    /** Invokes a static method (string-marshalled args); returns the formatted result. */
    String invokeStatic(String className, String method, String descriptor, List<String> args);

    /** Hot-applies JStudio's current bytecode for {@code className} to the live JVM. */
    void redefineClass(String className);

    // ---- DTOs -------------------------------------------------------------

    @Getter
    final class Metrics {
        private final long uptimeMs;
        private final long heapUsed;
        private final long heapMax;
        private final long nonHeapUsed;
        private final long nonHeapMax;
        private final double processCpuLoad;
        private final double systemCpuLoad;
        private final int availableProcessors;
        private final int threadCount;
        private final int daemonThreadCount;
        private final int peakThreadCount;
        private final int loadedClassCount;
        private final long totalLoadedClassCount;
        private final List<Gc> gc;
        private final List<Pool> pools;

        public Metrics(long uptimeMs, long heapUsed, long heapMax, long nonHeapUsed, long nonHeapMax,
                       double processCpuLoad, double systemCpuLoad, int availableProcessors, int threadCount,
                       int daemonThreadCount, int peakThreadCount, int loadedClassCount, long totalLoadedClassCount,
                       List<Gc> gc, List<Pool> pools) {
            this.uptimeMs = uptimeMs;
            this.heapUsed = heapUsed;
            this.heapMax = heapMax;
            this.nonHeapUsed = nonHeapUsed;
            this.nonHeapMax = nonHeapMax;
            this.processCpuLoad = processCpuLoad;
            this.systemCpuLoad = systemCpuLoad;
            this.availableProcessors = availableProcessors;
            this.threadCount = threadCount;
            this.daemonThreadCount = daemonThreadCount;
            this.peakThreadCount = peakThreadCount;
            this.loadedClassCount = loadedClassCount;
            this.totalLoadedClassCount = totalLoadedClassCount;
            this.gc = gc;
            this.pools = pools;
        }
    }

    @Getter
    final class Gc {
        private final String name;
        private final long count;
        private final long timeMs;

        public Gc(String name, long count, long timeMs) {
            this.name = name;
            this.count = count;
            this.timeMs = timeMs;
        }
    }

    @Getter
    final class Pool {
        private final String name;
        private final long used;
        private final long max;

        public Pool(String name, long used, long max) {
            this.name = name;
            this.used = used;
            this.max = max;
        }
    }

    @Getter
    final class ThreadDump {
        private final long id;
        private final String name;
        private final String state;
        private final List<Frame> frames;

        public ThreadDump(long id, String name, String state, List<Frame> frames) {
            this.id = id;
            this.name = name;
            this.state = state;
            this.frames = frames;
        }
    }

    @Getter
    final class Frame {
        private final String className;
        private final String method;
        private final String file;
        private final int line;

        public Frame(String className, String method, String file, int line) {
            this.className = className;
            this.method = method;
            this.file = file;
            this.line = line;
        }
    }

    @Getter
    final class Deadlock {
        /** Human-readable edges of the cycle, e.g. "thread A waits on Lock held by thread B". */
        private final List<String> edges;

        public Deadlock(List<String> edges) {
            this.edges = edges;
        }
    }

    @Getter
    final class StaticField {
        private final String name;
        private final String type;
        private final String value;

        public StaticField(String name, String type, String value) {
            this.name = name;
            this.type = type;
            this.value = value;
        }
    }

    @Getter
    final class EvalResult {
        private final boolean success;
        private final String output;

        public EvalResult(boolean success, String output) {
            this.success = success;
            this.output = output;
        }
    }

    @Getter
    final class Instances {
        private final String className;
        private final int total;
        private final List<InstanceRef> page;

        public Instances(String className, int total, List<InstanceRef> page) {
            this.className = className;
            this.total = total;
            this.page = page;
        }
    }

    @Getter
    final class InstanceRef {
        private final String id;
        private final String label;

        public InstanceRef(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    @Getter
    final class InstanceInfo {
        private final String id;
        private final String className;
        private final List<Field> fields;

        public InstanceInfo(String id, String className, List<Field> fields) {
            this.id = id;
            this.className = className;
            this.fields = fields;
        }
    }

    @Getter
    final class Field {
        private final String name;
        private final String type;
        private final String value;
        /** Object id of the referenced instance when {@code type == "ref"} (drill in via {@link #instance}), else null. */
        private final String refId;

        public Field(String name, String type, String value, String refId) {
            this.name = name;
            this.type = type;
            this.value = value;
            this.refId = refId;
        }
    }
}
