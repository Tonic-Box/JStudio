package com.tonic.event.events;

import com.tonic.event.Event;
import lombok.Getter;

/** Posted when the bytecode VM has been initialized with the project's classes. */
@Getter
public class VMInitializedEvent extends Event
{

    private final int classCount;
    private final String status;

    /**
     * Creates the event with status "initialized".
     *
     * @param source the poster
     * @param classCount how many classes the VM loaded
     */
    public VMInitializedEvent(Object source, int classCount)
    {
        super(source);
        this.classCount = classCount;
        this.status = "initialized";
    }

    /**
     * Creates the event.
     *
     * @param source the poster
     * @param classCount how many classes the VM loaded
     * @param status the status text
     */
    public VMInitializedEvent(Object source, int classCount, String status)
    {
        super(source);
        this.classCount = classCount;
        this.status = status;
    }

    @Override
    public String toString()
    {
        return "VMInitializedEvent{classCount=" + classCount + ", status='" + status + "'}";
    }
}
