package com.tonic.event.events;

import com.tonic.event.Event;
import lombok.Getter;

/** Streams a headless script run's output to the Script Console tab: START opens and clears it, LINE appends output, DONE reports the modification count. */
@Getter
public class ScriptConsoleEvent extends Event
{

    /** The stage of a script run being reported. */
    public enum Kind
    {
        START, LINE, DONE
    }

    private final Kind kind;
    private final String text;
    private final int modifications;

    /**
     * Creates the event.
     *
     * @param source the poster
     * @param kind the stage being reported
     * @param text the output line, for LINE
     * @param modifications the final modification count, for DONE
     */
    public ScriptConsoleEvent(Object source, Kind kind, String text, int modifications)
    {
        super(source);
        this.kind = kind;
        this.text = text;
        this.modifications = modifications;
    }
}
