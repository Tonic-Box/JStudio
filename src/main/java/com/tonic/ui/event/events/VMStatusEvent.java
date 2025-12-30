package com.tonic.ui.event.events;

import com.tonic.ui.event.Event;
import lombok.Getter;

@Getter
public class VMStatusEvent extends Event {

    public enum VMState {
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

    public VMStatusEvent(Object source, VMState state) {
        this(source, state, "", 0);
    }

    public VMStatusEvent(Object source, VMState state, String message) {
        this(source, state, message, 0);
    }

    public VMStatusEvent(Object source, VMState state, String message, long instructionCount) {
        super(source);
        this.state = state;
        this.message = message;
        this.instructionCount = instructionCount;
    }

    public boolean isIdle() {
        return state == VMState.IDLE;
    }

    public boolean isExecuting() {
        return state == VMState.EXECUTING;
    }

    public boolean isError() {
        return state == VMState.ERROR;
    }

    @Override
    public String toString() {
        return "VMStatusEvent{state=" + state + ", message='" + message + "', instructions=" + instructionCount + "}";
    }
}
