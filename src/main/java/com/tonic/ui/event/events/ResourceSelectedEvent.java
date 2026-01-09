package com.tonic.ui.event.events;

import com.tonic.ui.event.Event;
import com.tonic.ui.model.ResourceEntryModel;
import lombok.Getter;

@Getter
public class ResourceSelectedEvent extends Event {

    private final ResourceEntryModel resource;

    public ResourceSelectedEvent(Object source, ResourceEntryModel resource) {
        super(source);
        this.resource = resource;
    }
}
