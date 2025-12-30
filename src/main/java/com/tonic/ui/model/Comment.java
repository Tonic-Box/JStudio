package com.tonic.ui.model;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class Comment {

    public enum Type {
        LINE,
        BLOCK,
        PRE_METHOD,
        POST_METHOD,
        CLASS
    }

    private String id;
    private String className;
    private String memberName;
    private int lineNumber;
    private String text;
    private Type type;
    private long timestamp;

    public Comment() {
        this.id = UUID.randomUUID().toString();
        this.lineNumber = -1;
        this.type = Type.LINE;
        this.timestamp = System.currentTimeMillis();
    }

    public Comment(String className, int lineNumber, String text) {
        this();
        this.className = className;
        this.lineNumber = lineNumber;
        this.text = text;
    }

    public Comment(String className, String memberName, int lineNumber, String text, Type type) {
        this();
        this.className = className;
        this.memberName = memberName;
        this.lineNumber = lineNumber;
        this.text = text;
        this.type = type;
    }

    public void setText(String text) {
        this.text = text;
        this.timestamp = System.currentTimeMillis();
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

    @Override
    public String toString() {
        return getLocationKey() + " - " + (text.length() > 50 ? text.substring(0, 47) + "..." : text);
    }
}
