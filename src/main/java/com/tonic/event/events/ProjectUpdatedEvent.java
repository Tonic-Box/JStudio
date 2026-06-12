package com.tonic.event.events;

import com.tonic.event.Event;
import com.tonic.model.ProjectModel;
import lombok.Getter;

@Getter
public class ProjectUpdatedEvent extends Event {

    private final ProjectModel project;
    private final int addedClassCount;

    public ProjectUpdatedEvent(Object source, ProjectModel project, int addedClassCount) {
        super(source);
        this.project = project;
        this.addedClassCount = addedClassCount;
    }
}
