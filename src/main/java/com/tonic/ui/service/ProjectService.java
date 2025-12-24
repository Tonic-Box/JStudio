package com.tonic.ui.service;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.ui.event.EventBus;
import com.tonic.ui.util.Settings;
import com.tonic.ui.event.events.ProjectLoadedEvent;
import com.tonic.ui.event.events.ProjectUpdatedEvent;
import com.tonic.ui.event.events.StatusMessageEvent;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.ProjectModel;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Service for loading and managing projects (JARs, directories, class files).
 */
public class ProjectService {

    private static final ProjectService INSTANCE = new ProjectService();

    private ProjectModel currentProject;

    private ProjectService() {
    }

    public static ProjectService getInstance() {
        return INSTANCE;
    }

    /**
     * Create a new empty project.
     */
    public ProjectModel createProject(String name) {
        ProjectModel project = new ProjectModel();
        project.setProjectName(name);
        this.currentProject = project;
        return project;
    }

    /**
     * Load a JAR file into a new project.
     */
    public ProjectModel loadJar(File jarFile, ProgressCallback progress) throws IOException {
        if (!jarFile.exists()) {
            throw new IOException("File not found: " + jarFile.getAbsolutePath());
        }

        String name = jarFile.getName();
        EventBus.getInstance().post(new StatusMessageEvent(this, "Loading " + name + "..."));

        List<ClassFile> classes = new ArrayList<>();

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            List<JarEntry> classEntries = new ArrayList<>();

            // First pass: count class files
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    classEntries.add(entry);
                }
            }

            int total = classEntries.size();
            int current = 0;

            // Second pass: load classes
            for (JarEntry entry : classEntries) {
                try (InputStream is = jar.getInputStream(entry)) {
                    ClassFile cf = new ClassFile(is);
                    classes.add(cf);
                } catch (Exception e) {
                    // Log but continue loading other classes
                    System.err.println("Failed to load class: " + entry.getName() + " - " + e.getMessage());
                }

                current++;
                if (progress != null) {
                    progress.onProgress(current, total, "Loading " + entry.getName());
                }
            }
        }

        // Create project and class pool
        ProjectModel project = new ProjectModel();
        project.setProjectName(name);
        project.setSourceFile(jarFile);

        // Create class pool with JDK for recursive execution
        ClassPool pool = createClassPoolWithJdk();
        project.setClassPool(pool);

        // Add user classes (tracked separately from JDK)
        for (ClassFile cf : classes) {
            project.addClass(cf);
        }

        this.currentProject = project;

        EventBus.getInstance().post(new StatusMessageEvent(this,
                "Loaded " + classes.size() + " classes from " + name));
        EventBus.getInstance().post(new ProjectLoadedEvent(this, project));

        return project;
    }

    /**
     * Load a single class file.
     */
    public ProjectModel loadClassFile(File classFile) throws IOException {
        if (!classFile.exists()) {
            throw new IOException("File not found: " + classFile.getAbsolutePath());
        }

        byte[] data = Files.readAllBytes(classFile.toPath());
        ClassFile cf = new ClassFile(new ByteArrayInputStream(data));

        ProjectModel project = new ProjectModel();
        project.setProjectName(classFile.getName());
        project.setSourceFile(classFile);

        ClassPool pool = createClassPoolWithJdk();
        project.setClassPool(pool);
        project.addClass(cf);

        this.currentProject = project;

        EventBus.getInstance().post(new StatusMessageEvent(this,
                "Loaded " + cf.getClassName()));
        EventBus.getInstance().post(new ProjectLoadedEvent(this, project));

        return project;
    }

    /**
     * Load a directory of class files.
     */
    public ProjectModel loadDirectory(File directory, ProgressCallback progress) throws IOException {
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IOException("Not a valid directory: " + directory.getAbsolutePath());
        }

        EventBus.getInstance().post(new StatusMessageEvent(this, "Loading " + directory.getName() + "..."));

        List<Path> classPaths = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(directory.toPath())) {
            paths.filter(p -> p.toString().endsWith(".class"))
                    .forEach(classPaths::add);
        }

        List<ClassFile> classes = new ArrayList<>();
        int total = classPaths.size();
        int current = 0;

        for (Path path : classPaths) {
            try {
                byte[] data = Files.readAllBytes(path);
                ClassFile cf = new ClassFile(new ByteArrayInputStream(data));
                classes.add(cf);
            } catch (Exception e) {
                System.err.println("Failed to load class: " + path + " - " + e.getMessage());
            }

            current++;
            if (progress != null) {
                progress.onProgress(current, total, "Loading " + path.getFileName());
            }
        }

        ProjectModel project = new ProjectModel();
        project.setProjectName(directory.getName());
        project.setSourceFile(directory);

        ClassPool pool = createClassPoolWithJdk();
        project.setClassPool(pool);

        for (ClassFile cf : classes) {
            project.addClass(cf);
        }

        this.currentProject = project;

        EventBus.getInstance().post(new StatusMessageEvent(this,
                "Loaded " + classes.size() + " classes from " + directory.getName()));
        EventBus.getInstance().post(new ProjectLoadedEvent(this, project));

        return project;
    }

    /**
     * Add a class to the current project.
     */
    public ClassEntryModel addClass(byte[] classData) throws IOException {
        if (currentProject == null) {
            createProject("Untitled");
        }

        ClassFile cf = new ClassFile(new ByteArrayInputStream(classData));
        return currentProject.addClass(cf);
    }

    /**
     * Append a JAR file to the current project.
     */
    public int appendJar(File jarFile, ProgressCallback progress) throws IOException {
        if (!jarFile.exists()) {
            throw new IOException("File not found: " + jarFile.getAbsolutePath());
        }

        if (currentProject == null) {
            loadJar(jarFile, progress);
            return currentProject.getClassCount();
        }

        EventBus.getInstance().post(new StatusMessageEvent(this, "Appending " + jarFile.getName() + "..."));

        int addedCount = 0;

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            List<JarEntry> classEntries = new ArrayList<>();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    classEntries.add(entry);
                }
            }

            int total = classEntries.size();
            int current = 0;

            for (JarEntry entry : classEntries) {
                try (InputStream is = jar.getInputStream(entry)) {
                    ClassFile cf = new ClassFile(is);
                    currentProject.addClass(cf);
                    addedCount++;
                } catch (Exception e) {
                    System.err.println("Failed to load class: " + entry.getName() + " - " + e.getMessage());
                }

                current++;
                if (progress != null) {
                    progress.onProgress(current, total, "Appending " + entry.getName());
                }
            }
        }

        EventBus.getInstance().post(new StatusMessageEvent(this,
                "Appended " + addedCount + " classes from " + jarFile.getName()));
        EventBus.getInstance().post(new ProjectUpdatedEvent(this, currentProject, addedCount));

        return addedCount;
    }

    /**
     * Append a single class file to the current project.
     */
    public int appendClassFile(File classFile) throws IOException {
        if (!classFile.exists()) {
            throw new IOException("File not found: " + classFile.getAbsolutePath());
        }

        if (currentProject == null) {
            loadClassFile(classFile);
            return 1;
        }

        byte[] data = Files.readAllBytes(classFile.toPath());
        ClassFile cf = new ClassFile(new ByteArrayInputStream(data));

        currentProject.addClass(cf);

        EventBus.getInstance().post(new StatusMessageEvent(this, "Appended " + cf.getClassName()));
        EventBus.getInstance().post(new ProjectUpdatedEvent(this, currentProject, 1));

        return 1;
    }

    /**
     * Append a directory of class files to the current project.
     */
    public int appendDirectory(File directory, ProgressCallback progress) throws IOException {
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IOException("Not a valid directory: " + directory.getAbsolutePath());
        }

        if (currentProject == null) {
            loadDirectory(directory, progress);
            return currentProject.getClassCount();
        }

        EventBus.getInstance().post(new StatusMessageEvent(this, "Appending " + directory.getName() + "..."));

        List<Path> classPaths = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(directory.toPath())) {
            paths.filter(p -> p.toString().endsWith(".class"))
                    .forEach(classPaths::add);
        }

        int addedCount = 0;
        int total = classPaths.size();
        int current = 0;

        for (Path path : classPaths) {
            try {
                byte[] data = Files.readAllBytes(path);
                ClassFile cf = new ClassFile(new ByteArrayInputStream(data));
                currentProject.addClass(cf);
                addedCount++;
            } catch (Exception e) {
                System.err.println("Failed to load class: " + path + " - " + e.getMessage());
            }

            current++;
            if (progress != null) {
                progress.onProgress(current, total, "Appending " + path.getFileName());
            }
        }

        EventBus.getInstance().post(new StatusMessageEvent(this,
                "Appended " + addedCount + " classes from " + directory.getName()));
        EventBus.getInstance().post(new ProjectUpdatedEvent(this, currentProject, addedCount));

        return addedCount;
    }

    /**
     * Close the current project.
     */
    public void closeProject() {
        if (currentProject != null) {
            currentProject.clear();
            currentProject = null;
        }
    }

    /**
     * Get the current project.
     */
    public ProjectModel getCurrentProject() {
        return currentProject;
    }

    /**
     * Check if there is a project open.
     */
    public boolean hasProject() {
        return currentProject != null && currentProject.getClassCount() > 0;
    }

    /**
     * Create a class pool, optionally with JDK classes loaded.
     * When JDK classes are loaded, recursive execution can step into JDK methods.
     */
    private ClassPool createClassPoolWithJdk() {
        if (!Settings.getInstance().isLoadJdkClassesEnabled()) {
            return new ClassPool(true);
        }
        try {
            return new ClassPool();
        } catch (IOException e) {
            System.err.println("Failed to load JDK classes, falling back to empty pool: " + e.getMessage());
            return new ClassPool(true);
        }
    }

    /**
     * Progress callback interface.
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int current, int total, String message);
    }
}
