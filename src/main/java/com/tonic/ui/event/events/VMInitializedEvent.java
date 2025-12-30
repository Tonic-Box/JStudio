package com.tonic.ui.event.events;

import com.tonic.ui.event.Event;
import lombok.Getter;

@Getter
public class VMInitializedEvent extends Event {

    private final int classCount;
    private final String status;

    public VMInitializedEvent(Object source, int classCount) {
        super(source);
        this.classCount = classCount;
        this.status = "initialized";
    }

    public VMInitializedEvent(Object source, int classCount, String status) {
        super(source);
        this.classCount = classCount;
        this.status = status;
    }

    @Override
    public String toString() {
        return "VMInitializedEvent{classCount=" + classCount + ", status='" + status + "'}";
    }
}
