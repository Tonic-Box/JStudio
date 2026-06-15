package com.tonic.ui.live.recorder.jfr;

import java.time.Duration;

/** Shared, human-friendly formatting for JFR figures (bytes, counts, durations) used across the analysis UI. */
final class JfrFormat {

    private JfrFormat() {
    }

    static String bytes(double b) {
        if (b < 1024) {
            return (long) b + " B";
        }
        double kb = b / 1024.0;
        if (kb < 1024) {
            return String.format("%.0f KB", kb);
        }
        double mb = kb / 1024.0;
        return mb < 1024 ? String.format("%.1f MB", mb) : String.format("%.2f GB", mb / 1024.0);
    }

    static String count(double n) {
        return String.format("%,d", (long) n);
    }

    static String samples(long n) {
        return String.format("%,d sample%s", n, n == 1 ? "" : "s");
    }

    static String millis(double ms) {
        return String.format("%,.1f ms", ms);
    }

    static String millisFromNanos(long nanos) {
        return millis(nanos / 1_000_000.0);
    }

    static String durationSec(Duration d) {
        double s = d.toMillis() / 1000.0;
        if (s < 60) {
            return String.format("%.2fs", s);
        }
        long mins = (long) (s / 60);
        return String.format("%dm %02.0fs", mins, s - mins * 60);
    }
}
