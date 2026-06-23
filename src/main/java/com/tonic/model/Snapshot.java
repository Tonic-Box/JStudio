package com.tonic.model;

import lombok.Getter;

import java.util.Map;

/**
 * One Local History restore point: a labeled, timestamped manifest mapping each user class (and resource) to the
 * content hash of its stored bytes. The bytes themselves live as deduplicated blobs in the project's history store;
 * a snapshot only references them, so an unchanged class costs nothing across snapshots.
 */
@Getter
public final class Snapshot {

    /** What caused a snapshot; drives the default label and the row icon in the history panel. */
    @Getter
    public enum Trigger {
        MANUAL("Checkpoint"),
        SAVE("Saved"),
        BASELINE("Opened"),
        RENAME("Rename"),
        DEAD_CODE("Remove Dead Code"),
        DEOBFUSCATE("Deobfuscate"),
        SCRIPT("Script Transform"),
        RECOMPILE("Recompile"),
        DELETE("Delete");

        private final String defaultLabel;

        Trigger(String defaultLabel) {
            this.defaultLabel = defaultLabel;
        }
    }

    private final String id;
    private final long timestampMs;
    private final String label;
    private final Trigger trigger;
    private final Map<String, String> classes;   // internal class name -> blob hash
    private final Map<String, String> resources;  // resource path -> blob hash

    public Snapshot(String id, long timestampMs, String label, Trigger trigger,
                    Map<String, String> classes, Map<String, String> resources) {
        this.id = id;
        this.timestampMs = timestampMs;
        this.label = label;
        this.trigger = trigger;
        this.classes = classes;
        this.resources = resources;
    }
}
