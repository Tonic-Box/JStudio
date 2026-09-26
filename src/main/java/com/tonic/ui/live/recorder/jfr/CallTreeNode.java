package com.tonic.ui.live.recorder.jfr;

import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A node in a weighted call tree behind a flame graph; total weight covers the frame and its callees, self weight only what ended in it, in samples, bytes or nanos. */
public final class CallTreeNode
{

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

    /**
     * Creates a node with no weight and no children.
     *
     * @param frame the frame this node stands for, or null for the synthetic root
     */
    public CallTreeNode(FrameKey frame)
    {
        this.frame = frame;
    }

    /**
     * Adds to the weight of this frame and everything it called.
     *
     * @param weight the samples, bytes or nanos to add
     */
    public void addTotal(long weight)
    {
        totalWeight += weight;
    }

    /**
     * Adds to the weight that ended in this frame.
     *
     * @param weight the samples, bytes or nanos to add
     */
    public void addSelf(long weight)
    {
        selfWeight += weight;
    }

    /**
     * Returns the child for a frame, creating it if absent.
     *
     * @param key the child's frame
     * @return the existing or new child
     */
    public CallTreeNode child(FrameKey key)
    {
        return children.computeIfAbsent(key, CallTreeNode::new);
    }

    /**
     * Returns the children in flame-graph left-to-right order.
     *
     * @return a new list of the children, heaviest total weight first
     */
    public List<CallTreeNode> sortedChildren()
    {
        List<CallTreeNode> list = new ArrayList<>(children.values());
        list.sort((a, b) -> Long.compare(b.totalWeight, a.totalWeight));
        return list;
    }

    /**
     * Tells whether the node carries nothing.
     *
     * @return true if it has no children and no self weight
     */
    public boolean isEmpty()
    {
        return children.isEmpty() && selfWeight == 0;
    }
}
