package com.tonic.ui.vm.testgen.objectspec;

import lombok.Getter;
import lombok.Setter;

/** A named, saved object spec for one type, with creation and modification times. */
@Getter
@Setter
public class ObjectTemplate
{

    private String name;
    private String description;
    private String typeName;
    @Setter(lombok.AccessLevel.NONE)
    private ObjectSpec spec;
    private long createdAt;
    private long modifiedAt;

    /** Creates an empty template stamped with the current time. */
    public ObjectTemplate()
    {
        this.createdAt = System.currentTimeMillis();
        this.modifiedAt = this.createdAt;
    }

    /**
     * Creates a template stamped with the current time.
     *
     * @param name the template's name, its key in the manager
     * @param typeName the class's internal name, with slashes
     * @param spec the spec it saves
     */
    public ObjectTemplate(String name, String typeName, ObjectSpec spec)
    {
        this.name = name;
        this.typeName = typeName;
        this.spec = spec;
        this.createdAt = System.currentTimeMillis();
        this.modifiedAt = this.createdAt;
    }

    /**
     * Builds the name shown in template lists.
     *
     * @return the name, followed by the description when there is one
     */
    public String getDisplayName()
    {
        if (description != null && !description.isEmpty())
        {
            return name + " - " + description;
        }
        return name;
    }

    /**
     * Replaces the saved spec and updates the modification time.
     *
     * @param spec the new spec
     */
    public void setSpec(ObjectSpec spec)
    {
        this.spec = spec;
        this.modifiedAt = System.currentTimeMillis();
    }

    @Override
    public String toString()
    {
        return name + " (" + typeName + ")";
    }
}
