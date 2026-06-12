package com.tonic.event.events;

import com.tonic.event.Event;
import com.tonic.model.MethodEntryModel;
import lombok.Getter;

@Getter
public class MethodSelectedEvent extends Event {

    private final MethodEntryModel methodEntry;

    public MethodSelectedEvent(Object source, MethodEntryModel methodEntry) {
        super(source);
        this.methodEntry = methodEntry;
    }
}
