package com.tonic.ui.debug;

import com.tonic.live.LiveSession;
import com.tonic.live.protocol.LiveInstance;
import com.tonic.live.protocol.ScanPage;
import com.tonic.ui.live.LiveAttachService;

import java.io.IOException;
import java.util.List;

/** The single place that chooses between JDI-parked object sets and the agent's own heap walk for instance listing and value scans. */
public final class JdiReachService
{

    private static final JdiReachService INSTANCE = new JdiReachService();

    private static final int JDI_OBJECT_CAP = 200_000;

    private JdiReachService()
    {
    }

    /** @return the shared service */
    public static JdiReachService getInstance()
    {
        return INSTANCE;
    }

    /**
     * Tells whether the JDI-backed reach path is usable.
     *
     * @return true when both the JDI debugger and the agent are attached
     */
    public boolean isJdiBacked()
    {
        return DebugManager.getInstance().isConnected() && LiveAttachService.getInstance().isAttached();
    }

    /**
     * Lists live instances of a class, from a complete JDI enumeration when available, else from the agent's heap walk; blocks, so call it off the EDT.
     *
     * @param session the attached agent session
     * @param internalName the class's internal name, with slashes
     * @param max the most instances to return
     * @param maxVisited the most objects the heap walk may visit
     * @return the instances found
     * @throws IOException if the agent round-trip fails
     */
    public List<LiveInstance> listInstances(LiveSession session, String internalName, int max, int maxVisited)
            throws IOException
    {
        if (isJdiBacked())
        {
            int parked = DebugManager.getInstance().parkInstances(internalName.replace('/', '.'), max);
            if (parked >= 0)
            {
                return session.listInstances(internalName, max, maxVisited, true);
            }
        }
        return session.listInstances(internalName, max, maxVisited, false);
    }

    /**
     * Runs a first value scan, using JDI reach when available and the agent-only walk otherwise; blocks, so call it off the EDT.
     *
     * @param session the attached agent session
     * @param valueType the kind of value to match
     * @param scanKind how to compare against the value
     * @param value the value to match
     * @param value2 the upper bound for range scans
     * @param pkgFilter the package prefix to limit the scan to
     * @param userClassesOnly whether to skip JDK classes
     * @param maxVisited the most objects the walk may visit
     * @param maxMatches the most matches to retain
     * @param limit the page size of the returned results
     * @param scopeClass a class whose instances alone are scanned, or null or blank to add stack-held objects as extra roots
     * @return the first page of matches
     * @throws IOException if the agent round-trip fails
     */
    public ScanPage scanFirst(LiveSession session, int valueType, int scanKind, String value, String value2, String pkgFilter, boolean userClassesOnly, int maxVisited, int maxMatches, int limit, String scopeClass) throws IOException
    {
        if (isJdiBacked())
        {
            boolean scoped = scopeClass != null && !scopeClass.trim().isEmpty();
            int parked = scoped
                    ? DebugManager.getInstance().parkInstances(scopeClass.trim().replace('/', '.'), JDI_OBJECT_CAP)
                    : DebugManager.getInstance().parkStackRoots(JDI_OBJECT_CAP);
            if (parked >= 0)
            {
                return session.scanFirst(valueType, scanKind, value, value2, pkgFilter, userClassesOnly, maxVisited, maxMatches, limit, true, scoped);
            }
        }
        return session.scanFirst(valueType, scanKind, value, value2, pkgFilter, userClassesOnly, maxVisited, maxMatches, limit, false, false);
    }
}
