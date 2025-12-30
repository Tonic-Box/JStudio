package com.tonic.ui.core.util;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class ErrorHandler {

    private static final Logger LOGGER = Logger.getLogger(ErrorHandler.class.getName());

    private ErrorHandler() {
    }

    public static void handle(Exception e, String context) {
        LOGGER.log(Level.WARNING, context + ": " + e.getMessage(), e);
    }

    public static void handle(Throwable t, String context) {
        LOGGER.log(Level.WARNING, context + ": " + t.getMessage(), t);
    }

    public static void handleSevere(Exception e, String context) {
        LOGGER.log(Level.SEVERE, context + ": " + e.getMessage(), e);
    }

    public static void handleSilent(Exception e, String context) {
        LOGGER.log(Level.FINE, context + ": " + e.getMessage(), e);
    }

    public static void logInfo(String context, String message) {
        LOGGER.log(Level.INFO, context + ": " + message);
    }

    public static void logWarning(String context, String message) {
        LOGGER.log(Level.WARNING, context + ": " + message);
    }

    public static void logDebug(String context, String message) {
        LOGGER.log(Level.FINE, context + ": " + message);
    }
}
