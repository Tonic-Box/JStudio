package com.tonic.ui.event.events;

import com.tonic.ui.event.Event;
import com.tonic.ui.model.MethodEntryModel;
import lombok.Getter;

@Getter
public class MethodSelectedEvent extends Event {

    private final MethodEntryModel methodEntry;

    public MethodSelectedEvent(Object source, MethodEntryModel methodEntry) {
        super(source);
        this.methodEntry = methodEntry;
    }
}
