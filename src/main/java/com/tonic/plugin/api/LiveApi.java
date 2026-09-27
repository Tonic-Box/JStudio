package com.tonic.plugin.api;

import lombok.Getter;

import java.util.List;

/** Observes and acts on the JVM that JStudio's Live feature is attached to; attachment is checked on every call, calls other than isAttached and attachInfo throw IllegalStateException when detached, and eval, setStatic, invokeStatic and redefineClass change the target process. */
public interface LiveApi
{

    /** JFR category bits for jfr, combined with bitwise or. */
    @SuppressWarnings("unused")
    int JFR_CPU = 1;
    @SuppressWarnings("unused")
    int JFR_ALLOC = 1 << 1;
    @SuppressWarnings("unused")
    int JFR_LOCKS = 1 << 2;
    @SuppressWarnings("unused")
    int JFR_EXCEPTIONS = 1 << 3;

    /**
     * Reports whether JStudio holds a live session to a target JVM right now.
     *
     * @return true when attached
     */
    boolean isAttached();

    /**
     * Describes the attachment.
     *
     * @return "attached to pid" and the pid, or "not attached"
     */
    String attachInfo();

    /**
     * Reads the target's runtime metrics.
     *
     * @return a new snapshot of memory, CPU, thread, class and GC figures
     * @throws IllegalStateException if not attached
     * @throws RuntimeException if talking to the target fails
     */
    Metrics metrics();

    /**
     * Dumps the target's thread stacks.
     *
     * @param maxDepth the most frames to keep per thread
     * @return a new list with one entry per thread
     * @throws IllegalStateException if not attached
     * @throws RuntimeException if talking to the target fails
     */
    List<ThreadDump> threads(int maxDepth);

    /**
     * Finds monitor deadlock cycles in the target.
     *
     * @return a new list with one entry per cycle, empty when there are none
     * @throws IllegalStateException if not attached
     * @throws RuntimeException if talking to the target fails
     */
    List<Deadlock> deadlocks();

    /**
     * Reads a class's static fields with their current values.
     *
     * @param className the class, internal or dotted
     * @return a new list with one entry per static field
     * @throws IllegalStateException if not attached
     * @throws RuntimeException if talking to the target fails
     */
    List<StaticField> statics(String className);

    /**
     * Records a Flight Recorder profile and summarises it; blocks the calling thread for the whole recording, so never call it on the EDT.
     *
     * @param seconds how long to record, clamped to 1 to 30
     * @param categoryMask which JFR_ bits to record
     * @return a compact text summary, or a message saying JFR is not available on the target
     * @throws IllegalStateException if not attached
     * @throws RuntimeException if talking to the target fails
     */
    String jfr(int seconds, int categoryMask);

    /**
     * Lists live instances of a class from a heap snapshot, one page at a time; taking a snapshot dumps the target's heap, which is slow, so call it off the EDT.
     *
     * @param className the class, internal or dotted
     * @param offset the index of the first instance to return; negative is treated as 0
     * @param limit the most instances to return; negative is treated as 0
     * @param refresh true to take a fresh snapshot, false to reuse the cached one, taking one only when none exists
     * @return the total count and the requested page
     * @throws IllegalStateException if not attached
     * @throws RuntimeException if talking to the target fails
     */
    Instances instances(String className, int offset, int limit, boolean refresh);

    /**
     * Decodes one instance's fields from the cached heap snapshot.
     *
     * @param id the object id from instances or a Field's refId, hexadecimal with or without 0x
     * @return the instance's class and fields
     * @throws IllegalStateException if not attached or no snapshot has been taken yet
     * @throws IllegalArgumentException if the id is not hexadecimal
     * @throws RuntimeException if talking to the target fails
     */
    InstanceInfo instance(String id);

    /**
     * Compiles Java against the current project's classpath, as the Scratch Pad does, and runs it in the target.
     *
     * @param code the Java source to run
     * @param contextClass the class, dotted or internal, whose loader runs the code, or null or blank for the project's first class
     * @return success with the output, or failure with the compiler messages, the I/O error, or "No project is loaded."
     * @throws IllegalStateException if not attached
     */
    EvalResult eval(String code, String contextClass);

    /**
     * Sets a static field in the target.
     *
     * @param className the class, internal or dotted
     * @param field the field's name
     * @param setNull true to set it to null, ignoring value
     * @param value the new value as text, converted by the target agent
     * @return the field's value read back after the write
     * @throws IllegalStateException if not attached
     * @throws RuntimeException if talking to the target fails
     */
    String setStatic(String className, String field, boolean setNull, String value);

    /**
     * Invokes a static method in the target.
     *
     * @param className the class, internal or dotted
     * @param method the method's name
     * @param descriptor the method's JVM descriptor
     * @param args the arguments as text, converted by the target agent, or null for none
     * @return the result, formatted as text
     * @throws IllegalStateException if not attached
     * @throws RuntimeException if talking to the target fails
     */
    String invokeStatic(String className, String method, String descriptor, List<String> args);

    /**
     * Hot-swaps the class in the target with its current bytecode from the project.
     *
     * @param className the class, internal or dotted
     * @throws IllegalStateException if no project is loaded or not attached
     * @throws IllegalArgumentException if the class is not in the project
     * @throws RuntimeException if talking to the target fails
     */
    void redefineClass(String className);

    /** A snapshot of the target's runtime figures: uptime, heap and non-heap use, CPU load, thread and class counts, collectors and memory pools; sizes are in bytes. */
    @Getter
    final class Metrics
    {
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

        /**
         * Creates a snapshot.
         *
         * @param uptimeMs how long the target has run, in milliseconds
         * @param heapUsed heap bytes in use
         * @param heapMax the heap limit in bytes
         * @param nonHeapUsed non-heap bytes in use
         * @param nonHeapMax the non-heap limit in bytes
         * @param processCpuLoad the target process's CPU load, 0 to 1
         * @param systemCpuLoad the whole system's CPU load, 0 to 1
         * @param availableProcessors how many processors the target sees
         * @param threadCount live threads
         * @param daemonThreadCount live daemon threads
         * @param peakThreadCount the most live threads so far
         * @param loadedClassCount classes loaded now
         * @param totalLoadedClassCount classes loaded since start
         * @param gc the garbage collectors
         * @param pools the memory pools
         */
        public Metrics(long uptimeMs, long heapUsed, long heapMax, long nonHeapUsed, long nonHeapMax, double processCpuLoad, double systemCpuLoad, int availableProcessors, int threadCount, int daemonThreadCount, int peakThreadCount, int loadedClassCount, long totalLoadedClassCount, List<Gc> gc, List<Pool> pools)
        {
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

    /** One garbage collector: its name, how many collections it ran and their total time. */
    @Getter
    final class Gc
    {
        private final String name;
        private final long count;
        private final long timeMs;

        /**
         * Creates a collector entry.
         *
         * @param name the collector's name
         * @param count how many collections it ran
         * @param timeMs their total time, in milliseconds
         */
        public Gc(String name, long count, long timeMs)
        {
            this.name = name;
            this.count = count;
            this.timeMs = timeMs;
        }
    }

    /** One memory pool: its name and bytes used and allowed. */
    @Getter
    final class Pool
    {
        private final String name;
        private final long used;
        private final long max;

        /**
         * Creates a pool entry.
         *
         * @param name the pool's name
         * @param used bytes in use
         * @param max the limit in bytes
         */
        public Pool(String name, long used, long max)
        {
            this.name = name;
            this.used = used;
            this.max = max;
        }
    }

    /** One thread's id, name, state and stack frames, innermost first. */
    @Getter
    final class ThreadDump
    {
        private final long id;
        private final String name;
        private final String state;
        private final List<Frame> frames;

        /**
         * Creates a thread entry.
         *
         * @param id the thread id
         * @param name the thread's name
         * @param state the Thread.State name
         * @param frames the stack frames, innermost first
         */
        public ThreadDump(long id, String name, String state, List<Frame> frames)
        {
            this.id = id;
            this.name = name;
            this.state = state;
            this.frames = frames;
        }
    }

    /** One stack frame: class, method, source file and line. */
    @Getter
    final class Frame
    {
        private final String className;
        private final String method;
        private final String file;
        private final int line;

        /**
         * Creates a frame.
         *
         * @param className the declaring class
         * @param method the method's name
         * @param file the source file, when known
         * @param line the source line, when known
         */
        public Frame(String className, String method, String file, int line)
        {
            this.className = className;
            this.method = method;
            this.file = file;
            this.line = line;
        }
    }

    /** One deadlock cycle as readable edges. */
    @Getter
    final class Deadlock
    {
        /** Each edge reads "thread waits on monitor class held by owner thread". */
        private final List<String> edges;

        /**
         * Creates a cycle.
         *
         * @param edges the cycle's edges as text
         */
        public Deadlock(List<String> edges)
        {
            this.edges = edges;
        }
    }

    /** One static field with its name, type descriptor and current value as text. */
    @Getter
    final class StaticField
    {
        private final String name;
        private final String type;
        private final String value;

        /**
         * Creates a static field entry.
         *
         * @param name the field's name
         * @param type the field's type descriptor
         * @param value the current value as text
         */
        public StaticField(String name, String type, String value)
        {
            this.name = name;
            this.type = type;
            this.value = value;
        }
    }

    /** The outcome of eval: whether it compiled and ran, and its output or error text. */
    @Getter
    final class EvalResult
    {
        private final boolean success;
        private final String output;

        /**
         * Creates a result.
         *
         * @param success whether the code compiled and ran
         * @param output the output, or the failure text
         */
        public EvalResult(boolean success, String output)
        {
            this.success = success;
            this.output = output;
        }
    }

    /** One page of a class's live instances plus the total count. */
    @Getter
    final class Instances
    {
        private final String className;
        private final int total;
        private final List<InstanceRef> page;

        /**
         * Creates a page.
         *
         * @param className the class's internal name
         * @param total how many instances the snapshot holds
         * @param page the instances in the requested range
         */
        public Instances(String className, int total, List<InstanceRef> page)
        {
            this.className = className;
            this.total = total;
            this.page = page;
        }
    }

    /** One live instance: its object id and a short label. */
    @Getter
    final class InstanceRef
    {
        private final String id;
        private final String label;

        /**
         * Creates an instance reference.
         *
         * @param id the object id, hexadecimal with 0x
         * @param label a short description of the instance
         */
        public InstanceRef(String id, String label)
        {
            this.id = id;
            this.label = label;
        }
    }

    /** One decoded instance: its id, class and field values. */
    @Getter
    final class InstanceInfo
    {
        private final String id;
        private final String className;
        private final List<Field> fields;

        /**
         * Creates the decoded instance.
         *
         * @param id the object id, hexadecimal with 0x
         * @param className the instance's class
         * @param fields its field values
         */
        public InstanceInfo(String id, String className, List<Field> fields)
        {
            this.id = id;
            this.className = className;
            this.fields = fields;
        }
    }

    /** One field of a decoded instance: name, type, value as text, and the referenced object's id for a reference. */
    @Getter
    final class Field
    {
        private final String name;
        private final String type;
        private final String value;
        /** The referenced instance's id, for passing to instance, when the type is ref and the value is not null; otherwise null. */
        private final String refId;

        /**
         * Creates a field entry.
         *
         * @param name the field's name
         * @param type the field's type, "ref" for a reference
         * @param value the value as text
         * @param refId the referenced instance's id, or null
         */
        public Field(String name, String type, String value, String refId)
        {
            this.name = name;
            this.type = type;
            this.value = value;
            this.refId = refId;
        }
    }
}
