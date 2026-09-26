package com.tonic.ui.vm.debugger.edit;

/** Thrown when text typed as a new debugger value cannot be parsed for its type. */
public class ValueParseException extends Exception
{

    /**
     * Creates the exception.
     *
     * @param message what is wrong with the input
     */
    public ValueParseException(String message)
    {
        super(message);
    }

    /**
     * Creates the exception with the parse failure that caused it.
     *
     * @param message what is wrong with the input
     * @param cause the underlying failure
     */
    public ValueParseException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
