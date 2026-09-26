package com.tonic.event.events;

import com.tonic.event.Event;
import com.tonic.model.ClassEntryModel;
import lombok.Getter;

/** Posted to open a class, optionally scrolled to a method or line. */
@Getter
public class ClassSelectedEvent extends Event
{

    private final ClassEntryModel classEntry;
    private final String scrollToMethod;
    private final int highlightLine;

    /**
     * Creates the event with no scroll target.
     *
     * @param source the poster
     * @param classEntry the class to open
     */
    public ClassSelectedEvent(Object source, ClassEntryModel classEntry)
    {
        super(source);
        this.classEntry = classEntry;
        this.scrollToMethod = null;
        this.highlightLine = -1;
    }

    /**
     * Creates the event with a scroll target.
     *
     * @param source the poster
     * @param classEntry the class to open
     * @param scrollToMethod the method name to scroll to, or null
     * @param highlightLine the 1-based line to highlight, or 0 or less for none
     */
    public ClassSelectedEvent(Object source, ClassEntryModel classEntry, String scrollToMethod, int highlightLine)
    {
        super(source);
        this.classEntry = classEntry;
        this.scrollToMethod = scrollToMethod;
        this.highlightLine = highlightLine;
    }

    /**
     * Tells whether the event asks for a scroll.
     *
     * @return whether a method or a positive line was given to scroll to
     */
    public boolean hasScrollTarget()
    {
        return scrollToMethod != null || highlightLine > 0;
    }
}
