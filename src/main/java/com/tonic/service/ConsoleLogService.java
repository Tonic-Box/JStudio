package com.tonic.service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/** The application log that fans each message out to the registered console listeners. */
public class ConsoleLogService
{

    private static ConsoleLogService instance;

    private final List<BiConsumer<LogLevel, String>> listeners = new CopyOnWriteArrayList<>();

    private ConsoleLogService()
    {
    }

    /**
     * Returns the shared instance, creating it on first use.
     *
     * @return the shared instance
     */
    public static synchronized ConsoleLogService getInstance()
    {
        if (instance == null)
        {
            instance = new ConsoleLogService();
        }
        return instance;
    }

    /**
     * Registers a listener called with the level and text of every later message; exceptions it throws are swallowed.
     *
     * @param listener the listener
     */
    public void addListener(BiConsumer<LogLevel, String> listener)
    {
        listeners.add(listener);
    }

    /**
     * Passes a message to every listener.
     *
     * @param level the severity
     * @param message the text
     */
    public void log(LogLevel level, String message)
    {
        notifyListeners(level, message);
    }

    /**
     * Logs a message at info level.
     *
     * @param message the text
     */
    public void info(String message)
    {
        log(LogLevel.INFO, message);
    }

    /**
     * Logs a message at warn level.
     *
     * @param message the text
     */
    public void warn(String message)
    {
        log(LogLevel.WARN, message);
    }

    /**
     * Logs a message at error level.
     *
     * @param message the text
     */
    public void error(String message)
    {
        log(LogLevel.ERROR, message);
    }

    /**
     * Logs a message and the throwable's message at error level, followed by up to five stack frames.
     *
     * @param message the text
     * @param t the throwable to report
     */
    public void error(String message, Throwable t)
    {
        log(LogLevel.ERROR, message + ": " + t.getMessage());
        StackTraceElement[] stack = t.getStackTrace();
        int limit = Math.min(5, stack.length);
        for (int i = 0; i < limit; i++)
        {
            log(LogLevel.ERROR, "  at " + stack[i].toString());
        }
        if (stack.length > 5)
        {
            log(LogLevel.ERROR, "  ... " + (stack.length - 5) + " more");
        }
    }

    /**
     * Logs a message at debug level.
     *
     * @param message the text
     */
    public void debug(String message)
    {
        log(LogLevel.DEBUG, message);
    }

    private void notifyListeners(LogLevel level, String message)
    {
        for (BiConsumer<LogLevel, String> listener : listeners)
        {
            try
            {
                listener.accept(level, message);
            }
            catch (Exception e)
            {
            }
        }
    }
}
