package com.tonic.ui.event.events;

import com.tonic.ui.event.Event;
import com.tonic.ui.model.ProjectModel;

/**
 * Fired when classes are appended to an existing project.
 */
public class ProjectUpdatedEvent extends Event {

    private final ProjectModel project;
    private final int addedClassCount;

    public ProjectUpdatedEvent(Object source, ProjectModel project, int addedClassCount) {
        super(source);
        this.project = project;
        this.addedClassCount = addedClassCount;
    }

    public ProjectModel getProject() {
        return project;
    }

    public int getAddedClassCount() {
        return addedClassCount;
    }
}
