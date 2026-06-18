package com.tonic.graph.dot;

/** Thrown by {@link DotParser} when the input is not a recognizable (subset) DOT graph. */
public class DotParseException extends RuntimeException {
    public DotParseException(String message) {
        super(message);
    }
}
