package com.tonic.live.protocol;

import java.util.List;

/** A point-in-time snapshot of a target JVM's runtime metrics from its MXBeans; cumulative counters are differenced across snapshots for rates, and CPU loads are 0 to 1, or -1 when unavailable. */
public final class MetricsSnapshot
{

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

    /**
     * Creates a snapshot.
     *
     * @param uptimeMs the JVM uptime in milliseconds
     * @param heapUsed the heap bytes in use
     * @param heapCommitted the heap bytes committed
     * @param heapMax the heap limit in bytes, or -1 when undefined
     * @param nonHeapUsed the non-heap bytes in use
     * @param nonHeapCommitted the non-heap bytes committed
     * @param nonHeapMax the non-heap limit in bytes, or -1 when undefined
     * @param processCpuLoad the process CPU load from 0 to 1, or -1 when unavailable
     * @param systemCpuLoad the whole-system CPU load from 0 to 1, or -1 when unavailable
     * @param availableProcessors the processor count
     * @param threadCount the live thread count
     * @param daemonThreadCount the live daemon thread count
     * @param peakThreadCount the peak live thread count
     * @param totalStartedThreadCount the threads started since JVM start
     * @param loadedClassCount the classes currently loaded
     * @param totalLoadedClassCount the classes loaded since JVM start
     * @param unloadedClassCount the classes unloaded since JVM start
     * @param memoryPools the per-pool usage
     * @param gcStats the per-collector statistics
     */
    public MetricsSnapshot(long uptimeMs, long heapUsed, long heapCommitted, long heapMax, long nonHeapUsed, long nonHeapCommitted, long nonHeapMax, double processCpuLoad, double systemCpuLoad, int availableProcessors, int threadCount, int daemonThreadCount, int peakThreadCount, long totalStartedThreadCount, int loadedClassCount, long totalLoadedClassCount, long unloadedClassCount, List<MemoryPool> memoryPools, List<GcStat> gcStats)
    {
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

    /** A memory pool's usage, such as Metaspace, an Eden space, or the code cache. */
    public static final class MemoryPool
    {
        public final String name;
        public final long used;
        public final long committed;
        public final long max;

        /**
         * Creates a pool entry.
         *
         * @param name the pool name
         * @param used the bytes in use
         * @param committed the bytes committed
         * @param max the limit in bytes, or -1 when undefined
         */
        public MemoryPool(String name, long used, long committed, long max)
        {
            this.name = name;
            this.used = used;
            this.committed = committed;
            this.max = max;
        }
    }

    /** A garbage collector's cumulative collection count and accumulated collection time. */
    public static final class GcStat
    {
        public final String name;
        public final long collectionCount;
        public final long collectionTimeMs;

        /**
         * Creates a collector entry.
         *
         * @param name the collector name
         * @param collectionCount the collections so far
         * @param collectionTimeMs the accumulated collection time in milliseconds
         */
        public GcStat(String name, long collectionCount, long collectionTimeMs)
        {
            this.name = name;
            this.collectionCount = collectionCount;
            this.collectionTimeMs = collectionTimeMs;
        }
    }
}
