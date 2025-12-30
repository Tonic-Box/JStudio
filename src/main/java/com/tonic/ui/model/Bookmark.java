package com.tonic.ui.model;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class Bookmark {

    public static final int NO_SLOT = -1;

    private String id;
    private String name;
    private String className;
    private String memberName;
    private int lineNumber;
    private int slot;
    private long timestamp;
    private String notes;

    public Bookmark() {
        this.id = UUID.randomUUID().toString();
        this.lineNumber = -1;
        this.slot = NO_SLOT;
        this.timestamp = System.currentTimeMillis();
    }

    public Bookmark(String className, String name) {
        this();
        this.className = className;
        this.name = name;
    }

    public Bookmark(String className, String memberName, int lineNumber, String name) {
        this();
        this.className = className;
        this.memberName = memberName;
        this.lineNumber = lineNumber;
        this.name = name;
    }

    public boolean hasSlot() {
        return slot >= 0 && slot <= 9;
    }

    public String getLocationKey() {
        StringBuilder key = new StringBuilder(className);
        if (memberName != null && !memberName.isEmpty()) {
            key.append("#").append(memberName);
        }
        if (lineNumber >= 0) {
            key.append(":").append(lineNumber);
        }
        return key.toString();
    }

    public String getDisplayName() {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        String simple = className;
        int lastSlash = className.lastIndexOf('/');
        if (lastSlash >= 0) {
            simple = className.substring(lastSlash + 1);
        }
        if (memberName != null && !memberName.isEmpty()) {
            return simple + "#" + memberName;
        }
        return simple;
    }

    @Override
    public String toString() {
        String prefix = hasSlot() ? "[" + slot + "] " : "";
        return prefix + getDisplayName();
    }
}
