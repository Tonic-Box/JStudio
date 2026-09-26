package com.tonic.event.events;

import com.tonic.event.Event;
import lombok.Getter;

/** Posted when the bytecode VM changes state. */
@Getter
public class VMStatusEvent extends Event
{

    /** The bytecode VM's lifecycle state. */
    public enum VMState
    {
        IDLE,
        INITIALIZING,
        INITIALIZED,
        EXECUTING,
        PAUSED,
        STOPPED,
        ERROR
    }

    private final VMState state;
    private final String message;
    private final long instructionCount;

    /**
     * Creates the event with an empty message and no instruction count.
     *
     * @param source the poster
     * @param state the new state
     */
    public VMStatusEvent(Object source, VMState state)
    {
        this(source, state, "", 0);
    }

    /**
     * Creates the event with no instruction count.
     *
     * @param source the poster
     * @param state the new state
     * @param message the status text
     */
    public VMStatusEvent(Object source, VMState state, String message)
    {
        this(source, state, message, 0);
    }

    /**
     * Creates the event.
     *
     * @param source the poster
     * @param state the new state
     * @param message the status text
     * @param instructionCount how many instructions have executed
     */
    public VMStatusEvent(Object source, VMState state, String message, long instructionCount)
    {
        super(source);
        this.state = state;
        this.message = message;
        this.instructionCount = instructionCount;
    }

    /**
     * Tells whether the VM is idle.
     *
     * @return whether the state is IDLE
     */
    public boolean isIdle()
    {
        return state == VMState.IDLE;
    }

    /**
     * Tells whether the VM is executing.
     *
     * @return whether the state is EXECUTING
     */
    public boolean isExecuting()
    {
        return state == VMState.EXECUTING;
    }

    /**
     * Tells whether the VM is in error.
     *
     * @return whether the state is ERROR
     */
    public boolean isError()
    {
        return state == VMState.ERROR;
    }

    @Override
    public String toString()
    {
        return "VMStatusEvent{state=" + state + ", message='" + message + "', instructions=" + instructionCount + "}";
    }
}
