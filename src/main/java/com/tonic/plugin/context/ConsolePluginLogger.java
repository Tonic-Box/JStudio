package com.tonic.plugin.context;

import com.tonic.plugin.api.PluginLogger;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class ConsolePluginLogger implements PluginLogger {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String pluginName;
    private final PrintStream out;
    private final PrintStream err;
    private boolean debugEnabled = false;
    private Consumer<String> infoCallback;
    private Consumer<String> warnCallback;
    private Consumer<String> errorCallback;

    public ConsolePluginLogger(String pluginName) {
        this.pluginName = pluginName;
        this.out = System.out;
        this.err = System.err;
    }

    public ConsolePluginLogger(String pluginName, PrintStream out, PrintStream err) {
        this.pluginName = pluginName;
        this.out = out;
        this.err = err;
    }

    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
    }

    public void setInfoCallback(Consumer<String> callback) {
        this.infoCallback = callback;
    }

    public void setWarnCallback(Consumer<String> callback) {
        this.warnCallback = callback;
    }

    public void setErrorCallback(Consumer<String> callback) {
        this.errorCallback = callback;
    }

    @Override
    public void info(String message) {
        String formatted = format("INFO", message);
        out.println(formatted);
        if (infoCallback != null) {
            infoCallback.accept(message);
        }
    }

    @Override
    public void info(String format, Object... args) {
        info(String.format(format, args));
    }

    @Override
    public void warn(String message) {
        String formatted = format("WARN", message);
        out.println(formatted);
        if (warnCallback != null) {
            warnCallback.accept(message);
        }
    }

    @Override
    public void warn(String format, Object... args) {
        warn(String.format(format, args));
    }

    @Override
    public void error(String message) {
        String formatted = format("ERROR", message);
        err.println(formatted);
        if (errorCallback != null) {
            errorCallback.accept(message);
        }
    }

    @Override
    public void error(String format, Object... args) {
        error(String.format(format, args));
    }

    @Override
    public void error(String message, Throwable throwable) {
        error(message + " - " + throwable.getMessage());
        if (debugEnabled) {
            throwable.printStackTrace(err);
        }
    }

    @Override
    public void debug(String message) {
        if (debugEnabled) {
            out.println(format("DEBUG", message));
        }
    }

    @Override
    public void debug(String format, Object... args) {
        if (debugEnabled) {
            debug(String.format(format, args));
        }
    }

    @Override
    public void progress(String message, int current, int total) {
        int percent = total > 0 ? (current * 100) / total : 0;
        out.printf("\r[%s] %s: %d/%d (%d%%)%n", pluginName, message, current, total, percent);
    }

    @Override
    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    private String format(String level, String message) {
        String time = LocalDateTime.now().format(TIME_FORMAT);
        return String.format("[%s] [%s] [%s] %s", time, level, pluginName, message);
    }
}
