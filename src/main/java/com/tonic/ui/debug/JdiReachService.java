package com.tonic.ui.debug;

import com.tonic.live.LiveSession;
import com.tonic.live.protocol.LiveInstance;
import com.tonic.live.protocol.ScanPage;
import com.tonic.ui.live.LiveAttachService;

import java.io.IOException;
import java.util.List;

/**
 * Routes the Live Instances view (and, in later phases, the value scanner) through JDI-backed object
 * enumeration when both a JDI debugger and the instrument agent are attached, falling back to the agent's
 * heap-walk otherwise. JDI parks a complete object set into the agent's dropbox over JDWP; the agent then
 * consumes it with its fast in-process reflection. This is the single decision point - callers go through it
 * instead of the {@link LiveSession} directly.
 */
public final class JdiReachService {

    private static final JdiReachService INSTANCE = new JdiReachService();

    /** Upper bound on objects JDI parks per scan (stack roots, or a scoped class's instances). */
    private static final int JDI_OBJECT_CAP = 200_000;

    private JdiReachService() {
    }

    public static JdiReachService getInstance() {
        return INSTANCE;
    }

    /** True when JDI control + the agent are both attached, so the JDI-backed reach path is usable. */
    public boolean isJdiBacked() {
        return DebugManager.getInstance().isConnected() && LiveAttachService.getInstance().isAttached();
    }

    /**
     * Lists live instances of {@code internalName} - JDI-backed (complete enumeration) when available, else the
     * agent's heap-walk. Safe to call off the EDT (it performs the JDWP park + the agent round-trip).
     */
    public List<LiveInstance> listInstances(LiveSession session, String internalName, int max, int maxVisited)
            throws IOException {
        if (isJdiBacked()) {
            int parked = DebugManager.getInstance().parkInstances(internalName.replace('/', '.'), max);
            if (parked >= 0) {
                return session.listInstances(internalName, max, maxVisited, true);
            }
        }
        return session.listInstances(internalName, max, maxVisited, false);
    }

    /**
     * Runs a first scan with JDI-backed reach when available. With no {@code scopeClass}, JDI harvests the
     * target's stack-frame-held objects as EXTRA roots (fuller reach). With a {@code scopeClass}, JDI enumerates
     * that class's complete instance set and the scan runs over ONLY those (exhaustive per-class). Falls back to
     * the agent-only walk when JDI isn't attached or parking fails. Safe to call off the EDT.
     */
    public ScanPage scanFirst(LiveSession session, int valueType, int scanKind, String value, String value2,
                              String pkgFilter, boolean userClassesOnly, int maxVisited, int maxMatches, int limit,
                              String scopeClass) throws IOException {
        if (isJdiBacked()) {
            boolean scoped = scopeClass != null && !scopeClass.trim().isEmpty();
            int parked = scoped
                    ? DebugManager.getInstance().parkInstances(scopeClass.trim().replace('/', '.'), JDI_OBJECT_CAP)
                    : DebugManager.getInstance().parkStackRoots(JDI_OBJECT_CAP);
            if (parked >= 0) {
                return session.scanFirst(valueType, scanKind, value, value2, pkgFilter, userClassesOnly,
                        maxVisited, maxMatches, limit, true, scoped);
            }
        }
        return session.scanFirst(valueType, scanKind, value, value2, pkgFilter, userClassesOnly,
                maxVisited, maxMatches, limit, false, false);
    }
}
