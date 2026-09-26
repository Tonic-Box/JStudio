package com.tonic.event.events;

import com.tonic.event.Event;
import com.tonic.model.ResourceEntryModel;
import lombok.Getter;

/** Posted when a non-class resource is selected. */
@Getter
public class ResourceSelectedEvent extends Event
{

    private final ResourceEntryModel resource;

    /**
     * Creates the event.
     *
     * @param source the poster
     * @param resource the selected resource
     */
    public ResourceSelectedEvent(Object source, ResourceEntryModel resource)
    {
        super(source);
        this.resource = resource;
    }
}
