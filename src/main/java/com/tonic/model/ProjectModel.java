package com.tonic.model;

import com.tonic.analysis.xref.XrefDatabase;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.util.Settings;
import lombok.Getter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** The open project: its class entries (user classes marked apart from library ones), resources, class pool and xref database, with a dirty flag and a bytecode version. */
public class ProjectModel
{

    @Getter
    private String projectName;
    @Getter
    private File sourceFile;
    @Getter
    private ClassPool classPool;
    @Getter
    private XrefDatabase xrefDatabase;
    private final Map<String, ClassEntryModel> classEntries = new ConcurrentHashMap<>();
    private final Set<String> userClassNames = ConcurrentHashMap.newKeySet();
    private final Map<String, ResourceEntryModel> resources = new LinkedHashMap<>();
    @Getter
    private boolean dirty;
    /** Monotonic counter bumped on every bytecode mutation; the VM uses it to invalidate its cached class snapshot. */
    @Getter
    private long bytecodeVersion;

    /** Creates an empty project named Untitled with no class pool. */
    public ProjectModel()
    {
        this.projectName = "Untitled";
        this.classPool = null;
    }

    /**
     * Sets the class pool without adding class entries for its classes.
     *
     * @param classPool the class pool
     */
    public void setClassPool(ClassPool classPool)
    {
        this.classPool = classPool;
    }

    /** Marks the project as having unsaved changes and bumps the bytecode version. */
    public void markDirty()
    {
        this.dirty = true;
        bytecodeVersion++;
    }

    /**
     * Adds a user class, putting it in the class pool and marking the project dirty.
     *
     * @param classFile the parsed class
     * @return the new class entry
     */
    public ClassEntryModel addClass(ClassFile classFile)
    {
        String className = classFile.getClassName();
        if (classPool != null)
        {
            classPool.put(classFile);
        }
        ClassEntryModel entry = new ClassEntryModel(classFile);
        classEntries.put(className, entry);
        userClassNames.add(className);
        markDirty();
        return entry;
    }

    /**
     * Removes a class, rebuilds the class pool, clears the xref database and every decompilation cache, and marks the project dirty.
     *
     * @param className the class's internal name, with slashes
     * @return true if the class was removed, false if it was not in the project
     */
    public boolean removeClass(String className)
    {
        ClassEntryModel entry = classEntries.remove(className);
        if (entry == null)
        {
            return false;
        }
        userClassNames.remove(className);

        rebuildClassPool();

        if (xrefDatabase != null)
        {
            xrefDatabase.clear();
        }

        for (ClassEntryModel c : classEntries.values())
        {
            c.invalidateDecompilationCache();
        }

        markDirty();
        return true;
    }

    private void rebuildClassPool()
    {
        ClassPool newPool;
        if (!Settings.getInstance().isLoadJdkClassesEnabled())
        {
            newPool = new ClassPool(true);
        }
        else
        {
            try
            {
                newPool = new ClassPool();
            }
            catch (IOException e)
            {
                newPool = new ClassPool(true);
            }
        }

        for (ClassEntryModel entry : classEntries.values())
        {
            newPool.put(entry.getClassFile());
        }

        this.classPool = newPool;
    }

    /**
     * Looks up a class entry.
     *
     * @param internalName the class's internal name, with slashes
     * @return the entry, or null if the name is null or not in the project
     */
    public ClassEntryModel getClass(String internalName)
    {
        return internalName == null ? null : classEntries.get(internalName);
    }

    /**
     * Lists every class entry, user and library.
     *
     * @return a new list of the entries
     */
    public List<ClassEntryModel> getAllClasses()
    {
        return new ArrayList<>(classEntries.values());
    }

    /** Drops every class's cached decompilation, so each is decompiled from current bytecode the next time it is viewed. */
    public void invalidateAllDecompilationCaches()
    {
        for (ClassEntryModel entry : classEntries.values())
        {
            entry.invalidateDecompilationCache();
        }
    }

    /**
     * Lists the classes the user loaded, excluding library and JDK classes.
     *
     * @return a new list of the user class entries
     */
    public List<ClassEntryModel> getUserClasses()
    {
        return classEntries.values().stream()
                .filter(c -> userClassNames.contains(c.getClassName()))
                .collect(Collectors.toList());
    }

    /**
     * Reports whether a class was loaded by the user rather than from a library or the JDK.
     *
     * @param className the class's internal name, with slashes
     * @return true if it is a user class, false if it is not or the name is null
     */
    public boolean isUserClass(String className)
    {
        return className != null && userClassNames.contains(className);
    }

    /**
     * The names of the classes the user loaded.
     *
     * @return a read-only live view of the user class internal names
     */
    public Set<String> getUserClassNames()
    {
        return Collections.unmodifiableSet(userClassNames);
    }

    /**
     * Lists the classes whose dotted package name starts with a prefix.
     *
     * @param packagePrefix the dotted package prefix
     * @return a new list of the matching entries
     */
    public List<ClassEntryModel> getClassesInPackage(String packagePrefix)
    {
        List<ClassEntryModel> result = new ArrayList<>();
        for (ClassEntryModel entry : classEntries.values())
        {
            if (entry.getPackageName().startsWith(packagePrefix))
            {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Lists the distinct dotted package names, sorted.
     *
     * @return a new sorted list of package names, with the empty string for the default package
     */
    public List<String> getPackages()
    {
        List<String> packages = new ArrayList<>();
        for (ClassEntryModel entry : classEntries.values())
        {
            String pkg = entry.getPackageName();
            if (!packages.contains(pkg))
            {
                packages.add(pkg);
            }
        }
        Collections.sort(packages);
        return packages;
    }

    /**
     * Counts the class entries, user and library.
     *
     * @return the number of class entries
     */
    public int getClassCount()
    {
        return classEntries.size();
    }

    /**
     * Adds a resource, replacing any at the same path, and marks the project dirty.
     *
     * @param resource the resource
     */
    public void addResource(ResourceEntryModel resource)
    {
        resources.put(resource.getPath(), resource);
        markDirty();
    }

    /**
     * Removes a resource and marks the project dirty.
     *
     * @param path the resource's path inside the project
     * @return true if the resource was removed, false if it was not in the project
     */
    public boolean removeResource(String path)
    {
        ResourceEntryModel removed = resources.remove(path);
        if (removed != null)
        {
            markDirty();
            return true;
        }
        return false;
    }

    /**
     * Looks up a resource.
     *
     * @param path the resource's path inside the project
     * @return the resource, or null if none is at that path
     */
    public ResourceEntryModel getResource(String path)
    {
        return resources.get(path);
    }

    /**
     * The project's resources, in insertion order.
     *
     * @return a read-only live view of the resources
     */
    public Collection<ResourceEntryModel> getAllResources()
    {
        return Collections.unmodifiableCollection(resources.values());
    }

    /**
     * Counts the resources.
     *
     * @return the number of resources
     */
    public int getResourceCount()
    {
        return resources.size();
    }

    /** Removes every class and resource, empties the class pool and xref database, and clears the dirty flag. */
    public void clear()
    {
        classEntries.clear();
        userClassNames.clear();
        resources.clear();
        if (classPool != null)
        {
            classPool.getClasses().clear();
        }
        if (xrefDatabase != null)
        {
            xrefDatabase.clear();
        }
        dirty = false;
    }

    /**
     * Sets the name shown for the project.
     *
     * @param projectName the project name
     */
    public void setProjectName(String projectName)
    {
        this.projectName = projectName;
    }

    /**
     * Sets the file the project was loaded from.
     *
     * @param sourceFile the jar, directory or class file
     */
    public void setSourceFile(File sourceFile)
    {
        this.sourceFile = sourceFile;
    }

    /**
     * Sets the cross-reference database.
     *
     * @param xrefDatabase the xref database
     */
    public void setXrefDatabase(XrefDatabase xrefDatabase)
    {
        this.xrefDatabase = xrefDatabase;
    }

    /**
     * Finds a class by internal name, dotted name, or name suffix, in that order.
     *
     * @param name the internal, dotted or simple class name
     * @return the first matching entry, or null if the name is null or nothing matches
     */
    public ClassEntryModel findClassByName(String name)
    {
        if (name == null) return null;

        ClassEntryModel entry = classEntries.get(name);
        if (entry != null) return entry;

        String internalName = name.replace('.', '/');
        entry = classEntries.get(internalName);
        if (entry != null) return entry;

        for (Map.Entry<String, ClassEntryModel> e : classEntries.entrySet())
        {
            if (e.getKey().endsWith("/" + name) || e.getKey().endsWith(name))
            {
                return e.getValue();
            }
        }

        return null;
    }

    /**
     * Sets or clears the unsaved-changes flag without bumping the bytecode version.
     *
     * @param dirty whether the project has unsaved changes
     */
    public void setDirty(boolean dirty)
    {
        this.dirty = dirty;
    }

    /**
     * Rekeys a renamed class, refreshes its display data, clears the xref database and marks the project dirty.
     *
     * @param oldName the old internal name
     * @param newName the new internal name
     */
    public void notifyClassRenamed(String oldName, String newName)
    {
        ClassEntryModel entry = classEntries.remove(oldName);
        if (entry != null)
        {
            entry.refreshDisplayData();
            classEntries.put(newName, entry);
        }

        if (userClassNames.remove(oldName))
        {
            userClassNames.add(newName);
        }

        if (xrefDatabase != null)
        {
            xrefDatabase.clear();
        }

        markDirty();
    }

    /**
     * Rekeys many renamed classes at once, then clears the xref database and marks the project dirty.
     *
     * @param oldToNewNames the old internal name to new internal name map
     */
    public void applyClassNameMappings(Map<String, String> oldToNewNames)
    {
        for (Map.Entry<String, String> entry : oldToNewNames.entrySet())
        {
            String oldName = entry.getKey();
            String newName = entry.getValue();

            if (userClassNames.remove(oldName))
            {
                userClassNames.add(newName);
            }

            ClassEntryModel classEntry = classEntries.remove(oldName);
            if (classEntry != null)
            {
                classEntry.refreshDisplayData();
                classEntries.put(newName, classEntry);
            }
        }

        if (xrefDatabase != null)
        {
            xrefDatabase.clear();
        }

        markDirty();
    }

    /**
     * Replaces every user class and every resource with the given bytes, leaving library classes alone, rebuilding the class pool once and dropping all derived caches.
     *
     * @param classBytes the class bytes, keyed by internal name
     * @param resourceBytes the resource bytes, keyed by path
     * @throws IllegalStateException if any class or resource has no bytes or a class fails to parse, in which case nothing is replaced
     */
    public void replaceUserClasses(Map<String, byte[]> classBytes, Map<String, byte[]> resourceBytes)
    {
        Map<String, ClassEntryModel> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : classBytes.entrySet())
        {
            if (entry.getValue() == null)
            {
                throw new IllegalStateException("Failed to restore class " + entry.getKey() + ": no bytes");
            }
            try
            {
                ClassFile cf = new ClassFile(new ByteArrayInputStream(entry.getValue()));
                parsed.put(cf.getClassName(), new ClassEntryModel(cf));
            }
            catch (Exception e)
            {
                throw new IllegalStateException("Failed to restore class " + entry.getKey() + ": " + e.getMessage(), e);
            }
        }
        for (Map.Entry<String, byte[]> entry : resourceBytes.entrySet())
        {
            if (entry.getValue() == null)
            {
                throw new IllegalStateException("Failed to restore resource " + entry.getKey() + ": no bytes");
            }
        }

        for (String name : new ArrayList<>(userClassNames))
        {
            classEntries.remove(name);
        }
        userClassNames.clear();
        classEntries.putAll(parsed);
        userClassNames.addAll(parsed.keySet());

        rebuildClassPool();

        resources.clear();
        for (Map.Entry<String, byte[]> entry : resourceBytes.entrySet())
        {
            resources.put(entry.getKey(), new ResourceEntryModel(entry.getKey(), entry.getValue()));
        }

        if (xrefDatabase != null)
        {
            xrefDatabase.clear();
        }
        invalidateAllDecompilationCaches();
        markDirty();
    }

    /**
     * Replaces one class's bytecode; does nothing if the class is not in the project.
     *
     * @param internalName the class's internal name, with slashes
     * @param bytes the class file bytes
     * @throws IllegalStateException if the bytes fail to parse
     */
    public void replaceClass(String internalName, byte[] bytes)
    {
        ClassEntryModel entry = classEntries.get(internalName);
        if (entry == null)
        {
            return;
        }
        ClassFile cf;
        try
        {
            cf = new ClassFile(new java.io.ByteArrayInputStream(bytes));
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Failed to restore class " + internalName + ": " + e.getMessage(), e);
        }
        entry.updateClassFile(cf);
        if (classPool != null)
        {
            classPool.put(cf);
        }
        if (xrefDatabase != null)
        {
            xrefDatabase.clear();
        }
        markDirty();
    }

    @Override
    public String toString()
    {
        int resCount = getResourceCount();
        if (resCount > 0)
        {
            return projectName + " (" + getClassCount() + " classes, " + resCount + " resources)";
        }
        return projectName + " (" + getClassCount() + " classes)";
    }
}
