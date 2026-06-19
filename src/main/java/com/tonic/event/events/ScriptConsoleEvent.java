package com.tonic.event.events;

import com.tonic.event.Event;
import lombok.Getter;

/**
 * Streams a headless script run's console output to the bottom Script Console tab: {@code START} opens/clears the
 * tab, {@code LINE} appends a line of output, {@code DONE} reports the final modification count.
 */
@Getter
public class ScriptConsoleEvent extends Event {

    public enum Kind {
        START, LINE, DONE
    }

    private final Kind kind;
    private final String text;
    private final int modifications;

    public ScriptConsoleEvent(Object source, Kind kind, String text, int modifications) {
        super(source);
        this.kind = kind;
        this.text = text;
        this.modifications = modifications;
    }
}
