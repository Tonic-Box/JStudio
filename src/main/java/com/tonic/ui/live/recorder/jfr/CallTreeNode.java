package com.tonic.ui.live.recorder.jfr;

import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A node in a weighted call tree (the model behind a flame graph). The root is a synthetic entry whose
 * children are the outermost callers; each node accumulates {@code totalWeight} (this frame and everything it
 * called) and {@code selfWeight} (samples/bytes/time that ended in this frame). Weight units depend on the
 * source aggregate (CPU = sample count, allocation = bytes, locks = nanos).
 */
public final class CallTreeNode {

    /**
     * -- GETTER --
     * The frame this node represents, or null for the synthetic root.
     */
    @Getter
    private final FrameKey frame;
    private final Map<FrameKey, CallTreeNode> children = new LinkedHashMap<>();
    @Getter
    private long totalWeight;
    @Getter
    private long selfWeight;

    public CallTreeNode(FrameKey frame) {
        this.frame = frame;
    }

    public void addTotal(long weight) {
        totalWeight += weight;
    }

    public void addSelf(long weight) {
        selfWeight += weight;
    }

    /** Returns the child for {@code key}, creating it if absent. */
    public CallTreeNode child(FrameKey key) {
        return children.computeIfAbsent(key, CallTreeNode::new);
    }

    /** Children sorted by descending total weight (flame-graph left-to-right order). */
    public List<CallTreeNode> sortedChildren() {
        List<CallTreeNode> list = new ArrayList<>(children.values());
        list.sort((a, b) -> Long.compare(b.totalWeight, a.totalWeight));
        return list;
    }

    public boolean isEmpty() {
        return children.isEmpty() && selfWeight == 0;
    }
}
