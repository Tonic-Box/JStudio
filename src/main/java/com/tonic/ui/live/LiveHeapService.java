package com.tonic.ui.live;

import com.tonic.live.LiveSession;
import com.tonic.ui.live.heap.HprofSnapshot;

import java.io.File;
import java.io.IOException;

/** The single current heap-dump snapshot shared by every editor tab; taking a new one closes and deletes the old dump. */
public final class LiveHeapService
{

    private static final LiveHeapService INSTANCE = new LiveHeapService();

    private HprofSnapshot snapshot;

    private LiveHeapService()
    {
    }

    /** @return the shared instance */
    public static LiveHeapService get()
    {
        return INSTANCE;
    }

    /** @return the current snapshot, or null if none has been taken */
    public synchronized HprofSnapshot getSnapshot()
    {
        return snapshot;
    }

    /**
     * Takes a fresh heap dump from the target, parses it, and replaces the previous snapshot; call off the EDT.
     *
     * @param session the session to dump
     * @return the new snapshot
     * @throws IOException if the dump or its parsing fails
     */
    public HprofSnapshot snapshot(LiveSession session) throws IOException
    {
        String path = session.heapDump();
        HprofSnapshot fresh = new HprofSnapshot(new File(path));
        HprofSnapshot old;
        synchronized (this)
        {
            old = snapshot;
            snapshot = fresh;
        }
        if (old != null)
        {
            old.close();
        }
        return fresh;
    }

    /**
     * Returns the current snapshot, taking one only if none exists; call off the EDT.
     *
     * @param session the session to dump if no snapshot exists
     * @return the current or newly taken snapshot
     * @throws IOException if a needed dump or its parsing fails
     */
    public HprofSnapshot ensureSnapshot(LiveSession session) throws IOException
    {
        synchronized (this)
        {
            if (snapshot != null)
            {
                return snapshot;
            }
        }
        return snapshot(session);
    }

    /** Closes the current snapshot and deletes its dump file. */
    public synchronized void clear()
    {
        if (snapshot != null)
        {
            snapshot.close();
            snapshot = null;
        }
    }
}
