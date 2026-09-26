package com.tonic.model;

import com.tonic.parser.FieldEntry;
import com.tonic.util.AccessFlags;
import com.tonic.util.DescriptorParser;
import lombok.Getter;
import lombok.Setter;

/** The UI-side model of one field: its parsed entry, owning class, formatted type and user notes. */
@Getter
public class FieldEntryModel
{

    private final FieldEntry fieldEntry;
    private final ClassEntryModel owner;

    @Setter
    private boolean selected;
    @Setter
    private String userNotes;

    private String displayType;
    private String iconKey;

    /**
     * Wraps a parsed field and formats its type for display.
     *
     * @param fieldEntry the parsed field
     * @param owner the model of the declaring class
     */
    public FieldEntryModel(FieldEntry fieldEntry, ClassEntryModel owner)
    {
        this.fieldEntry = fieldEntry;
        this.owner = owner;
        buildDisplayData();
    }

    private void buildDisplayData()
    {
        this.displayType = DescriptorParser.formatFieldDescriptor(fieldEntry.getDesc());
        this.iconKey = "field";
    }

    /**
     * The field's name.
     *
     * @return the field name
     */
    public String getName()
    {
        return fieldEntry.getName();
    }

    /**
     * The field's type descriptor.
     *
     * @return the descriptor
     */
    public String getDescriptor()
    {
        return fieldEntry.getDesc();
    }

    /**
     * The field's access flags.
     *
     * @return the raw access flags
     */
    public int getAccessFlags()
    {
        return fieldEntry.getAccess();
    }

    /**
     * Reports whether the field is static.
     *
     * @return true if the static flag is set
     */
    public boolean isStatic()
    {
        return AccessFlags.isStatic(fieldEntry.getAccess());
    }

    /**
     * Reports whether the field is final.
     *
     * @return true if the final flag is set
     */
    public boolean isFinal()
    {
        return AccessFlags.isFinal(fieldEntry.getAccess());
    }

    /**
     * Reports whether the field is public.
     *
     * @return true if the public flag is set
     */
    public boolean isPublic()
    {
        return AccessFlags.isPublic(fieldEntry.getAccess());
    }

    /**
     * Reports whether the field is private.
     *
     * @return true if the private flag is set
     */
    public boolean isPrivate()
    {
        return AccessFlags.isPrivate(fieldEntry.getAccess());
    }

    /**
     * Reports whether the field is protected.
     *
     * @return true if the protected flag is set
     */
    public boolean isProtected()
    {
        return AccessFlags.isProtected(fieldEntry.getAccess());
    }

    /**
     * Reports whether the field is volatile.
     *
     * @return true if the volatile flag is set
     */
    public boolean isVolatile()
    {
        return AccessFlags.isVolatile(fieldEntry.getAccess());
    }

    /**
     * Reports whether the field is transient.
     *
     * @return true if the transient flag is set
     */
    public boolean isTransient()
    {
        return AccessFlags.isTransient(fieldEntry.getAccess());
    }

    @Override
    public String toString()
    {
        return displayType + " " + fieldEntry.getName();
    }
}
