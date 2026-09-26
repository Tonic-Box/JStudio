package com.tonic.event.events;

import com.tonic.event.Event;
import com.tonic.model.ProjectModel;
import lombok.Getter;

/** Posted when a project has been loaded. */
@Getter
public class ProjectLoadedEvent extends Event
{

    private final ProjectModel project;

    /**
     * Creates the event.
     *
     * @param source the poster
     * @param project the loaded project
     */
    public ProjectLoadedEvent(Object source, ProjectModel project)
    {
        super(source);
        this.project = project;
    }
}
