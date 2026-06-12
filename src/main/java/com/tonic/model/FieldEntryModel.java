package com.tonic.model;

import com.tonic.parser.FieldEntry;
import com.tonic.util.AccessFlags;
import com.tonic.util.DescriptorParser;
import lombok.Getter;
import lombok.Setter;


@Getter
public class FieldEntryModel {

    private final FieldEntry fieldEntry;
    private final ClassEntryModel owner;

    // UI state
    @Setter
    private boolean selected;
    @Setter
    private String userNotes;

    // Display data
    private String displayType;
    private String iconKey;

    public FieldEntryModel(FieldEntry fieldEntry, ClassEntryModel owner) {
        this.fieldEntry = fieldEntry;
        this.owner = owner;
        buildDisplayData();
    }

    private void buildDisplayData() {
        this.displayType = DescriptorParser.formatFieldDescriptor(fieldEntry.getDesc());
        this.iconKey = "field";
    }

    // FieldEntry delegated methods

    public String getName() {
        return fieldEntry.getName();
    }

    public String getDescriptor() {
        return fieldEntry.getDesc();
    }

    public int getAccessFlags() {
        return fieldEntry.getAccess();
    }

    public boolean isStatic() {
        return AccessFlags.isStatic(fieldEntry.getAccess());
    }

    public boolean isFinal() {
        return AccessFlags.isFinal(fieldEntry.getAccess());
    }

    public boolean isPublic() {
        return AccessFlags.isPublic(fieldEntry.getAccess());
    }

    public boolean isPrivate() {
        return AccessFlags.isPrivate(fieldEntry.getAccess());
    }

    public boolean isProtected() {
        return AccessFlags.isProtected(fieldEntry.getAccess());
    }

    public boolean isVolatile() {
        return AccessFlags.isVolatile(fieldEntry.getAccess());
    }

    public boolean isTransient() {
        return AccessFlags.isTransient(fieldEntry.getAccess());
    }

    @Override
    public String toString() {
        return displayType + " " + fieldEntry.getName();
    }
}
