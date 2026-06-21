package com.tonic.util;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class ErrorHandler {

    private static final Logger LOGGER = Logger.getLogger(ErrorHandler.class.getName());

    private ErrorHandler() {
    }

    public static void handle(Exception e, String context) {
        LOGGER.log(Level.WARNING, context + ": " + e.getMessage(), e);
    }
}
