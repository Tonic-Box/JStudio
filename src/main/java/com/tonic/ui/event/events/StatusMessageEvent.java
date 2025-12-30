package com.tonic.ui.event.events;

import com.tonic.ui.event.Event;
import lombok.Getter;

@Getter
public class StatusMessageEvent extends Event {

    public enum MessageType {
        INFO,
        WARNING,
        ERROR
    }

    private final String message;
    private final MessageType type;

    public StatusMessageEvent(Object source, String message) {
        this(source, message, MessageType.INFO);
    }

    public StatusMessageEvent(Object source, String message, MessageType type) {
        super(source);
        this.message = message;
        this.type = type;
    }
}
