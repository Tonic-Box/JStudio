package com.tonic.ui.model;

import lombok.Getter;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Getter
public class ProjectDatabase {

    public static final String VERSION = "1.0";
    public static final String FILE_EXTENSION = ".jstudio";

    private String version;
    private String targetPath;
    private String targetHash;
    private long created;
    private long modified;

    private CommentStore comments;
    private BookmarkStore bookmarks;
    private Map<String, String> renames;
    private Map<String, Object> metadata;

    public ProjectDatabase() {
        this.version = VERSION;
        this.created = System.currentTimeMillis();
        this.modified = this.created;
        this.comments = new CommentStore();
        this.bookmarks = new BookmarkStore();
        this.renames = new HashMap<>();
        this.metadata = new HashMap<>();
    }

    public ProjectDatabase(File targetFile) {
        this();
        this.targetPath = targetFile.getAbsolutePath();
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public void setTargetHash(String targetHash) {
        this.targetHash = targetHash;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public void setModified(long modified) {
        this.modified = modified;
    }

    public void touch() {
        this.modified = System.currentTimeMillis();
    }

    public void setComments(CommentStore comments) {
        this.comments = comments != null ? comments : new CommentStore();
    }

    public void setBookmarks(BookmarkStore bookmarks) {
        this.bookmarks = bookmarks != null ? bookmarks : new BookmarkStore();
    }

    public void setRenames(Map<String, String> renames) {
        this.renames = renames != null ? renames : new HashMap<>();
    }

    public void addRename(String original, String renamed) {
        renames.put(original, renamed);
        touch();
    }

    public String getRenamedName(String original) {
        return renames.getOrDefault(original, original);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    public void setMetadataValue(String key, Object value) {
        metadata.put(key, value);
        touch();
    }

    public Object getMetadataValue(String key) {
        return metadata.get(key);
    }

    public int getTotalAnnotationCount() {
        return comments.getCommentCount() + bookmarks.getBookmarkCount();
    }

    public String getTargetFileName() {
        if (targetPath == null) {
            return "Untitled";
        }
        File f = new File(targetPath);
        return f.getName();
    }

    public static String getProjectFileName(File targetFile) {
        String name = targetFile.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name + FILE_EXTENSION;
    }

    public static File getDefaultProjectFile(File targetFile) {
        return new File(targetFile.getParentFile(), getProjectFileName(targetFile));
    }

    @Override
    public String toString() {
        return "ProjectDatabase{" +
            "target=" + getTargetFileName() +
            ", comments=" + comments.getCommentCount() +
            ", bookmarks=" + bookmarks.getBookmarkCount() +
            ", renames=" + renames.size() +
            '}';
    }
}
