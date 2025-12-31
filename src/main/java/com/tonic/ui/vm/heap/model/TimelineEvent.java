package com.tonic.ui.vm.heap.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
public class TimelineEvent implements Comparable<TimelineEvent> {
    public enum EventType {
        ALLOCATION,
        MUTATION,
        SNAPSHOT
    }

    private final EventType type;
    private final long instructionCount;
    private final int objectId;
    private final String description;
    private final Object eventData;

    private TimelineEvent(EventType type, long instructionCount, int objectId,
                          String description, Object eventData) {
        this.type = type;
        this.instructionCount = instructionCount;
        this.objectId = objectId;
        this.description = description;
        this.eventData = eventData;
    }

    public static TimelineEvent allocation(AllocationEvent event) {
        return new TimelineEvent(
            EventType.ALLOCATION,
            event.getInstructionCount(),
            event.getObjectId(),
            "NEW " + event.getShortDescription(),
            event
        );
    }

    public static TimelineEvent mutation(MutationEvent event) {
        String desc = (event.isStatic() ? "PUTSTATIC " : "PUTFIELD ") +
                      event.getFieldName();
        return new TimelineEvent(
            EventType.MUTATION,
            event.getInstructionCount(),
            event.getObjectId(),
            desc,
            event
        );
    }

    public static TimelineEvent snapshot(long instructionCount, String label, HeapSnapshot snapshot) {
        return new TimelineEvent(
            EventType.SNAPSHOT,
            instructionCount,
            -1,
            "SNAPSHOT: " + label,
            snapshot
        );
    }

    public AllocationEvent asAllocation() {
        return type == EventType.ALLOCATION ? (AllocationEvent) eventData : null;
    }

    public MutationEvent asMutation() {
        return type == EventType.MUTATION ? (MutationEvent) eventData : null;
    }

    public HeapSnapshot asSnapshot() {
        return type == EventType.SNAPSHOT ? (HeapSnapshot) eventData : null;
    }

    @Override
    public int compareTo(TimelineEvent other) {
        return Long.compare(this.instructionCount, other.instructionCount);
    }

    @Override
    public String toString() {
        return String.format("[%d] %s: %s", instructionCount, type, description);
    }
}
