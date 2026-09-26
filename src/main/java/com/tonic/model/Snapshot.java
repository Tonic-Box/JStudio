package com.tonic.model;

import lombok.Getter;

import java.util.Map;

/** One Local History restore point: a labeled, timestamped manifest mapping each user class and resource to the content hash of its stored bytes. */
@Getter
public final class Snapshot
{

    /** What caused a snapshot; drives the default label and the row icon in the history panel. */
    @Getter
    public enum Trigger
    {
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

        Trigger(String defaultLabel)
        {
            this.defaultLabel = defaultLabel;
        }
    }

    private final String id;
    private final long timestampMs;
    private final String label;
    private final Trigger trigger;
    private final Map<String, String> classes;
    private final Map<String, String> resources;

    /**
     * Creates a snapshot record.
     *
     * @param id the snapshot's id
     * @param timestampMs when it was taken, in epoch milliseconds
     * @param label the label shown in the history panel
     * @param trigger what caused it
     * @param classes the content hash of each user class's bytes, keyed by internal name
     * @param resources the content hash of each resource's bytes, keyed by path
     */
    public Snapshot(String id, long timestampMs, String label, Trigger trigger, Map<String, String> classes, Map<String, String> resources)
    {
        this.id = id;
        this.timestampMs = timestampMs;
        this.label = label;
        this.trigger = trigger;
        this.classes = classes;
        this.resources = resources;
    }
}
