package com.tonic.event.events;

import com.tonic.event.Event;
import lombok.Getter;

/** Posted when a script is written to the user scripts directory, so the Script Editor refreshes and opens it. */
@Getter
public class ScriptWrittenEvent extends Event
{

    private final String scriptName;

    /**
     * Creates the event.
     *
     * @param source the poster
     * @param scriptName the written script's name
     */
    public ScriptWrittenEvent(Object source, String scriptName)
    {
        super(source);
        this.scriptName = scriptName;
    }
}
