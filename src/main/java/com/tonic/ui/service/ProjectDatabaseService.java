package com.tonic.ui.service;

import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.StatusMessageEvent;
import com.tonic.ui.model.Bookmark;
import com.tonic.ui.model.Comment;
import com.tonic.ui.model.ProjectDatabase;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.util.JsonSerializer;
import lombok.Getter;

import javax.swing.Timer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class ProjectDatabaseService {

    private static final ProjectDatabaseService INSTANCE = new ProjectDatabaseService();

    private ProjectDatabase currentDatabase;
    @Getter
    private File projectFile;
    @Getter
    private boolean dirty;
    private Timer autoSaveTimer;
    private final List<DatabaseChangeListener> listeners = new ArrayList<>();

    private ProjectDatabaseService() {
    }

    public static ProjectDatabaseService getInstance() {
        return INSTANCE;
    }

    public void create(File targetFile) {
        currentDatabase = new ProjectDatabase(targetFile);
        currentDatabase.setTargetHash(computeFileHash(targetFile));
        projectFile = ProjectDatabase.getDefaultProjectFile(targetFile);
        dirty = false;
        notifyListeners();
    }

    public void open(File jstudioFile) throws IOException {
        currentDatabase = JsonSerializer.load(jstudioFile);
        projectFile = jstudioFile;
        dirty = false;
        notifyListeners();
        EventBus.getInstance().post(new StatusMessageEvent(this,
            "Loaded project: " + projectFile.getName()));
    }

    public void save() throws IOException {
        if (projectFile == null) {
            throw new IOException("No project file set");
        }
        saveAs(projectFile);
    }

    public void saveAs(File file) throws IOException {
        if (currentDatabase == null) {
            throw new IOException("No project database to save");
        }
        currentDatabase.touch();
        JsonSerializer.save(currentDatabase, file);
        projectFile = file;
        dirty = false;
        notifyListeners();
        EventBus.getInstance().post(new StatusMessageEvent(this,
            "Saved project: " + file.getName()));
    }

    public void close() {
        currentDatabase = null;
        projectFile = null;
        dirty = false;
        stopAutoSave();
        notifyListeners();
    }

    public void markDirty() {
        if (!dirty) {
            dirty = true;
            notifyListeners();
        }
    }

    public boolean hasDatabase() {
        return currentDatabase != null;
    }

    public ProjectDatabase getDatabase() {
        return currentDatabase;
    }

    public void addComment(Comment comment) {
        if (currentDatabase != null) {
            currentDatabase.getComments().addComment(comment);
            markDirty();
        }
    }

    public void removeComment(String id) {
        if (currentDatabase != null) {
            currentDatabase.getComments().removeComment(id);
            markDirty();
        }
    }

    public void updateComment(String id, String newText) {
        if (currentDatabase != null) {
            currentDatabase.getComments().updateComment(id, newText);
            markDirty();
        }
    }

    public List<Comment> getCommentsForClass(String className) {
        if (currentDatabase != null) {
            return currentDatabase.getComments().getCommentsForClass(className);
        }
        return new ArrayList<>();
    }

    public void addBookmark(Bookmark bookmark) {
        if (currentDatabase != null) {
            currentDatabase.getBookmarks().addBookmark(bookmark);
            markDirty();
        }
    }

    public void removeBookmark(String id) {
        if (currentDatabase != null) {
            currentDatabase.getBookmarks().removeBookmark(id);
            markDirty();
        }
    }

    public void setQuickSlot(int slot, Bookmark bookmark) {
        if (currentDatabase != null) {
            currentDatabase.getBookmarks().setQuickSlot(slot, bookmark);
            markDirty();
        }
    }

    public Bookmark getQuickSlot(int slot) {
        if (currentDatabase != null) {
            return currentDatabase.getBookmarks().getQuickSlot(slot);
        }
        return null;
    }

    public List<Bookmark> getAllBookmarks() {
        if (currentDatabase != null) {
            return currentDatabase.getBookmarks().getAll();
        }
        return new ArrayList<>();
    }

    public List<Bookmark> getBookmarksForClass(String className) {
        if (currentDatabase != null) {
            return currentDatabase.getBookmarks().getForClass(className);
        }
        return new ArrayList<>();
    }

    public void addRename(String original, String renamed) {
        if (currentDatabase != null) {
            currentDatabase.addRename(original, renamed);
            markDirty();
        }
    }

    public String getRenamedName(String original) {
        if (currentDatabase != null) {
            return currentDatabase.getRenamedName(original);
        }
        return original;
    }

    public void enableAutoSave(int intervalSeconds) {
        stopAutoSave();
        if (intervalSeconds > 0) {
            autoSaveTimer = new Timer(intervalSeconds * 1000, e -> {
                if (dirty && projectFile != null) {
                    try {
                        save();
                        EventBus.getInstance().post(new StatusMessageEvent(this, "Auto-saved project"));
                    } catch (IOException ex) {
                        System.err.println("Auto-save failed: " + ex.getMessage());
                    }
                }
            });
            autoSaveTimer.start();
        }
    }

    public void stopAutoSave() {
        if (autoSaveTimer != null) {
            autoSaveTimer.stop();
            autoSaveTimer = null;
        }
    }

    public void initializeForProject(ProjectModel project) {
        if (project == null || project.getSourceFile() == null) {
            return;
        }
        File sourceFile = project.getSourceFile();
        File defaultDbFile = ProjectDatabase.getDefaultProjectFile(sourceFile);
        if (defaultDbFile.exists()) {
            try {
                open(defaultDbFile);
            } catch (IOException e) {
                System.err.println("Failed to load existing project database: " + e.getMessage());
                create(sourceFile);
            }
        } else {
            create(sourceFile);
        }
    }

    public void addListener(DatabaseChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(DatabaseChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (DatabaseChangeListener listener : listeners) {
            listener.onDatabaseChanged(currentDatabase, dirty);
        }
    }

    private String computeFileHash(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    md.update(buffer, 0, read);
                }
            }
            byte[] hash = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public interface DatabaseChangeListener {
        void onDatabaseChanged(ProjectDatabase database, boolean dirty);
    }
}
