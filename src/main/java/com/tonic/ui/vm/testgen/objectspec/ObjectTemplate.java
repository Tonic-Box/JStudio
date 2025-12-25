package com.tonic.ui.vm.testgen.objectspec;

public class ObjectTemplate {

    private String name;
    private String description;
    private String typeName;
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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }

    public ObjectSpec getSpec() { return spec; }
    public void setSpec(ObjectSpec spec) {
        this.spec = spec;
        this.modifiedAt = System.currentTimeMillis();
    }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(long modifiedAt) { this.modifiedAt = modifiedAt; }

    @Override
    public String toString() {
        return name + " (" + typeName + ")";
    }
}
