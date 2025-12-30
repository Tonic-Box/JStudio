package com.tonic.ui.event.events;

import com.tonic.ui.event.Event;
import com.tonic.ui.model.ClassEntryModel;
import lombok.Getter;

@Getter
public class ClassSelectedEvent extends Event {

    private final ClassEntryModel classEntry;

    public ClassSelectedEvent(Object source, ClassEntryModel classEntry) {
        super(source);
        this.classEntry = classEntry;
    }
}
