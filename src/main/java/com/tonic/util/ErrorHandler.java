package com.tonic.util;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Logs handled exceptions at WARNING level. */
public final class ErrorHandler
{

    private static final Logger LOGGER = Logger.getLogger(ErrorHandler.class.getName());

    private ErrorHandler()
    {
    }

    /**
     * Logs an exception with a description of what was being done.
     *
     * @param e the exception
     * @param context what failed, prefixed to the log message
     */
    public static void handle(Exception e, String context)
    {
        LOGGER.log(Level.WARNING, context + ": " + e.getMessage(), e);
    }
}
