package com.tonic.live.agent;

import com.tonic.live.protocol.LiveProtocol;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.jfr.Configuration;
import jdk.jfr.EventType;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;

/**
 * Drives Java Flight Recorder in the target JVM for the JStudio Live "Recorder" tool. Owns a single active
 * {@link Recording} at a time (the same "one capture" model as the heap dump). Recordings dump to the target's
 * temp dir; since attach is local (127.0.0.1) JStudio reads the {@code .jfr} path directly.
 *
 * <p>JFR ({@code jdk.jfr}) is present on standard OpenJDK 11+. {@link #isAvailable()} probes for it so the
 * agent can advertise {@link LiveProtocol#CAP_JFR} and the UI can hide the tool when it is missing. All methods
 * here run on the agent's single dispatch thread, so the lone {@code active} reference needs no extra locking.
 */
final class JfrController {

    private static final AtomicInteger DUMP_COUNTER = new AtomicInteger();

    /** Category bit -> candidate JFR event names; only those present on this JVM are applied (version-robust). */
    private static final String[] CPU_EVENTS = {"jdk.ExecutionSample", "jdk.NativeMethodSample"};
    private static final String[] ALLOC_EVENTS = {
            "jdk.ObjectAllocationSample", "jdk.ObjectAllocationInNewTLAB", "jdk.ObjectAllocationOutsideTLAB"};
    private static final String[] LOCK_EVENTS = {"jdk.JavaMonitorEnter", "jdk.JavaMonitorWait", "jdk.ThreadPark"};
    private static final String[] EXCEPTION_EVENTS = {"jdk.JavaExceptionThrow", "jdk.JavaErrorThrow"};

    private Recording active;

    /** Whether JFR is usable on this runtime (so the agent can advertise {@link LiveProtocol#CAP_JFR}). */
    static boolean isAvailable() {
        try {
            Class.forName("jdk.jfr.Recording");
            return FlightRecorder.isAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Starts a recording from a base profile ({@code "default"}/{@code "profile"}) plus category toggles. */
    void start(String profile, int categoryMask, int maxSizeMb) throws IOException {
        if (active != null) {
            throw new IllegalStateException("a recording is already in progress");
        }
        Recording recording;
        try {
            recording = new Recording(Configuration.getConfiguration(profile));
        } catch (Exception e) {
            throw new IOException("could not start JFR profile '" + profile + "': " + e.getMessage());
        }
        recording.setName("JStudio Recorder");
        recording.setToDisk(true);
        if (maxSizeMb > 0) {
            recording.setMaxSize((long) maxSizeMb * 1024 * 1024);
        }

        Set<String> available = availableEventNames();
        applyCategory(recording, available, (categoryMask & LiveProtocol.JFR_CAT_CPU) != 0, null, CPU_EVENTS);
        applyCategory(recording, available, (categoryMask & LiveProtocol.JFR_CAT_ALLOC) != 0, null, ALLOC_EVENTS);
        applyCategory(recording, available, (categoryMask & LiveProtocol.JFR_CAT_LOCKS) != 0,
                Duration.ofMillis(10), LOCK_EVENTS);
        applyCategory(recording, available, (categoryMask & LiveProtocol.JFR_CAT_EXCEPTIONS) != 0, null,
                EXCEPTION_EVENTS);

        recording.start();
        active = recording;
    }

    /** Dumps the in-progress recording's buffer to a fresh file without stopping it. */
    String snapshot() throws IOException {
        if (active == null) {
            throw new IllegalStateException("no recording in progress");
        }
        return dump(active);
    }

    /** Stops the active recording, dumps it, closes it, and returns the file path. */
    String stop() throws IOException {
        if (active == null) {
            throw new IllegalStateException("no recording in progress");
        }
        try (Recording recording = active) {
            active = null;
            recording.stop();
            return dump(recording);
        }
    }

    /** Stops and discards any active recording without dumping (used on disconnect, so nothing is orphaned). */
    void discard() {
        Recording recording = active;
        active = null;
        if (recording != null) {
            try {
                recording.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String dump(Recording recording) throws IOException {
        File out = new File(System.getProperty("java.io.tmpdir"),
                "jstudio-jfr-" + DUMP_COUNTER.incrementAndGet() + ".jfr");
        Path path = out.toPath();
        try {
            recording.dump(path);
        } catch (Exception e) {
            throw new IOException("JFR dump failed: " + e.getMessage());
        }
        return out.getAbsolutePath();
    }

    private static void applyCategory(Recording recording, Set<String> available, boolean enabled,
                                      Duration threshold, String[] eventNames) {
        for (String name : eventNames) {
            if (!available.contains(name)) {
                continue;
            }
            if (enabled) {
                jdk.jfr.EventSettings settings = recording.enable(name).withStackTrace();
                if (threshold != null) {
                    settings.withThreshold(threshold);
                }
            } else {
                recording.disable(name);
            }
        }
    }

    private static Set<String> availableEventNames() {
        Set<String> names = new HashSet<>();
        for (EventType type : FlightRecorder.getFlightRecorder().getEventTypes()) {
            names.add(type.getName());
        }
        return names;
    }
}
