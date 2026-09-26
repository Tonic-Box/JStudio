package com.tonic.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The project's user comments, indexed by id and by class; safe for concurrent use. */
public class CommentStore
{

    private final Map<String, List<Comment>> commentsByClass;
    private final Map<String, Comment> commentsById;

    /** Creates an empty store. */
    public CommentStore()
    {
        this.commentsByClass = new ConcurrentHashMap<>();
        this.commentsById = new ConcurrentHashMap<>();
    }

    /**
     * Adds a comment; ignores null and comments without a class.
     *
     * @param comment the comment to add
     */
    public void addComment(Comment comment)
    {
        if (comment == null || comment.getClassName() == null)
        {
            return;
        }
        commentsById.put(comment.getId(), comment);
        commentsByClass
                .computeIfAbsent(comment.getClassName(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(comment);
    }

    /**
     * Removes a comment, dropping its class's entry when it was the last one.
     *
     * @param id the comment's id
     */
    public void removeComment(String id)
    {
        Comment comment = commentsById.remove(id);
        if (comment != null)
        {
            List<Comment> classComments = commentsByClass.get(comment.getClassName());
            if (classComments != null)
            {
                classComments.removeIf(c -> c.getId().equals(id));
                if (classComments.isEmpty())
                {
                    commentsByClass.remove(comment.getClassName());
                }
            }
        }
    }

    /**
     * Replaces a comment's text; does nothing if the id is unknown.
     *
     * @param id the comment's id
     * @param newText the new text
     */
    public void updateComment(String id, String newText)
    {
        Comment comment = commentsById.get(id);
        if (comment != null)
        {
            comment.setText(newText);
        }
    }

    /**
     * Lists the comments on one class.
     *
     * @param className the class's internal name, with slashes
     * @return a copy of the class's comments, empty if it has none
     */
    public List<Comment> getCommentsForClass(String className)
    {
        List<Comment> comments = commentsByClass.get(className);
        return comments != null ? new ArrayList<>(comments) : Collections.emptyList();
    }

    /**
     * Lists every comment.
     *
     * @return a new list of the comments
     */
    public List<Comment> getAllComments()
    {
        return new ArrayList<>(commentsById.values());
    }

    /**
     * Counts the comments.
     *
     * @return the number of comments
     */
    public int getCommentCount()
    {
        return commentsById.size();
    }

    /** Removes every comment. */
    public void clear()
    {
        commentsByClass.clear();
        commentsById.clear();
    }

    /**
     * Replaces the contents with the given comments.
     *
     * @param comments the comments, or null to leave the store empty
     */
    public void setComments(List<Comment> comments)
    {
        clear();
        if (comments != null)
        {
            for (Comment comment : comments)
            {
                addComment(comment);
            }
        }
    }
}
