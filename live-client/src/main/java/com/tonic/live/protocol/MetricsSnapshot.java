package com.tonic.live.protocol;

import java.util.List;

/**
 * A point-in-time snapshot of a target JVM's runtime metrics, read from its JMX MXBeans by the agent
 * ({@link LiveProtocol#MSG_GET_METRICS}). Cumulative counters (GC counts/times, total/unloaded classes) are
 * differenced client-side over successive snapshots to derive rates; CPU loads are instantaneous (0..1, or
 * -1 when the JVM cannot report them).
 */
public final class MetricsSnapshot {

    public final long uptimeMs;

    public final long heapUsed;
    public final long heapCommitted;
    public final long heapMax;
    public final long nonHeapUsed;
    public final long nonHeapCommitted;
    public final long nonHeapMax;

    /** Process / whole-system CPU load in [0, 1], or -1 if unavailable on this JVM. */
    public final double processCpuLoad;
    public final double systemCpuLoad;
    public final int availableProcessors;

    public final int threadCount;
    public final int daemonThreadCount;
    public final int peakThreadCount;
    public final long totalStartedThreadCount;

    public final int loadedClassCount;
    public final long totalLoadedClassCount;
    public final long unloadedClassCount;

    public final List<MemoryPool> memoryPools;
    public final List<GcStat> gcStats;

    public MetricsSnapshot(long uptimeMs,
                           long heapUsed, long heapCommitted, long heapMax,
                           long nonHeapUsed, long nonHeapCommitted, long nonHeapMax,
                           double processCpuLoad, double systemCpuLoad, int availableProcessors,
                           int threadCount, int daemonThreadCount, int peakThreadCount, long totalStartedThreadCount,
                           int loadedClassCount, long totalLoadedClassCount, long unloadedClassCount,
                           List<MemoryPool> memoryPools, List<GcStat> gcStats) {
        this.uptimeMs = uptimeMs;
        this.heapUsed = heapUsed;
        this.heapCommitted = heapCommitted;
        this.heapMax = heapMax;
        this.nonHeapUsed = nonHeapUsed;
        this.nonHeapCommitted = nonHeapCommitted;
        this.nonHeapMax = nonHeapMax;
        this.processCpuLoad = processCpuLoad;
        this.systemCpuLoad = systemCpuLoad;
        this.availableProcessors = availableProcessors;
        this.threadCount = threadCount;
        this.daemonThreadCount = daemonThreadCount;
        this.peakThreadCount = peakThreadCount;
        this.totalStartedThreadCount = totalStartedThreadCount;
        this.loadedClassCount = loadedClassCount;
        this.totalLoadedClassCount = totalLoadedClassCount;
        this.unloadedClassCount = unloadedClassCount;
        this.memoryPools = memoryPools;
        this.gcStats = gcStats;
    }

    /** A memory pool's usage (e.g. {@code Metaspace}, {@code G1 Eden Space}, {@code Code Cache}). */
    public static final class MemoryPool {
        public final String name;
        public final long used;
        public final long committed;
        public final long max;

        public MemoryPool(String name, long used, long committed, long max) {
            this.name = name;
            this.used = used;
            this.committed = committed;
            this.max = max;
        }
    }

    /** A garbage collector's cumulative collection count and accumulated pause time (ms). */
    public static final class GcStat {
        public final String name;
        public final long collectionCount;
        public final long collectionTimeMs;

        public GcStat(String name, long collectionCount, long collectionTimeMs) {
            this.name = name;
            this.collectionCount = collectionCount;
            this.collectionTimeMs = collectionTimeMs;
        }
    }
}
