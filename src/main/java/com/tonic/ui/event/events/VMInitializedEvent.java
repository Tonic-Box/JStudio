package com.tonic.ui.event.events;

import com.tonic.ui.event.Event;

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

    public int getClassCount() {
        return classCount;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "VMInitializedEvent{classCount=" + classCount + ", status='" + status + "'}";
    }
}
