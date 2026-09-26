package com.tonic.event.events;

import com.tonic.event.Event;
import lombok.Getter;

/** Posted to show a message in the status bar. */
@Getter
public class StatusMessageEvent extends Event
{

    /** The severity of a status message. */
    public enum MessageType
    {
        INFO,
        WARNING,
        ERROR
    }

    private final String message;
    private final MessageType type;

    /**
     * Creates an INFO message.
     *
     * @param source the poster
     * @param message the text to show
     */
    public StatusMessageEvent(Object source, String message)
    {
        this(source, message, MessageType.INFO);
    }

    /**
     * Creates a message of the given severity.
     *
     * @param source the poster
     * @param message the text to show
     * @param type the severity
     */
    public StatusMessageEvent(Object source, String message, MessageType type)
    {
        super(source);
        this.message = message;
        this.type = type;
    }
}
