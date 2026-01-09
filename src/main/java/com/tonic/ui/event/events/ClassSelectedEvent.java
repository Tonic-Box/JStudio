package com.tonic.ui.event.events;

import com.tonic.ui.event.Event;
import com.tonic.ui.model.ClassEntryModel;
import lombok.Getter;

@Getter
public class ClassSelectedEvent extends Event {

    private final ClassEntryModel classEntry;
    private final String scrollToMethod;
    private final int highlightLine;

    public ClassSelectedEvent(Object source, ClassEntryModel classEntry) {
        super(source);
        this.classEntry = classEntry;
        this.scrollToMethod = null;
        this.highlightLine = -1;
    }

    public ClassSelectedEvent(Object source, ClassEntryModel classEntry, String scrollToMethod, int highlightLine) {
        super(source);
        this.classEntry = classEntry;
        this.scrollToMethod = scrollToMethod;
        this.highlightLine = highlightLine;
    }

    public boolean hasScrollTarget() {
        return scrollToMethod != null || highlightLine > 0;
    }
}
