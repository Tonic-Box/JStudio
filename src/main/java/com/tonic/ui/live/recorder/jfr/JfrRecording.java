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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** The aggregates of one JFR recording parsed in a single pass: event counts, CPU and allocation call trees, lock contention and exceptions. */
@Getter
public final class JfrRecording
{

    private final Duration duration;
    private final long totalEvents;
    /** The count of events per event type name, sorted by name. */
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

    private JfrRecording(Builder b)
    {
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

    /**
     * Reads and aggregates a recording in a single pass.
     *
     * @param jfr the recording file
     * @return the aggregates
     * @throws IOException if the file cannot be read as a recording
     */
    public static JfrRecording parse(File jfr) throws IOException
    {
        Builder b = new Builder();
        try (RecordingFile file = new RecordingFile(jfr.toPath()))
        {
            while (file.hasMoreEvents())
            {
                b.accept(file.readEvent());
            }
        }
        return new JfrRecording(b);
    }

    /**
     * Tells whether the recording has CPU samples.
     *
     * @return true if any were recorded
     */
    public boolean hasCpu()
    {
        return cpuSamples > 0;
    }

    /**
     * Tells whether the recording has allocation data.
     *
     * @return true if any allocated bytes or types were recorded
     */
    public boolean hasAllocations()
    {
        return allocBytes > 0 || !allocByType.isEmpty();
    }

    /**
     * Tells whether the recording has lock contention.
     *
     * @return true if any contended lock was recorded
     */
    public boolean hasLocks()
    {
        return !lockContention.isEmpty();
    }

    /**
     * Tells whether the recording has thrown exceptions.
     *
     * @return true if any were recorded
     */
    public boolean hasExceptions()
    {
        return exceptionCount > 0;
    }

    private static final class Builder
    {
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

        void accept(RecordedEvent event)
        {
            totalEvents++;
            String name = event.getEventType().getName();
            eventCounts.merge(name, 1L, Long::sum);
            track(event.getStartTime());

            switch (name)
            {
                case "jdk.ExecutionSample":
                case "jdk.NativeMethodSample":
                    cpuSamples++;
                    addStack(cpuTree, event.getStackTrace(), 1);
                    accumulateMethods(event.getStackTrace(), 1);
                    break;
                case "jdk.ObjectAllocationSample":
                case "jdk.ObjectAllocationInNewTLAB":
                case "jdk.ObjectAllocationOutsideTLAB":
                {
                    long bytes = allocationBytes(event);
                    allocBytes += bytes;
                    addStack(allocTree, event.getStackTrace(), bytes);
                    String type = className(event, "objectClass");
                    if (type != null)
                    {
                        allocTypes.computeIfAbsent(type, TypeStat::new).add(bytes);
                    }
                    break;
                }
                case "jdk.JavaMonitorEnter":
                case "jdk.JavaMonitorWait":
                case "jdk.ThreadPark":
                {
                    long nanos = event.getDuration() != null ? event.getDuration().toNanos() : 0;
                    lockNanos += nanos;
                    addStack(lockTree, event.getStackTrace(), nanos);
                    String monitor = className(event, "monitorClass");
                    if (monitor == null)
                    {
                        monitor = className(event, "parkedClass");
                    }
                    if (monitor != null)
                    {
                        locks.computeIfAbsent(monitor, LockStat::new).add(nanos);
                    }
                    break;
                }
                case "jdk.JavaExceptionThrow":
                case "jdk.JavaErrorThrow":
                {
                    exceptionCount++;
                    String type = className(event, "thrownClass");
                    if (type == null)
                    {
                        type = "(unknown)";
                    }
                    exceptionTypes.computeIfAbsent(type, ExceptionStat::new).add();
                    break;
                }
                default:
                    break;
            }
        }

        private void track(Instant time)
        {
            if (time == null)
            {
                return;
            }
            if (first == null || time.isBefore(first))
            {
                first = time;
            }
            if (last == null || time.isAfter(last))
            {
                last = time;
            }
        }

        Duration duration()
        {
            return first != null && last != null ? Duration.between(first, last) : Duration.ZERO;
        }

        private static void addStack(CallTreeNode root, RecordedStackTrace stack, long weight)
        {
            root.addTotal(weight);
            if (stack == null)
            {
                return;
            }
            List<RecordedFrame> frames = stack.getFrames();
            CallTreeNode node = root;
            for (int i = frames.size() - 1; i >= 0; i--)
            {
                FrameKey key = frameKey(frames.get(i));
                if (key == null)
                {
                    continue;
                }
                node = node.child(key);
                node.addTotal(weight);
            }
            node.addSelf(weight);
        }

        private void accumulateMethods(RecordedStackTrace stack, long weight)
        {
            if (stack == null)
            {
                return;
            }
            List<RecordedFrame> frames = stack.getFrames();
            boolean selfAssigned = false;
            Set<String> seen = new HashSet<>();
            for (RecordedFrame frame : frames)
            {
                FrameKey key = frameKey(frame);
                if (key == null)
                {
                    continue;
                }
                String methodKey = key.getClassInternal() + '#' + key.getMethod();
                MethodStat stat = methods.computeIfAbsent(methodKey, k -> new MethodStat(key));
                if (seen.add(methodKey))
                {
                    stat.addTotal(weight);
                }
                if (!selfAssigned)
                {
                    stat.addSelf(weight);
                    selfAssigned = true;
                }
            }
        }

        private static FrameKey frameKey(RecordedFrame frame)
        {
            if (!frame.isJavaFrame())
            {
                return null;
            }
            RecordedMethod method = frame.getMethod();
            if (method == null || method.getType() == null)
            {
                return null;
            }
            String dotted = method.getType().getName();
            return new FrameKey(dotted.replace('.', '/'), method.getName(), frame.getLineNumber());
        }

        private static long allocationBytes(RecordedEvent event)
        {
            if (event.hasField("allocationSize"))
            {
                return event.getLong("allocationSize");
            }
            if (event.hasField("weight"))
            {
                return event.getLong("weight");
            }
            return 0;
        }

        private static String className(RecordedEvent event, String field)
        {
            if (!event.hasField(field))
            {
                return null;
            }
            Object value = event.getValue(field);
            return value instanceof RecordedClass ? ((RecordedClass) value).getName() : null;
        }

        List<MethodStat> hotMethods()
        {
            List<MethodStat> list = new ArrayList<>(methods.values());
            list.sort(Comparator.comparingLong(MethodStat::getSelf).reversed());
            return list;
        }

        List<TypeStat> allocByType()
        {
            List<TypeStat> list = new ArrayList<>(allocTypes.values());
            list.sort(Comparator.comparingLong(TypeStat::getBytes).reversed());
            return list;
        }

        List<LockStat> lockContention()
        {
            List<LockStat> list = new ArrayList<>(locks.values());
            list.sort(Comparator.comparingLong(LockStat::getNanos).reversed());
            return list;
        }

        List<ExceptionStat> exceptions()
        {
            List<ExceptionStat> list = new ArrayList<>(exceptionTypes.values());
            list.sort(Comparator.comparingLong(ExceptionStat::getCount).reversed());
            return list;
        }
    }

    /** A method's self and total CPU samples, with its frame for source navigation. */
    @Getter
    public static final class MethodStat
    {
        private final FrameKey frame;
        private long self;
        private long total;

        MethodStat(FrameKey frame)
        {
            this.frame = frame;
        }

        void addSelf(long w)
        {
            self += w;
        }

        void addTotal(long w)
        {
            total += w;
        }

    }

    /** Allocation totals for one allocated type. */
    @Getter
    public static final class TypeStat
    {
        private final String className;
        private long count;
        private long bytes;

        TypeStat(String className)
        {
            this.className = className;
        }

        void add(long b)
        {
            count++;
            bytes += b;
        }

    }

    /** Contention totals for one monitor/parked type. */
    @Getter
    public static final class LockStat
    {
        private final String className;
        private long count;
        private long nanos;

        LockStat(String className)
        {
            this.className = className;
        }

        void add(long n)
        {
            count++;
            nanos += n;
        }

    }

    /** Throw count for one exception type. */
    @Getter
    public static final class ExceptionStat
    {
        private final String className;
        private long count;

        ExceptionStat(String className)
        {
            this.className = className;
        }

        void add()
        {
            count++;
        }

    }
}
