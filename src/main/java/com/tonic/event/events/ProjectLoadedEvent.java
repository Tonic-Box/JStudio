package com.tonic.event.events;

import com.tonic.event.Event;
import com.tonic.model.ProjectModel;
import lombok.Getter;

@Getter
public class ProjectLoadedEvent extends Event {

    private final ProjectModel project;

    public ProjectLoadedEvent(Object source, ProjectModel project) {
        super(source);
        this.project = project;
    }
}
