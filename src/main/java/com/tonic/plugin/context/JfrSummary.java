package com.tonic.plugin.context;

import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Summarizes a captured {@code .jfr} recording into compact text for the AI: top hot methods (CPU execution
 * samples), top allocation types, and lock/exception counts. Uses the JDK's {@code jdk.jfr.consumer} reader.
 */
final class JfrSummary {

    private JfrSummary() {
    }

    static String summarize(String path) {
        Map<String, Integer> hotMethods = new HashMap<>();
        Map<String, Integer> allocations = new HashMap<>();
        int samples = 0;
        int lockEvents = 0;
        int exceptions = 0;

        try (RecordingFile recording = new RecordingFile(Paths.get(path))) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                String name = event.getEventType().getName();
                if (name.equals("jdk.ExecutionSample") || name.equals("jdk.NativeMethodSample")) {
                    samples++;
                    RecordedStackTrace stack = event.getStackTrace();
                    if (stack != null && !stack.getFrames().isEmpty()) {
                        RecordedMethod method = stack.getFrames().get(0).getMethod();
                        if (method != null && method.getType() != null) {
                            hotMethods.merge(method.getType().getName() + "." + method.getName(), 1, Integer::sum);
                        }
                    }
                } else if (name.startsWith("jdk.ObjectAllocation") && event.hasField("objectClass")) {
                    Object value = event.getValue("objectClass");
                    if (value instanceof RecordedClass) {
                        allocations.merge(((RecordedClass) value).getName(), 1, Integer::sum);
                    }
                } else if (name.equals("jdk.JavaMonitorEnter") || name.equals("jdk.JavaMonitorWait")
                        || name.equals("jdk.ThreadPark")) {
                    lockEvents++;
                } else if (name.equals("jdk.JavaExceptionThrow") || name.equals("jdk.JavaErrorThrow")) {
                    exceptions++;
                }
            }
        } catch (IOException | RuntimeException e) {
            return "Could not parse JFR recording: " + e.getMessage();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("JFR summary (").append(samples).append(" CPU samples)\n");
        sb.append("\nHot methods (by sample count):\n").append(top(hotMethods, 15));
        if (!allocations.isEmpty()) {
            sb.append("\n\nAllocations by type:\n").append(top(allocations, 10));
        }
        sb.append("\n\nLock/park events: ").append(lockEvents);
        sb.append("\nExceptions thrown: ").append(exceptions);
        return sb.toString();
    }

    private static String top(Map<String, Integer> counts, int limit) {
        return counts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(limit)
                .map(e -> "  " + e.getValue() + "  " + e.getKey())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("  (none)");
    }
}
