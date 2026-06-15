package com.tonic.ui.live;

import com.tonic.live.LiveSession;
import com.tonic.ui.live.heap.HprofSnapshot;

import java.io.File;
import java.io.IOException;

/**
 * Holds the single current heap-dump snapshot shared across all editor tabs. A heap dump is the whole heap,
 * so one parsed {@link HprofSnapshot} serves every class: switching tabs re-filters instantly, and only an
 * explicit refresh (or the first entry into the Live Instances view) takes a fresh dump.
 *
 * <p>At most one HPROF file exists on disk at a time - taking a new snapshot closes and deletes the old one.
 * {@link #clear()} (called on detach) disposes the current snapshot.
 */
public final class LiveHeapService {

    private static final LiveHeapService INSTANCE = new LiveHeapService();

    private HprofSnapshot snapshot;

    private LiveHeapService() {
    }

    public static LiveHeapService get() {
        return INSTANCE;
    }

    public synchronized HprofSnapshot getSnapshot() {
        return snapshot;
    }

    /** Take a fresh heap dump from the target, parse it, and replace any previous snapshot. Call off the EDT. */
    public HprofSnapshot snapshot(LiveSession session) throws IOException {
        String path = session.heapDump();
        HprofSnapshot fresh = new HprofSnapshot(new File(path));
        HprofSnapshot old;
        synchronized (this) {
            old = snapshot;
            snapshot = fresh;
        }
        if (old != null) {
            old.close();
        }
        return fresh;
    }

    /** Return the current snapshot, taking one only if none exists. Call off the EDT. */
    public HprofSnapshot ensureSnapshot(LiveSession session) throws IOException {
        synchronized (this) {
            if (snapshot != null) {
                return snapshot;
            }
        }
        return snapshot(session);
    }

    /** Dispose the current snapshot (close its file handle + delete the dump). */
    public synchronized void clear() {
        if (snapshot != null) {
            snapshot.close();
            snapshot = null;
        }
    }
}
