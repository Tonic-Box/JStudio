package com.tonic.ui.event.events;

import com.tonic.ui.event.Event;
import com.tonic.ui.model.ProjectModel;
import lombok.Getter;

@Getter
public class ProjectLoadedEvent extends Event {

    private final ProjectModel project;

    public ProjectLoadedEvent(Object source, ProjectModel project) {
        super(source);
        this.project = project;
    }
}
