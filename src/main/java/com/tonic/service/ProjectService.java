package com.tonic.service;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.live.LiveSession;
import com.tonic.live.protocol.LoadedClass;
import com.tonic.event.EventBus;
import com.tonic.util.Settings;
import com.tonic.event.events.ProjectLoadedEvent;
import com.tonic.event.events.ProjectUpdatedEvent;
import com.tonic.event.events.StatusMessageEvent;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.model.ResourceEntryModel;
import lombok.Getter;

import java.io.ByteArrayInputStream;
import java.io.File;
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

/** The holder of the current project, loading it from a jar, class file, directory or live JVM and appending to it. */
@Getter
public class ProjectService
{

    private static final ProjectService INSTANCE = new ProjectService();

    /**
     * -- GETTER --
     *  Get the current project.
     */
    private ProjectModel currentProject;

    private ProjectService()
    {
    }

    /** @return the shared instance */
    public static ProjectService getInstance()
    {
        return INSTANCE;
    }

    /**
     * Replaces the current project with a new empty one.
     *
     * @param name the project name
     * @return the new project
     */
    public ProjectModel createProject(String name)
    {
        ProjectModel project = new ProjectModel();
        project.setProjectName(name);
        this.currentProject = project;
        return project;
    }

    /**
     * Replaces the current project with the classes and resources of a jar, logging entries that fail to load.
     *
     * @param jarFile the jar
     * @param progress receives a call per entry, or null
     * @return the new project
     * @throws IOException if the file does not exist or the jar cannot be opened
     */
    public ProjectModel loadJar(File jarFile, ProgressCallback progress) throws IOException
    {
        if (!jarFile.exists())
        {
            throw new IOException("File not found: " + jarFile.getAbsolutePath());
        }

        String name = jarFile.getName();
        EventBus.getInstance().post(new StatusMessageEvent(this, "Loading " + name + "..."));

        List<ClassFile> classes = new ArrayList<>();

        List<ResourceEntryModel> resources = new ArrayList<>();

        try (JarFile jar = new JarFile(jarFile))
        {
            Enumeration<JarEntry> entries = jar.entries();
            List<JarEntry> classEntries = new ArrayList<>();
            List<JarEntry> resourceEntries = new ArrayList<>();

            while (entries.hasMoreElements())
            {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory())
                {
                    continue;
                }
                if (entry.getName().endsWith(".class"))
                {
                    classEntries.add(entry);
                }
                else
                {
                    resourceEntries.add(entry);
                }
            }

            int total = classEntries.size() + resourceEntries.size();
            int current = 0;

            for (JarEntry entry : classEntries)
            {
                try (InputStream is = jar.getInputStream(entry))
                {
                    ClassFile cf = new ClassFile(is);
                    classes.add(cf);
                }
                catch (Exception e)
                {
                    ConsoleLogService.getInstance().error("Failed to load class: " + entry.getName() + " - " + e.getMessage());
                }
                current++;
                if (progress != null)
                {
                    progress.onProgress(current, total, "Loading " + entry.getName());
                }
            }

            for (JarEntry entry : resourceEntries)
            {
                try (InputStream is = jar.getInputStream(entry))
                {
                    byte[] data = is.readAllBytes();
                    resources.add(new ResourceEntryModel(entry.getName(), data));
                }
                catch (Exception e)
                {
                    ConsoleLogService.getInstance().error("Failed to load resource: " + entry.getName() + " - " + e.getMessage());
                }
                current++;
                if (progress != null)
                {
                    progress.onProgress(current, total, "Loading " + entry.getName());
                }
            }
        }

        ProjectModel project = new ProjectModel();
        project.setProjectName(name);
        project.setSourceFile(jarFile);

        ClassPool pool = createClassPoolWithJdk();
        project.setClassPool(pool);

        for (ClassFile cf : classes)
        {
            project.addClass(cf);
        }

        for (ResourceEntryModel resource : resources)
        {
            project.addResource(resource);
        }

        this.currentProject = project;

        String message = "Loaded " + classes.size() + " classes";
        if (!resources.isEmpty())
        {
            message += ", " + resources.size() + " resources";
        }
        message += " from " + name;
        EventBus.getInstance().post(new StatusMessageEvent(this, message));
        EventBus.getInstance().post(new ProjectLoadedEvent(this, project));

        return project;
    }

    /**
     * Replaces the current project with one holding a single class.
     *
     * @param classFile the class file
     * @return the new project
     * @throws IOException if the file does not exist or cannot be read or parsed
     */
    public ProjectModel loadClassFile(File classFile) throws IOException
    {
        if (!classFile.exists())
        {
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

        EventBus.getInstance().post(new StatusMessageEvent(this, "Loaded " + cf.getClassName()));
        EventBus.getInstance().post(new ProjectLoadedEvent(this, project));

        return project;
    }

    /**
     * Replaces the current project with every class file found under a directory, logging files that fail to load.
     *
     * @param directory the root directory
     * @param progress receives a call per class file, or null
     * @return the new project
     * @throws IOException if the path is not an existing directory or cannot be walked
     */
    public ProjectModel loadDirectory(File directory, ProgressCallback progress) throws IOException
    {
        if (!directory.exists() || !directory.isDirectory())
        {
            throw new IOException("Not a valid directory: " + directory.getAbsolutePath());
        }

        EventBus.getInstance().post(new StatusMessageEvent(this, "Loading " + directory.getName() + "..."));

        List<Path> classPaths = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(directory.toPath()))
        {
            paths.filter(p -> p.toString().endsWith(".class"))
                    .forEach(classPaths::add);
        }

        List<ClassFile> classes = new ArrayList<>();
        int total = classPaths.size();
        int current = 0;

        for (Path path : classPaths)
        {
            try
            {
                byte[] data = Files.readAllBytes(path);
                ClassFile cf = new ClassFile(new ByteArrayInputStream(data));
                classes.add(cf);
            }
            catch (Exception e)
            {
                ConsoleLogService.getInstance().error("Failed to load class: " + path + " - " + e.getMessage());
            }

            current++;
            if (progress != null)
            {
                progress.onProgress(current, total, "Loading " + path.getFileName());
            }
        }

        ProjectModel project = new ProjectModel();
        project.setProjectName(directory.getName());
        project.setSourceFile(directory);

        ClassPool pool = createClassPoolWithJdk();
        project.setClassPool(pool);

        for (ClassFile cf : classes)
        {
            project.addClass(cf);
        }

        this.currentProject = project;

        EventBus.getInstance().post(new StatusMessageEvent(this, "Loaded " + classes.size() + " classes from " + directory.getName()));
        EventBus.getInstance().post(new ProjectLoadedEvent(this, project));

        return project;
    }

    /**
     * Adds a jar's classes and resources to the current project, or loads it as a new project when none is open.
     *
     * @param jarFile the jar
     * @param progress receives a call per entry, or null
     * @return the number of classes added, or the new project's class count when none was open
     * @throws IOException if the file does not exist or the jar cannot be opened
     */
    public int appendJar(File jarFile, ProgressCallback progress) throws IOException
    {
        if (!jarFile.exists())
        {
            throw new IOException("File not found: " + jarFile.getAbsolutePath());
        }

        if (currentProject == null)
        {
            loadJar(jarFile, progress);
            return currentProject.getClassCount();
        }

        EventBus.getInstance().post(new StatusMessageEvent(this, "Appending " + jarFile.getName() + "..."));

        int addedClassCount = 0;
        int addedResourceCount = 0;

        try (JarFile jar = new JarFile(jarFile))
        {
            Enumeration<JarEntry> entries = jar.entries();
            List<JarEntry> classEntries = new ArrayList<>();
            List<JarEntry> resourceEntries = new ArrayList<>();

            while (entries.hasMoreElements())
            {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory())
                {
                    continue;
                }
                if (entry.getName().endsWith(".class"))
                {
                    classEntries.add(entry);
                }
                else
                {
                    resourceEntries.add(entry);
                }
            }

            int total = classEntries.size() + resourceEntries.size();
            int current = 0;

            for (JarEntry entry : classEntries)
            {
                try (InputStream is = jar.getInputStream(entry))
                {
                    ClassFile cf = new ClassFile(is);
                    currentProject.addClass(cf);
                    addedClassCount++;
                }
                catch (Exception e)
                {
                    ConsoleLogService.getInstance().error("Failed to load class: " + entry.getName() + " - " + e.getMessage());
                }
                current++;
                if (progress != null)
                {
                    progress.onProgress(current, total, "Appending " + entry.getName());
                }
            }

            for (JarEntry entry : resourceEntries)
            {
                try (InputStream is = jar.getInputStream(entry))
                {
                    byte[] data = is.readAllBytes();
                    currentProject.addResource(new ResourceEntryModel(entry.getName(), data));
                    addedResourceCount++;
                }
                catch (Exception e)
                {
                    ConsoleLogService.getInstance().error("Failed to load resource: " + entry.getName() + " - " + e.getMessage());
                }
                current++;
                if (progress != null)
                {
                    progress.onProgress(current, total, "Appending " + entry.getName());
                }
            }
        }

        String message = "Appended " + addedClassCount + " classes";
        if (addedResourceCount > 0)
        {
            message += ", " + addedResourceCount + " resources";
        }
        message += " from " + jarFile.getName();
        EventBus.getInstance().post(new StatusMessageEvent(this, message));
        EventBus.getInstance().post(new ProjectUpdatedEvent(this, currentProject, addedClassCount));

        return addedClassCount;
    }

    /**
     * Adds a class file to the current project, or loads it as a new project when none is open.
     *
     * @param classFile the class file
     * @return 1
     * @throws IOException if the file does not exist or cannot be read or parsed
     */
    public int appendClassFile(File classFile) throws IOException
    {
        if (!classFile.exists())
        {
            throw new IOException("File not found: " + classFile.getAbsolutePath());
        }

        if (currentProject == null)
        {
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
     * Adds every class file under a directory to the current project, or loads it as a new project when none is open.
     *
     * @param directory the root directory
     * @param progress receives a call per class file, or null
     * @return the number of classes added, or the new project's class count when none was open
     * @throws IOException if the path is not an existing directory or cannot be walked
     */
    public int appendDirectory(File directory, ProgressCallback progress) throws IOException
    {
        if (!directory.exists() || !directory.isDirectory())
        {
            throw new IOException("Not a valid directory: " + directory.getAbsolutePath());
        }

        if (currentProject == null)
        {
            loadDirectory(directory, progress);
            return currentProject.getClassCount();
        }

        EventBus.getInstance().post(new StatusMessageEvent(this, "Appending " + directory.getName() + "..."));

        List<Path> classPaths = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(directory.toPath()))
        {
            paths.filter(p -> p.toString().endsWith(".class"))
                    .forEach(classPaths::add);
        }

        int addedCount = 0;
        int total = classPaths.size();
        int current = 0;

        for (Path path : classPaths)
        {
            try
            {
                byte[] data = Files.readAllBytes(path);
                ClassFile cf = new ClassFile(new ByteArrayInputStream(data));
                currentProject.addClass(cf);
                addedCount++;
            }
            catch (Exception e)
            {
                ConsoleLogService.getInstance().error("Failed to load class: " + path + " - " + e.getMessage());
            }

            current++;
            if (progress != null)
            {
                progress.onProgress(current, total, "Appending " + path.getFileName());
            }
        }

        EventBus.getInstance().post(new StatusMessageEvent(this, "Appended " + addedCount + " classes from " + directory.getName()));
        EventBus.getInstance().post(new ProjectUpdatedEvent(this, currentProject, addedCount));

        return addedCount;
    }

    /**
     * Closes the current project and builds a new one from the bytecode of every class loaded in a live JVM, skipping hidden classes.
     *
     * @param session the attached live session
     * @param includeJdk whether to pull JDK and other bootstrap-package classes too
     * @param progress receives a call per class, or null
     * @return the new project
     * @throws IOException if the session cannot enumerate its classes
     */
    public ProjectModel loadLiveProject(LiveSession session, boolean includeJdk, ProgressCallback progress) throws IOException
    {
        closeProject();
        String name = "live:" + session.getPid();
        EventBus.getInstance().post(new StatusMessageEvent(this, "Enumerating classes in " + name + "..."));

        List<LoadedClass> all = session.enumerateClasses();
        List<LoadedClass> wanted = new ArrayList<>();
        for (LoadedClass lc : all)
        {
            if (isHiddenClass(lc.getInternalName()))
            {
                continue;
            }
            if (includeJdk || !isBootstrapName(lc.getInternalName()))
            {
                wanted.add(lc);
            }
        }

        ProjectModel project = new ProjectModel();
        project.setProjectName(name);
        project.setClassPool(createClassPoolWithJdk());

        int total = wanted.size();
        int current = 0;
        int loaded = 0;
        for (LoadedClass lc : wanted)
        {
            try
            {
                byte[] data = session.fetchClassBytes(lc.getInternalName());
                project.addClass(new ClassFile(new ByteArrayInputStream(data)));
                loaded++;
            }
            catch (Exception e)
            {
                ConsoleLogService.getInstance().error("Failed to pull live class " + lc.getInternalName() + ": " + e.getMessage());
            }
            current++;
            if (progress != null)
            {
                progress.onProgress(current, total, "Pulling " + lc.getBinaryName());
            }
        }

        this.currentProject = project;
        EventBus.getInstance().post(new StatusMessageEvent(this, "Attached to " + name + " - pulled " + loaded + "/" + total + " classes"));
        EventBus.getInstance().post(new ProjectLoadedEvent(this, project));
        return project;
    }

    /**
     * Pulls classes loaded in the live JVM since the current project was built.
     *
     * @param session the attached live session
     * @param includeJdk whether to pull JDK and other bootstrap-package classes too
     * @param progress receives a call per new class, or null
     * @return the number of classes added, 0 when no project is open
     * @throws IOException if the session cannot enumerate its classes
     */
    public int refreshLiveProject(LiveSession session, boolean includeJdk, ProgressCallback progress) throws IOException
    {
        if (currentProject == null)
        {
            return 0;
        }
        List<LoadedClass> all = session.enumerateClasses();
        List<LoadedClass> missing = new ArrayList<>();
        for (LoadedClass lc : all)
        {
            if (isHiddenClass(lc.getInternalName()))
            {
                continue;
            }
            if (!includeJdk && isBootstrapName(lc.getInternalName()))
            {
                continue;
            }
            if (currentProject.getClass(lc.getInternalName()) == null)
            {
                missing.add(lc);
            }
        }
        int total = missing.size();
        int current = 0;
        int added = 0;
        for (LoadedClass lc : missing)
        {
            try
            {
                byte[] data = session.fetchClassBytes(lc.getInternalName());
                currentProject.addClass(new ClassFile(new ByteArrayInputStream(data)));
                added++;
            }
            catch (Exception e)
            {
                ConsoleLogService.getInstance().error("Failed to refresh live class " + lc.getInternalName() + ": " + e.getMessage());
            }
            current++;
            if (progress != null)
            {
                progress.onProgress(current, total, "Pulling " + lc.getBinaryName());
            }
        }
        if (added > 0)
        {
            EventBus.getInstance().post(new ProjectUpdatedEvent(this, currentProject, added));
        }
        return added;
    }

    /**
     * Adds a class captured from a live class-load event to the current project.
     *
     * @param internalName the class's internal name, with slashes
     * @param classBytes the class bytecode
     * @return the added entry, or null when there is no project, no bytes, the class is bootstrap, hidden or already present, or it fails to parse
     */
    public ClassEntryModel addCapturedLiveClass(String internalName, byte[] classBytes)
    {
        if (currentProject == null || classBytes == null || classBytes.length == 0)
        {
            return null;
        }
        if (isBootstrapName(internalName) || isHiddenClass(internalName))
        {
            return null;
        }
        if (currentProject.getClass(internalName) != null)
        {
            return null;
        }
        try
        {
            ClassEntryModel entry = currentProject.addClass(new ClassFile(new ByteArrayInputStream(classBytes)));
            EventBus.getInstance().post(new ProjectUpdatedEvent(this, currentProject, 1));
            return entry;
        }
        catch (IOException e)
        {
            ConsoleLogService.getInstance().error("Failed to add captured class " + internalName + ": " + e.getMessage());
            return null;
        }
    }

    private static boolean isBootstrapName(String internalName)
    {
        return internalName.startsWith("java/") || internalName.startsWith("javax/")
                || internalName.startsWith("jdk/") || internalName.startsWith("sun/")
                || internalName.startsWith("com/sun/") || internalName.startsWith("jakarta/")
                || internalName.startsWith("[");
    }

    private static boolean isHiddenClass(String internalName)
    {
        return internalName.contains("/0x");
    }

    /** Clears and drops the current project; safe to call when none is open. */
    public void closeProject()
    {
        if (currentProject != null)
        {
            currentProject.clear();
            currentProject = null;
        }
    }

    /**
     * Reports whether a project with at least one class is open.
     *
     * @return true if such a project is open
     */
    public boolean hasProject()
    {
        return currentProject != null && currentProject.getClassCount() > 0;
    }

    private ClassPool createClassPoolWithJdk()
    {
        if (!Settings.getInstance().isLoadJdkClassesEnabled())
        {
            return new ClassPool(true);
        }
        try
        {
            return new ClassPool();
        }
        catch (IOException e)
        {
            ConsoleLogService.getInstance().warn("Failed to load JDK classes, falling back to empty pool: " + e.getMessage());
            return new ClassPool(true);
        }
    }

    /** A receiver for load progress. */
    @FunctionalInterface
    public interface ProgressCallback
    {
        /**
         * Reports progress after one item.
         *
         * @param current how many items are done
         * @param total how many items there are
         * @param message what was just processed
         */
        void onProgress(int current, int total, String message);
    }
}
