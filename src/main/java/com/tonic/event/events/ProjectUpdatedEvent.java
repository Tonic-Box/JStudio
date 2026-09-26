package com.tonic.event.events;

import com.tonic.event.Event;
import com.tonic.model.ProjectModel;
import lombok.Getter;

/** Posted when classes have been added to the open project. */
@Getter
public class ProjectUpdatedEvent extends Event
{

    private final ProjectModel project;
    private final int addedClassCount;

    /**
     * Creates the event.
     *
     * @param source the poster
     * @param project the updated project
     * @param addedClassCount how many classes were added
     */
    public ProjectUpdatedEvent(Object source, ProjectModel project, int addedClassCount)
    {
        super(source);
        this.project = project;
        this.addedClassCount = addedClassCount;
    }
}
