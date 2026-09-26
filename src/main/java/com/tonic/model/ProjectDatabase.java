package com.tonic.model;

import lombok.Getter;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** The saved per-target project data (comments, bookmarks, renames and metadata) stored beside the target in a .jstudio file. */
@Getter
public class ProjectDatabase
{

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

    /** Creates an empty database stamped with the current format version and time. */
    public ProjectDatabase()
    {
        this.version = VERSION;
        this.created = System.currentTimeMillis();
        this.modified = this.created;
        this.comments = new CommentStore();
        this.bookmarks = new BookmarkStore();
        this.renames = new HashMap<>();
        this.metadata = new HashMap<>();
    }

    /**
     * Creates an empty database for a target.
     *
     * @param targetFile the analyzed jar or class file
     */
    public ProjectDatabase(File targetFile)
    {
        this();
        this.targetPath = targetFile.getAbsolutePath();
    }

    /**
     * Sets the format version read from disk.
     *
     * @param version the format version
     */
    public void setVersion(String version)
    {
        this.version = version;
    }

    /**
     * Sets the target's path.
     *
     * @param targetPath the target's absolute path
     */
    public void setTargetPath(String targetPath)
    {
        this.targetPath = targetPath;
    }

    /**
     * Sets the hash of the target's contents.
     *
     * @param targetHash the content hash
     */
    public void setTargetHash(String targetHash)
    {
        this.targetHash = targetHash;
    }

    /**
     * Sets the creation time.
     *
     * @param created the creation time in epoch milliseconds
     */
    public void setCreated(long created)
    {
        this.created = created;
    }

    /**
     * Sets the last-modified time.
     *
     * @param modified the modification time in epoch milliseconds
     */
    public void setModified(long modified)
    {
        this.modified = modified;
    }

    /** Sets the last-modified time to now. */
    public void touch()
    {
        this.modified = System.currentTimeMillis();
    }

    /**
     * Replaces the comment store.
     *
     * @param comments the store, or null for an empty one
     */
    public void setComments(CommentStore comments)
    {
        this.comments = comments != null ? comments : new CommentStore();
    }

    /**
     * Replaces the bookmark store.
     *
     * @param bookmarks the store, or null for an empty one
     */
    public void setBookmarks(BookmarkStore bookmarks)
    {
        this.bookmarks = bookmarks != null ? bookmarks : new BookmarkStore();
    }

    /**
     * Replaces the rename table.
     *
     * @param renames the original to renamed name map, or null for an empty one
     */
    public void setRenames(Map<String, String> renames)
    {
        this.renames = renames != null ? renames : new HashMap<>();
    }

    /**
     * Records a rename and touches the modified time.
     *
     * @param original the original name
     * @param renamed the new name
     */
    public void addRename(String original, String renamed)
    {
        renames.put(original, renamed);
        touch();
    }

    /**
     * Looks up a rename.
     *
     * @param original the original name
     * @return the recorded new name, or the original if it was never renamed
     */
    public String getRenamedName(String original)
    {
        return renames.getOrDefault(original, original);
    }

    /**
     * Replaces the free-form metadata.
     *
     * @param metadata the metadata, or null for an empty map
     */
    public void setMetadata(Map<String, Object> metadata)
    {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    /**
     * The target's file name.
     *
     * @return the file name, or Untitled when no target is set
     */
    public String getTargetFileName()
    {
        if (targetPath == null)
        {
            return "Untitled";
        }
        File f = new File(targetPath);
        return f.getName();
    }

    /**
     * Derives the project file name for a target: its name without the extension, plus .jstudio.
     *
     * @param targetFile the analyzed jar or class file
     * @return the project file name
     */
    public static String getProjectFileName(File targetFile)
    {
        String name = targetFile.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0)
        {
            name = name.substring(0, dot);
        }
        return name + FILE_EXTENSION;
    }

    /**
     * Locates the project file beside a target.
     *
     * @param targetFile the analyzed jar or class file
     * @return the project file in the target's directory
     */
    public static File getDefaultProjectFile(File targetFile)
    {
        return new File(targetFile.getParentFile(), getProjectFileName(targetFile));
    }

    @Override
    public String toString()
    {
        return "ProjectDatabase{" +
                "target=" + getTargetFileName() +
                ", comments=" + comments.getCommentCount() +
                ", bookmarks=" + bookmarks.getBookmarkCount() +
                ", renames=" + renames.size() +
                '}';
    }
}
