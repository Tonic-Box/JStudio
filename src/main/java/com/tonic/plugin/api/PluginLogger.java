package com.tonic.plugin.api;

public interface PluginLogger {

    void info(String message);

    void info(String format, Object... args);

    void warn(String message);

    void warn(String format, Object... args);

    void error(String message);

    void error(String format, Object... args);

    void error(String message, Throwable throwable);

    void debug(String message);

    void debug(String format, Object... args);

    void progress(String message, int current, int total);

    boolean isDebugEnabled();
}
