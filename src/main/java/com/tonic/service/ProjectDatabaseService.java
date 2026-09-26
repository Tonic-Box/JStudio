package com.tonic.service;

import com.tonic.event.EventBus;
import com.tonic.event.events.StatusMessageEvent;
import com.tonic.model.Bookmark;
import com.tonic.model.Comment;
import com.tonic.model.ProjectDatabase;
import com.tonic.model.ProjectModel;
import com.tonic.util.JsonSerializer;
import lombok.Getter;

import javax.swing.Timer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** The holder of the open project database (comments, bookmarks, renames), its save file, dirty state and auto-save timer. */
public class ProjectDatabaseService
{

    private static final ProjectDatabaseService INSTANCE = new ProjectDatabaseService();

    private ProjectDatabase currentDatabase;
    @Getter
    private File projectFile;
    @Getter
    private boolean dirty;
    private Timer autoSaveTimer;
    private final List<DatabaseChangeListener> listeners = new ArrayList<>();

    private ProjectDatabaseService()
    {
    }

    /** @return the shared instance */
    public static ProjectDatabaseService getInstance()
    {
        return INSTANCE;
    }

    /**
     * Starts a new, clean database for a target file, hashing the target and pointing the save file at the default location beside it.
     *
     * @param targetFile the jar, class or directory the database annotates
     */
    public void create(File targetFile)
    {
        currentDatabase = new ProjectDatabase(targetFile);
        currentDatabase.setTargetHash(computeFileHash(targetFile));
        projectFile = ProjectDatabase.getDefaultProjectFile(targetFile);
        dirty = false;
        notifyListeners();
    }

    /**
     * Loads a database from a project file and makes it current.
     *
     * @param jstudioFile the project file
     * @throws IOException if the file cannot be read or parsed
     */
    public void open(File jstudioFile) throws IOException
    {
        currentDatabase = JsonSerializer.load(jstudioFile);
        projectFile = jstudioFile;
        dirty = false;
        notifyListeners();
        EventBus.getInstance().post(new StatusMessageEvent(this, "Loaded project: " + projectFile.getName()));
    }

    /**
     * Saves the database to its current project file.
     *
     * @throws IOException if no project file is set, no database is open, or writing fails
     */
    public void save() throws IOException
    {
        if (projectFile == null)
        {
            throw new IOException("No project file set");
        }
        saveAs(projectFile);
    }

    /**
     * Saves the database to a file, which becomes the current project file.
     *
     * @param file where to write
     * @throws IOException if no database is open or writing fails
     */
    public void saveAs(File file) throws IOException
    {
        if (currentDatabase == null)
        {
            throw new IOException("No project database to save");
        }
        currentDatabase.touch();
        JsonSerializer.save(currentDatabase, file);
        projectFile = file;
        dirty = false;
        notifyListeners();
        EventBus.getInstance().post(new StatusMessageEvent(this, "Saved project: " + file.getName()));
    }

    /** Drops the current database and stops auto-save; safe to call more than once, though each call notifies listeners. */
    public void close()
    {
        currentDatabase = null;
        projectFile = null;
        dirty = false;
        stopAutoSave();
        notifyListeners();
    }

    /** Marks the database as having unsaved changes, notifying listeners only on the change from clean. */
    public void markDirty()
    {
        if (!dirty)
        {
            dirty = true;
            notifyListeners();
        }
    }

    /**
     * Reports whether a database is open.
     *
     * @return true if a database is open
     */
    public boolean hasDatabase()
    {
        return currentDatabase != null;
    }

    /** @return the open database, or null when none is open */
    public ProjectDatabase getDatabase()
    {
        return currentDatabase;
    }

    /**
     * Adds a comment and marks the database dirty; does nothing when no database is open.
     *
     * @param comment the comment
     */
    public void addComment(Comment comment)
    {
        if (currentDatabase != null)
        {
            currentDatabase.getComments().addComment(comment);
            markDirty();
        }
    }

    /**
     * Removes a comment and marks the database dirty; does nothing when no database is open.
     *
     * @param id the comment's id
     */
    public void removeComment(String id)
    {
        if (currentDatabase != null)
        {
            currentDatabase.getComments().removeComment(id);
            markDirty();
        }
    }

    /**
     * Replaces a comment's text and marks the database dirty; does nothing when no database is open.
     *
     * @param id the comment's id
     * @param newText the replacement text
     */
    public void updateComment(String id, String newText)
    {
        if (currentDatabase != null)
        {
            currentDatabase.getComments().updateComment(id, newText);
            markDirty();
        }
    }

    /**
     * Lists the comments attached to a class.
     *
     * @param className the class name the comments were stored under
     * @return the comments, or an empty list when no database is open
     */
    public List<Comment> getCommentsForClass(String className)
    {
        if (currentDatabase != null)
        {
            return currentDatabase.getComments().getCommentsForClass(className);
        }
        return new ArrayList<>();
    }

    /**
     * Adds a bookmark and marks the database dirty; does nothing when no database is open.
     *
     * @param bookmark the bookmark
     */
    public void addBookmark(Bookmark bookmark)
    {
        if (currentDatabase != null)
        {
            currentDatabase.getBookmarks().addBookmark(bookmark);
            markDirty();
        }
    }

    /**
     * Removes a bookmark and marks the database dirty; does nothing when no database is open.
     *
     * @param id the bookmark's id
     */
    public void removeBookmark(String id)
    {
        if (currentDatabase != null)
        {
            currentDatabase.getBookmarks().removeBookmark(id);
            markDirty();
        }
    }

    /**
     * Assigns a bookmark to a quick-access slot and marks the database dirty; does nothing when no database is open.
     *
     * @param slot the slot number, 0 to 9
     * @param bookmark the bookmark to put in it
     */
    public void setQuickSlot(int slot, Bookmark bookmark)
    {
        if (currentDatabase != null)
        {
            currentDatabase.getBookmarks().setQuickSlot(slot, bookmark);
            markDirty();
        }
    }

    /**
     * Lists every bookmark.
     *
     * @return the bookmarks, or an empty list when no database is open
     */
    public List<Bookmark> getAllBookmarks()
    {
        if (currentDatabase != null)
        {
            return currentDatabase.getBookmarks().getAll();
        }
        return new ArrayList<>();
    }

    /**
     * Records a user rename and marks the database dirty; does nothing when no database is open.
     *
     * @param original the original name
     * @param renamed the new name
     */
    public void addRename(String original, String renamed)
    {
        if (currentDatabase != null)
        {
            currentDatabase.addRename(original, renamed);
            markDirty();
        }
    }

    /**
     * Looks up the user's rename for a name.
     *
     * @param original the original name
     * @return the recorded rename, or the original when there is none or no database is open
     */
    public String getRenamedName(String original)
    {
        if (currentDatabase != null)
        {
            return currentDatabase.getRenamedName(original);
        }
        return original;
    }

    /**
     * Replaces any auto-save timer with one that saves a dirty database to its project file at a fixed interval, logging failures.
     *
     * @param intervalSeconds seconds between saves; zero or less leaves auto-save off
     */
    public void enableAutoSave(int intervalSeconds)
    {
        stopAutoSave();
        if (intervalSeconds > 0)
        {
            autoSaveTimer = new Timer(intervalSeconds * 1000, e ->
            {
                if (dirty && projectFile != null)
                {
                    try
                    {
                        save();
                        EventBus.getInstance().post(new StatusMessageEvent(this, "Auto-saved project"));
                    }
                    catch (IOException ex)
                    {
                        ConsoleLogService.getInstance().warn("Auto-save failed: " + ex.getMessage());
                    }
                }
            });
            autoSaveTimer.start();
        }
    }

    /** Stops the auto-save timer if one is running. */
    public void stopAutoSave()
    {
        if (autoSaveTimer != null)
        {
            autoSaveTimer.stop();
            autoSaveTimer = null;
        }
    }

    /**
     * Opens the database saved beside a project's source file, or creates a new one when there is none or it fails to load.
     *
     * @param project the project; nothing happens if it or its source file is null
     */
    public void initializeForProject(ProjectModel project)
    {
        if (project == null || project.getSourceFile() == null)
        {
            return;
        }
        File sourceFile = project.getSourceFile();
        File defaultDbFile = ProjectDatabase.getDefaultProjectFile(sourceFile);
        if (defaultDbFile.exists())
        {
            try
            {
                open(defaultDbFile);
            }
            catch (IOException e)
            {
                ConsoleLogService.getInstance().warn("Failed to load existing project database: " + e.getMessage());
                create(sourceFile);
            }
        }
        else
        {
            create(sourceFile);
        }
    }

    /**
     * Registers a listener for database changes, ignoring one already registered.
     *
     * @param listener the listener
     */
    public void addListener(DatabaseChangeListener listener)
    {
        if (!listeners.contains(listener))
        {
            listeners.add(listener);
        }
    }

    /**
     * Unregisters a listener.
     *
     * @param listener the listener
     */
    public void removeListener(DatabaseChangeListener listener)
    {
        listeners.remove(listener);
    }

    /**
     * Counts the registered listeners, for tests that check for listener leaks.
     *
     * @return the number of listeners
     */
    public int getListenerCount()
    {
        return listeners.size();
    }

    private void notifyListeners()
    {
        for (DatabaseChangeListener listener : listeners)
        {
            listener.onDatabaseChanged(currentDatabase, dirty);
        }
    }

    private String computeFileHash(File file)
    {
        if (file == null || !file.exists())
        {
            return null;
        }
        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file))
            {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = fis.read(buffer)) != -1)
                {
                    md.update(buffer, 0, read);
                }
            }
            byte[] hash = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash)
            {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /** A callback for when the database is opened, created, saved, closed or first marked dirty. */
    public interface DatabaseChangeListener
    {
        /**
         * Called after the database changes.
         *
         * @param database the current database, or null after close
         * @param dirty whether it has unsaved changes
         */
        void onDatabaseChanged(ProjectDatabase database, boolean dirty);
    }
}
