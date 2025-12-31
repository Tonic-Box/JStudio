package com.tonic.ui.service;

import com.tonic.ui.console.LogLevel;
import lombok.Getter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

public class ConsoleLogService {

    private static ConsoleLogService instance;

    private final List<BiConsumer<LogLevel, String>> listeners = new CopyOnWriteArrayList<>();
    private final List<LogEntry> logHistory = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
    @Getter
    private boolean showTimestamps = true;
    private int maxHistory = 1000;

    private ConsoleLogService() {}

    public static synchronized ConsoleLogService getInstance() {
        if (instance == null) {
            instance = new ConsoleLogService();
        }
        return instance;
    }

    public void addListener(BiConsumer<LogLevel, String> listener) {
        listeners.add(listener);
    }

    public void removeListener(BiConsumer<LogLevel, String> listener) {
        listeners.remove(listener);
    }

    public void log(LogLevel level, String message) {
        LogEntry entry = new LogEntry(level, message, System.currentTimeMillis());
        addToHistory(entry);
        notifyListeners(level, message);
    }

    public void info(String message) {
        log(LogLevel.INFO, message);
    }

    public void warn(String message) {
        log(LogLevel.WARN, message);
    }

    public void error(String message) {
        log(LogLevel.ERROR, message);
    }

    public void error(String message, Throwable t) {
        log(LogLevel.ERROR, message + ": " + t.getMessage());
        StackTraceElement[] stack = t.getStackTrace();
        int limit = Math.min(5, stack.length);
        for (int i = 0; i < limit; i++) {
            log(LogLevel.ERROR, "  at " + stack[i].toString());
        }
        if (stack.length > 5) {
            log(LogLevel.ERROR, "  ... " + (stack.length - 5) + " more");
        }
    }

    public void debug(String message) {
        log(LogLevel.DEBUG, message);
    }

    public String formatWithTimestamp(String message) {
        if (showTimestamps) {
            return "[" + dateFormat.format(new Date()) + "] " + message;
        }
        return message;
    }

    public void setShowTimestamps(boolean show) {
        this.showTimestamps = show;
    }

    public List<LogEntry> getHistory() {
        synchronized (logHistory) {
            return new ArrayList<>(logHistory);
        }
    }

    public List<LogEntry> getHistory(LogLevel minLevel) {
        List<LogEntry> filtered = new ArrayList<>();
        synchronized (logHistory) {
            for (LogEntry entry : logHistory) {
                if (entry.getLevel().ordinal() >= minLevel.ordinal()) {
                    filtered.add(entry);
                }
            }
        }
        return filtered;
    }

    public void clearHistory() {
        synchronized (logHistory) {
            logHistory.clear();
        }
    }

    public void setMaxHistory(int max) {
        this.maxHistory = max;
        trimHistory();
    }

    private void addToHistory(LogEntry entry) {
        synchronized (logHistory) {
            logHistory.add(entry);
            trimHistory();
        }
    }

    private void trimHistory() {
        synchronized (logHistory) {
            while (logHistory.size() > maxHistory) {
                logHistory.remove(0);
            }
        }
    }

    private void notifyListeners(LogLevel level, String message) {
        for (BiConsumer<LogLevel, String> listener : listeners) {
            try {
                listener.accept(level, message);
            } catch (Exception e) {
                // Ignore listener errors to avoid cascading failures
            }
        }
    }

    @Getter
    public static class LogEntry {
        private final LogLevel level;
        private final String message;
        private final long timestamp;

        public LogEntry(LogLevel level, String message, long timestamp) {
            this.level = level;
            this.message = message;
            this.timestamp = timestamp;
        }

    }
}
