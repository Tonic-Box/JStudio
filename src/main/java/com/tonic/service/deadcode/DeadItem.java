package com.tonic.service.deadcode;

import com.tonic.analysis.common.MethodReference;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * One removable item found by {@link DeadCodeAnalyzer}: an entire dead class, a dead method, or a dead field.
 * Owner names are internal form ({@code com/foo/Bar}). For a write-only field, {@link #getWriters()} lists the
 * reachable methods whose store of the field must be patched out before the field is removed.
 */
@Getter
public final class DeadItem {

    public enum Kind {
        CLASS, METHOD, FIELD
    }

    private final Kind kind;
    private final String owner;
    private final String name;
    private final String desc;
    private final boolean writeOnly;
    private final List<MethodReference> writers;

    private DeadItem(Kind kind, String owner, String name, String desc, boolean writeOnly,
                     List<MethodReference> writers) {
        this.kind = kind;
        this.owner = owner;
        this.name = name;
        this.desc = desc;
        this.writeOnly = writeOnly;
        this.writers = writers;
    }

    static DeadItem ofClass(String owner) {
        return new DeadItem(Kind.CLASS, owner, null, null, false, Collections.emptyList());
    }

    static DeadItem ofMethod(String owner, String name, String desc) {
        return new DeadItem(Kind.METHOD, owner, name, desc, false, Collections.emptyList());
    }

    static DeadItem ofField(String owner, String name, String desc, boolean writeOnly,
                            List<MethodReference> writers) {
        return new DeadItem(Kind.FIELD, owner, name, desc, writeOnly, writers);
    }

    /** A readable one-line label for the UI, e.g. {@code doWork(I)V} or {@code count : I (write-only)}. */
    public String displayLabel() {
        switch (kind) {
            case CLASS:
                return owner.replace('/', '.');
            case METHOD:
                return name + desc;
            default:
                return name + " : " + desc + (writeOnly ? "  (write-only)" : "");
        }
    }
}
