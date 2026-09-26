package com.tonic.plugin.api;

/** A plugin's logger; every line carries a timestamp, level and the plugin name, info and warn go to standard out and error to standard error. */
public interface PluginLogger
{

    /**
     * Logs a message at info level.
     *
     * @param message the text to log
     */
    void info(String message);

    /**
     * Logs a formatted message at info level.
     *
     * @param format a String.format pattern
     * @param args the pattern's arguments
     */
    void info(String format, Object... args);

    /**
     * Logs a message at warn level.
     *
     * @param message the text to log
     */
    void warn(String message);

    /**
     * Logs a formatted message at warn level.
     *
     * @param format a String.format pattern
     * @param args the pattern's arguments
     */
    void warn(String format, Object... args);

    /**
     * Logs a message at error level.
     *
     * @param message the text to log
     */
    void error(String message);

    /**
     * Logs a formatted message at error level.
     *
     * @param format a String.format pattern
     * @param args the pattern's arguments
     */
    void error(String format, Object... args);

    /**
     * Logs a message with the throwable's message appended at error level; the stack trace is printed only when debug is enabled.
     *
     * @param message the text to log
     * @param throwable the failure to report, not null
     */
    void error(String message, Throwable throwable);

    /**
     * Logs a message at debug level; dropped unless debug is enabled.
     *
     * @param message the text to log
     */
    void debug(String message);

    /**
     * Logs a formatted message at debug level; dropped, without formatting, unless debug is enabled.
     *
     * @param format a String.format pattern
     * @param args the pattern's arguments
     */
    void debug(String format, Object... args);

    /**
     * Logs a progress line with the count and percentage.
     *
     * @param message what is progressing
     * @param current how many items are done
     * @param total how many items there are; 0 or less shows 0 percent
     */
    void progress(String message, int current, int total);

    /**
     * Reports whether debug messages are written.
     *
     * @return true when debug is on; false unless the host turns it on
     */
    boolean isDebugEnabled();
}
