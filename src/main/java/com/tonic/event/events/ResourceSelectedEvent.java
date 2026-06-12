package com.tonic.event.events;

import com.tonic.event.Event;
import com.tonic.model.ResourceEntryModel;
import lombok.Getter;

@Getter
public class ResourceSelectedEvent extends Event {

    private final ResourceEntryModel resource;

    public ResourceSelectedEvent(Object source, ResourceEntryModel resource) {
        super(source);
        this.resource = resource;
    }
}
