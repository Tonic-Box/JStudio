package com.tonic.ui.vm.heap.model;

import lombok.Getter;

/** One entry on the heap forensics timeline: an allocation, a mutation or a snapshot, ordered by instruction count. */
@Getter
public class TimelineEvent implements Comparable<TimelineEvent>
{
    /** What kind of event a timeline entry holds. */
    public enum EventType
    {
        ALLOCATION,
        MUTATION,
        SNAPSHOT
    }

    private final EventType type;
    private final long instructionCount;
    private final int objectId;
    private final String description;
    private final Object eventData;

    private TimelineEvent(EventType type, long instructionCount, int objectId, String description, Object eventData)
    {
        this.type = type;
        this.instructionCount = instructionCount;
        this.objectId = objectId;
        this.description = description;
        this.eventData = eventData;
    }

    /**
     * Wraps an allocation.
     *
     * @param event the allocation
     * @return the timeline entry
     */
    public static TimelineEvent allocation(AllocationEvent event)
    {
        return new TimelineEvent(EventType.ALLOCATION, event.getInstructionCount(), event.getObjectId(), "NEW " + event.getShortDescription(), event);
    }

    /**
     * Wraps a field write.
     *
     * @param event the mutation
     * @return the timeline entry
     */
    public static TimelineEvent mutation(MutationEvent event)
    {
        String desc = (event.isStatic() ? "PUTSTATIC " : "PUTFIELD ") +
                event.getFieldName();
        return new TimelineEvent(EventType.MUTATION, event.getInstructionCount(), event.getObjectId(), desc, event);
    }

    /**
     * Wraps a snapshot; the entry has no object id.
     *
     * @param instructionCount when the snapshot was taken
     * @param label the snapshot's label
     * @param snapshot the snapshot
     * @return the timeline entry
     */
    public static TimelineEvent snapshot(long instructionCount, String label, HeapSnapshot snapshot)
    {
        return new TimelineEvent(EventType.SNAPSHOT, instructionCount, -1, "SNAPSHOT: " + label, snapshot);
    }

    /**
     * Unwraps an allocation entry.
     *
     * @return the allocation, or null when this entry is another kind
     */
    public AllocationEvent asAllocation()
    {
        return type == EventType.ALLOCATION ? (AllocationEvent) eventData : null;
    }

    /**
     * Unwraps a mutation entry.
     *
     * @return the mutation, or null when this entry is another kind
     */
    public MutationEvent asMutation()
    {
        return type == EventType.MUTATION ? (MutationEvent) eventData : null;
    }

    /**
     * Unwraps a snapshot entry.
     *
     * @return the snapshot, or null when this entry is another kind
     */
    public HeapSnapshot asSnapshot()
    {
        return type == EventType.SNAPSHOT ? (HeapSnapshot) eventData : null;
    }

    @Override
    public int compareTo(TimelineEvent other)
    {
        return Long.compare(this.instructionCount, other.instructionCount);
    }

    @Override
    public String toString()
    {
        return String.format("[%d] %s: %s", instructionCount, type, description);
    }
}
