package com.tonic.ui.query.parser;

/**
 * Exception thrown when query parsing fails.
 */
public class ParseException extends Exception {

    private final int position;

    public ParseException(String message, int position) {
        super(message + " at position " + position);
        this.position = position;
    }

    public int getPosition() {
        return position;
    }
}
