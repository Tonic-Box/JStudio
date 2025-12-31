package com.tonic.ui.query.parser;

import lombok.Getter;

/**
 * Exception thrown when query parsing fails.
 */
@Getter
public class ParseException extends Exception {

    private final int position;

    public ParseException(String message, int position) {
        super(message + " at position " + position);
        this.position = position;
    }

}
