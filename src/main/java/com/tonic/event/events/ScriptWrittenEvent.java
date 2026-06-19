package com.tonic.event.events;

import com.tonic.event.Event;
import lombok.Getter;

/**
 * Posted when a script is written to the user scripts directory (e.g. by the AI assistant), so the Script Editor
 * can refresh its list and open to the new script.
 */
@Getter
public class ScriptWrittenEvent extends Event {

    private final String scriptName;

    public ScriptWrittenEvent(Object source, String scriptName) {
        super(source);
        this.scriptName = scriptName;
    }
}
