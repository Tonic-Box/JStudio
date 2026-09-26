package com.tonic.model;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** A user comment attached to a class, member or line. */
@Getter
@Setter
public class Comment
{

    /** Where a comment is placed relative to the code. */
    public enum Type
    {
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

    /** Creates an empty line comment with a fresh id and the current time. */
    public Comment()
    {
        this.id = UUID.randomUUID().toString();
        this.lineNumber = -1;
        this.type = Type.LINE;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Creates a line comment.
     *
     * @param className the class's internal name, with slashes
     * @param lineNumber the source line, or -1 for none
     * @param text the comment text
     */
    public Comment(String className, int lineNumber, String text)
    {
        this();
        this.className = className;
        this.lineNumber = lineNumber;
        this.text = text;
    }

    /**
     * Replaces the text and updates the timestamp.
     *
     * @param text the new text
     */
    public void setText(String text)
    {
        this.text = text;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Builds the location key: the class, then #member and :line when set.
     *
     * @return the location key
     */
    public String getLocationKey()
    {
        StringBuilder key = new StringBuilder(className);
        if (memberName != null && !memberName.isEmpty())
        {
            key.append("#").append(memberName);
        }
        if (lineNumber >= 0)
        {
            key.append(":").append(lineNumber);
        }
        return key.toString();
    }

    @Override
    public String toString()
    {
        return getLocationKey() + " - " + (text.length() > 50 ? text.substring(0, 47) + "..." : text);
    }
}
