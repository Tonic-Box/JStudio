package com.tonic.plugin.context;

import com.tonic.plugin.api.PluginLogger;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** A PluginLogger that prints timestamped, plugin-labelled lines to two print streams, standard out and error by default; debug output starts off. */
public class ConsolePluginLogger implements PluginLogger
{

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String pluginName;
    private final PrintStream out;
    private final PrintStream err;
    private boolean debugEnabled = false;

    /**
     * Creates a logger that writes to standard out and standard error.
     *
     * @param pluginName the name printed on every line
     */
    public ConsolePluginLogger(String pluginName)
    {
        this.pluginName = pluginName;
        this.out = System.out;
        this.err = System.err;
    }

    /**
     * Creates a logger that writes to the given streams.
     *
     * @param pluginName the name printed on every line
     * @param out receives info, warn, debug and progress lines
     * @param err receives error lines and stack traces
     */
    public ConsolePluginLogger(String pluginName, PrintStream out, PrintStream err)
    {
        this.pluginName = pluginName;
        this.out = out;
        this.err = err;
    }

    /**
     * Turns debug output, and stack traces on errors, on or off.
     *
     * @param enabled true to write them
     */
    public void setDebugEnabled(boolean enabled)
    {
        this.debugEnabled = enabled;
    }

    @Override
    public void info(String message)
    {
        out.println(format("INFO", message));
    }

    @Override
    public void info(String format, Object... args)
    {
        info(String.format(format, args));
    }

    @Override
    public void warn(String message)
    {
        out.println(format("WARN", message));
    }

    @Override
    public void warn(String format, Object... args)
    {
        warn(String.format(format, args));
    }

    @Override
    public void error(String message)
    {
        err.println(format("ERROR", message));
    }

    @Override
    public void error(String format, Object... args)
    {
        error(String.format(format, args));
    }

    @Override
    public void error(String message, Throwable throwable)
    {
        error(message + " - " + throwable.getMessage());
        if (debugEnabled)
        {
            throwable.printStackTrace(err);
        }
    }

    @Override
    public void debug(String message)
    {
        if (debugEnabled)
        {
            out.println(format("DEBUG", message));
        }
    }

    @Override
    public void debug(String format, Object... args)
    {
        if (debugEnabled)
        {
            debug(String.format(format, args));
        }
    }

    @Override
    public void progress(String message, int current, int total)
    {
        int percent = total > 0 ? (current * 100) / total : 0;
        out.printf("\r[%s] %s: %d/%d (%d%%)%n", pluginName, message, current, total, percent);
    }

    @Override
    public boolean isDebugEnabled()
    {
        return debugEnabled;
    }

    private String format(String level, String message)
    {
        String time = LocalDateTime.now().format(TIME_FORMAT);
        return String.format("[%s] [%s] [%s] %s", time, level, pluginName, message);
    }
}
