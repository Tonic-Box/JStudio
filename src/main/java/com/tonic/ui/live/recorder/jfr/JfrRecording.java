package com.tonic.ui.live.recorder.jfr;

import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Parses a {@code .jfr} recording (one streaming pass over {@link RecordingFile}) into the aggregates the
 * analysis window renders: an event-type overview, a CPU call tree + hot methods, an allocation call tree +
 * by-type breakdown, lock contention, and thrown exceptions. UI-free.
 *
 * <p>Field access is guarded by {@link RecordedEvent#hasField} so it stays robust across JDK versions whose
 * JFR event schemas differ (e.g. {@code jdk.ObjectAllocationSample} on 16+ vs the TLAB events on 11).
 */
@Getter
public final class JfrRecording {

    private final Duration duration;
    private final long totalEvents;
    /**
     * -- GETTER --
     * Event-type name -> count, sorted by name.
     */
    private final Map<String, Long> eventCounts;

    private final CallTreeNode cpuTree;
    private final long cpuSamples;
    private final List<MethodStat> hotMethods;

    private final CallTreeNode allocTree;
    private final long allocBytes;
    private final List<TypeStat> allocByType;

    private final CallTreeNode lockTree;
    private final long lockNanos;
    private final List<LockStat> lockContention;

    private final List<ExceptionStat> exceptions;
    private final long exceptionCount;

    private JfrRecording(Builder b) {
        this.duration = b.duration();
        this.totalEvents = b.totalEvents;
        this.eventCounts = b.eventCounts;
        this.cpuTree = b.cpuTree;
        this.cpuSamples = b.cpuSamples;
        this.hotMethods = b.hotMethods();
        this.allocTree = b.allocTree;
        this.allocBytes = b.allocBytes;
        this.allocByType = b.allocByType();
        this.lockTree = b.lockTree;
        this.lockNanos = b.lockNanos;
        this.lockContention = b.lockContention();
        this.exceptions = b.exceptions();
        this.exceptionCount = b.exceptionCount;
    }

    /** Reads and aggregates {@code jfr} in a single pass. */
    public static JfrRecording parse(File jfr) throws IOException {
        Builder b = new Builder();
        try (RecordingFile file = new RecordingFile(jfr.toPath())) {
            while (file.hasMoreEvents()) {
                b.accept(file.readEvent());
            }
        }
        return new JfrRecording(b);
    }

    public boolean hasCpu() {
        return cpuSamples > 0;
    }

    public boolean hasAllocations() {
        return allocBytes > 0 || !allocByType.isEmpty();
    }

    public boolean hasLocks() {
        return !lockContention.isEmpty();
    }

    public boolean hasExceptions() {
        return exceptionCount > 0;
    }

    // ---- parsing ------------------------------------------------------------------------------------

    /** Mutable accumulator used during the single parse pass. */
    private static final class Builder {
        private long totalEvents;
        private final Map<String, Long> eventCounts = new TreeMap<>();
        private Instant first;
        private Instant last;

        private final CallTreeNode cpuTree = new CallTreeNode(null);
        private long cpuSamples;
        private final Map<String, MethodStat> methods = new LinkedHashMap<>();

        private final CallTreeNode allocTree = new CallTreeNode(null);
        private long allocBytes;
        private final Map<String, TypeStat> allocTypes = new LinkedHashMap<>();

        private final CallTreeNode lockTree = new CallTreeNode(null);
        private long lockNanos;
        private final Map<String, LockStat> locks = new LinkedHashMap<>();

        private final Map<String, ExceptionStat> exceptionTypes = new LinkedHashMap<>();
        private long exceptionCount;

        void accept(RecordedEvent event) {
            totalEvents++;
            String name = event.getEventType().getName();
            eventCounts.merge(name, 1L, Long::sum);
            track(event.getStartTime());

            switch (name) {
                case "jdk.ExecutionSample":
                case "jdk.NativeMethodSample":
                    cpuSamples++;
                    addStack(cpuTree, event.getStackTrace(), 1);
                    accumulateMethods(event.getStackTrace(), 1);
                    break;
                case "jdk.ObjectAllocationSample":
                case "jdk.ObjectAllocationInNewTLAB":
                case "jdk.ObjectAllocationOutsideTLAB": {
                    long bytes = allocationBytes(event);
                    allocBytes += bytes;
                    addStack(allocTree, event.getStackTrace(), bytes);
                    String type = className(event, "objectClass");
                    if (type != null) {
                        allocTypes.computeIfAbsent(type, TypeStat::new).add(bytes);
                    }
                    break;
                }
                case "jdk.JavaMonitorEnter":
                case "jdk.JavaMonitorWait":
                case "jdk.ThreadPark": {
                    long nanos = event.getDuration() != null ? event.getDuration().toNanos() : 0;
                    lockNanos += nanos;
                    addStack(lockTree, event.getStackTrace(), nanos);
                    String monitor = className(event, "monitorClass");
                    if (monitor == null) {
                        monitor = className(event, "parkedClass");
                    }
                    if (monitor != null) {
                        locks.computeIfAbsent(monitor, LockStat::new).add(nanos);
                    }
                    break;
                }
                case "jdk.JavaExceptionThrow":
                case "jdk.JavaErrorThrow": {
                    exceptionCount++;
                    String type = className(event, "thrownClass");
                    if (type == null) {
                        type = "(unknown)";
                    }
                    exceptionTypes.computeIfAbsent(type, ExceptionStat::new).add();
                    break;
                }
                default:
                    break;
            }
        }

        private void track(Instant time) {
            if (time == null) {
                return;
            }
            if (first == null || time.isBefore(first)) {
                first = time;
            }
            if (last == null || time.isAfter(last)) {
                last = time;
            }
        }

        Duration duration() {
            return first != null && last != null ? Duration.between(first, last) : Duration.ZERO;
        }

        /** Adds an event's stack (outermost-first) to {@code root}, weighting every node on the path. */
        private static void addStack(CallTreeNode root, RecordedStackTrace stack, long weight) {
            root.addTotal(weight);
            if (stack == null) {
                return;
            }
            List<RecordedFrame> frames = stack.getFrames();
            CallTreeNode node = root;
            for (int i = frames.size() - 1; i >= 0; i--) {
                FrameKey key = frameKey(frames.get(i));
                if (key == null) {
                    continue;
                }
                node = node.child(key);
                node.addTotal(weight);
            }
            node.addSelf(weight);
        }

        /** Adds {@code weight} as self to the innermost frame's method and as total to every distinct method. */
        private void accumulateMethods(RecordedStackTrace stack, long weight) {
            if (stack == null) {
                return;
            }
            List<RecordedFrame> frames = stack.getFrames();
            boolean selfAssigned = false;
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (RecordedFrame frame : frames) {
                FrameKey key = frameKey(frame);
                if (key == null) {
                    continue;
                }
                String methodKey = key.getClassInternal() + '#' + key.getMethod();
                MethodStat stat = methods.computeIfAbsent(methodKey, k -> new MethodStat(key));
                if (seen.add(methodKey)) {
                    stat.addTotal(weight);
                }
                if (!selfAssigned) {
                    stat.addSelf(weight);
                    selfAssigned = true;
                }
            }
        }

        private static FrameKey frameKey(RecordedFrame frame) {
            if (!frame.isJavaFrame()) {
                return null;
            }
            RecordedMethod method = frame.getMethod();
            if (method == null || method.getType() == null) {
                return null;
            }
            String dotted = method.getType().getName();
            if (dotted == null) {
                return null;
            }
            return new FrameKey(dotted.replace('.', '/'), method.getName(), frame.getLineNumber());
        }

        private static long allocationBytes(RecordedEvent event) {
            if (event.hasField("allocationSize")) {
                return event.getLong("allocationSize");
            }
            if (event.hasField("weight")) {
                return event.getLong("weight");
            }
            return 0;
        }

        private static String className(RecordedEvent event, String field) {
            if (!event.hasField(field)) {
                return null;
            }
            Object value = event.getValue(field);
            return value instanceof RecordedClass ? ((RecordedClass) value).getName() : null;
        }

        List<MethodStat> hotMethods() {
            List<MethodStat> list = new ArrayList<>(methods.values());
            list.sort(Comparator.comparingLong(MethodStat::getSelf).reversed());
            return list;
        }

        List<TypeStat> allocByType() {
            List<TypeStat> list = new ArrayList<>(allocTypes.values());
            list.sort(Comparator.comparingLong(TypeStat::getBytes).reversed());
            return list;
        }

        List<LockStat> lockContention() {
            List<LockStat> list = new ArrayList<>(locks.values());
            list.sort(Comparator.comparingLong(LockStat::getNanos).reversed());
            return list;
        }

        List<ExceptionStat> exceptions() {
            List<ExceptionStat> list = new ArrayList<>(exceptionTypes.values());
            list.sort(Comparator.comparingLong(ExceptionStat::getCount).reversed());
            return list;
        }
    }

    // ---- stat rows ----------------------------------------------------------------------------------

    /** A method's self vs total weight (CPU samples). Carries a {@link FrameKey} for source navigation. */
    @Getter
    public static final class MethodStat {
        private final FrameKey frame;
        private long self;
        private long total;

        MethodStat(FrameKey frame) {
            this.frame = frame;
        }

        void addSelf(long w) {
            self += w;
        }

        void addTotal(long w) {
            total += w;
        }

    }

    /** Allocation totals for one allocated type. */
    @Getter
    public static final class TypeStat {
        private final String className;
        private long count;
        private long bytes;

        TypeStat(String className) {
            this.className = className;
        }

        void add(long b) {
            count++;
            bytes += b;
        }

    }

    /** Contention totals for one monitor/parked type. */
    @Getter
    public static final class LockStat {
        private final String className;
        private long count;
        private long nanos;

        LockStat(String className) {
            this.className = className;
        }

        void add(long n) {
            count++;
            nanos += n;
        }

    }

    /** Throw count for one exception type. */
    @Getter
    public static final class ExceptionStat {
        private final String className;
        private long count;

        ExceptionStat(String className) {
            this.className = className;
        }

        void add() {
            count++;
        }

    }
}
