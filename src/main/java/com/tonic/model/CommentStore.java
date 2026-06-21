package com.tonic.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CommentStore {

    private final Map<String, List<Comment>> commentsByClass;
    private final Map<String, Comment> commentsById;

    public CommentStore() {
        this.commentsByClass = new ConcurrentHashMap<>();
        this.commentsById = new ConcurrentHashMap<>();
    }

    public void addComment(Comment comment) {
        if (comment == null || comment.getClassName() == null) {
            return;
        }
        commentsById.put(comment.getId(), comment);
        commentsByClass
            .computeIfAbsent(comment.getClassName(), k -> Collections.synchronizedList(new ArrayList<>()))
            .add(comment);
    }

    public void removeComment(String id) {
        Comment comment = commentsById.remove(id);
        if (comment != null) {
            List<Comment> classComments = commentsByClass.get(comment.getClassName());
            if (classComments != null) {
                classComments.removeIf(c -> c.getId().equals(id));
                if (classComments.isEmpty()) {
                    commentsByClass.remove(comment.getClassName());
                }
            }
        }
    }

    public void updateComment(String id, String newText) {
        Comment comment = commentsById.get(id);
        if (comment != null) {
            comment.setText(newText);
        }
    }

    public List<Comment> getCommentsForClass(String className) {
        List<Comment> comments = commentsByClass.get(className);
        return comments != null ? new ArrayList<>(comments) : Collections.emptyList();
    }

    public List<Comment> getAllComments() {
        return new ArrayList<>(commentsById.values());
    }

    public int getCommentCount() {
        return commentsById.size();
    }

    public void clear() {
        commentsByClass.clear();
        commentsById.clear();
    }

    public void setComments(List<Comment> comments) {
        clear();
        if (comments != null) {
            for (Comment comment : comments) {
                addComment(comment);
            }
        }
    }
}
