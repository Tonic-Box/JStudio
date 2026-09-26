package com.tonic.event.events;

import com.tonic.event.Event;
import com.tonic.model.MethodEntryModel;
import lombok.Getter;

/** Posted when a method is selected. */
@Getter
public class MethodSelectedEvent extends Event
{

    private final MethodEntryModel methodEntry;

    /**
     * Creates the event.
     *
     * @param source the poster
     * @param methodEntry the selected method
     */
    public MethodSelectedEvent(Object source, MethodEntryModel methodEntry)
    {
        super(source);
        this.methodEntry = methodEntry;
    }
}
