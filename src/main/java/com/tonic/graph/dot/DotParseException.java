package com.tonic.graph.dot;

/** Thrown by the DOT parser when the input is not a recognizable DOT graph. */
public class DotParseException extends RuntimeException
{
    /**
     * Creates the exception.
     *
     * @param message what was wrong with the input
     */
    public DotParseException(String message)
    {
        super(message);
    }
}
