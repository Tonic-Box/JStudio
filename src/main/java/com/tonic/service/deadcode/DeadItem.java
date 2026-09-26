package com.tonic.service.deadcode;

import com.tonic.analysis.common.MethodReference;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/** One removable item found by dead-code analysis: a class, method or field, with owners as internal names; a write-only field lists the writers to patch. */
@Getter
public final class DeadItem
{

    /** What a dead item is. */
    public enum Kind
    {
        CLASS, METHOD, FIELD
    }

    private final Kind kind;
    private final String owner;
    private final String name;
    private final String desc;
    private final boolean writeOnly;
    private final List<MethodReference> writers;

    private DeadItem(Kind kind, String owner, String name, String desc, boolean writeOnly, List<MethodReference> writers)
    {
        this.kind = kind;
        this.owner = owner;
        this.name = name;
        this.desc = desc;
        this.writeOnly = writeOnly;
        this.writers = writers;
    }

    static DeadItem ofClass(String owner)
    {
        return new DeadItem(Kind.CLASS, owner, null, null, false, Collections.emptyList());
    }

    static DeadItem ofMethod(String owner, String name, String desc)
    {
        return new DeadItem(Kind.METHOD, owner, name, desc, false, Collections.emptyList());
    }

    static DeadItem ofField(String owner, String name, String desc, boolean writeOnly, List<MethodReference> writers)
    {
        return new DeadItem(Kind.FIELD, owner, name, desc, writeOnly, writers);
    }

    /**
     * Builds a one-line label for the UI.
     *
     * @return the dotted class name, the method name with descriptor, or the field name and descriptor marked when write-only
     */
    public String displayLabel()
    {
        switch (kind)
        {
            case CLASS:
                return owner.replace('/', '.');
            case METHOD:
                return name + desc;
            default:
                return name + " : " + desc + (writeOnly ? "  (write-only)" : "");
        }
    }
}
