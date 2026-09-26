package com.tonic.live;

import com.tonic.live.protocol.ContentionEdge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Finds deadlocks as cycles in a live wait-for graph; each thread blocks on at most one monitor, so every cycle is a simple ring. */
public final class Deadlocks
{

    private Deadlocks()
    {
    }

    /**
     * Finds every deadlock cycle in a wait-for graph.
     *
     * @param edges the graph, one edge per blocked thread to its monitor's owner
     * @return each cycle as the ordered edges forming the ring; empty when there is none
     */
    public static List<List<ContentionEdge>> find(List<ContentionEdge> edges)
    {
        Map<Long, ContentionEdge> byThread = new HashMap<>();
        for (ContentionEdge e : edges)
        {
            byThread.put(e.getThreadId(), e);
        }
        List<List<ContentionEdge>> cycles = new ArrayList<>();
        Set<Long> consumed = new HashSet<>();
        for (ContentionEdge start : edges)
        {
            if (consumed.contains(start.getThreadId()))
            {
                continue;
            }
            List<ContentionEdge> path = new ArrayList<>();
            Set<Long> onPath = new HashSet<>();
            ContentionEdge cur = start;
            while (cur != null && !onPath.contains(cur.getThreadId()) && !consumed.contains(cur.getThreadId()))
            {
                path.add(cur);
                onPath.add(cur.getThreadId());
                cur = byThread.get(cur.getOwnerThreadId());
            }
            if (cur != null && onPath.contains(cur.getThreadId()))
            {
                int from = 0;
                while (path.get(from).getThreadId() != cur.getThreadId())
                {
                    from++;
                }
                List<ContentionEdge> cycle = new ArrayList<>(path.subList(from, path.size()));
                cycles.add(cycle);
                for (ContentionEdge e : cycle)
                {
                    consumed.add(e.getThreadId());
                }
            }
        }
        return cycles;
    }
}
