package com.tonic.ui.vm.testgen.objectspec;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ObjectTemplate {

    private String name;
    private String description;
    private String typeName;
    @Setter(lombok.AccessLevel.NONE)
    private ObjectSpec spec;
    private long createdAt;
    private long modifiedAt;

    public ObjectTemplate() {
        this.createdAt = System.currentTimeMillis();
        this.modifiedAt = this.createdAt;
    }

    public ObjectTemplate(String name, String typeName, ObjectSpec spec) {
        this.name = name;
        this.typeName = typeName;
        this.spec = spec;
        this.createdAt = System.currentTimeMillis();
        this.modifiedAt = this.createdAt;
    }

    public String getDisplayName() {
        if (description != null && !description.isEmpty()) {
            return name + " - " + description;
        }
        return name;
    }

    public void setSpec(ObjectSpec spec) {
        this.spec = spec;
        this.modifiedAt = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return name + " (" + typeName + ")";
    }
}
