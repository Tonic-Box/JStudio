package com.tonic.model;

import com.tonic.analysis.xref.XrefDatabase;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.util.Settings;
import lombok.Getter;

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

/**
 * Represents an open project containing classes to analyze.
 * Can be loaded from a JAR file, directory, or individual class files.
 */
public class ProjectModel {

    @Getter
    private String projectName;
    @Getter
    private File sourceFile;
    @Getter
    private ClassPool classPool;
    @Getter
    private XrefDatabase xrefDatabase;
    // Concurrent: live capture/attach pulls classes in on a background thread while the EDT iterates them
    // (navigator rebuild, etc.). Weakly-consistent iteration avoids ConcurrentModificationException.
    private final Map<String, ClassEntryModel> classEntries = new ConcurrentHashMap<>();
    private final Set<String> userClassNames = ConcurrentHashMap.newKeySet();
    private final Map<String, ResourceEntryModel> resources = new LinkedHashMap<>();
    @Getter
    private boolean dirty;
    /** Monotonic counter bumped on every bytecode mutation; the VM uses it to invalidate its cached class snapshot. */
    @Getter
    private long bytecodeVersion;

    public ProjectModel() {
        this.projectName = "Untitled";
        // Start with no class pool - it will be set when a file is loaded
        this.classPool = null;
    }

    /**
     * Set the class pool (does not auto-populate class entries).
     */
    public void setClassPool(ClassPool classPool) {
        this.classPool = classPool;
    }

    /** Marks the project as having unsaved changes (so close prompts to save). */
    public void markDirty() {
        this.dirty = true;
        bytecodeVersion++;
    }

    /**
     * Add a user class to the project.
     */
    public ClassEntryModel addClass(ClassFile classFile) {
        String className = classFile.getClassName();
        if (classPool != null) {
            classPool.put(classFile);
        }
        ClassEntryModel entry = new ClassEntryModel(classFile);
        classEntries.put(className, entry);
        userClassNames.add(className);
        markDirty();
        return entry;
    }

    /**
     * Remove a class from the project and rebuild all analysis state.
     * This invalidates the ClassPool, xref database, and all decompilation caches.
     * @return true if the class was removed, false if it didn't exist
     */
    public boolean removeClass(String className) {
        ClassEntryModel entry = classEntries.remove(className);
        if (entry == null) {
            return false;
        }
        userClassNames.remove(className);

        rebuildClassPool();

        if (xrefDatabase != null) {
            xrefDatabase.clear();
        }

        for (ClassEntryModel c : classEntries.values()) {
            c.invalidateDecompilationCache();
        }

        markDirty();
        return true;
    }

    /**
     * Rebuild the ClassPool from the current user classes.
     * This creates a fresh ClassPool and repopulates it with all remaining classes.
     */
    private void rebuildClassPool() {
        ClassPool newPool;
        if (!Settings.getInstance().isLoadJdkClassesEnabled()) {
            newPool = new ClassPool(true);
        } else {
            try {
                newPool = new ClassPool();
            } catch (IOException e) {
                newPool = new ClassPool(true);
            }
        }

        for (ClassEntryModel entry : classEntries.values()) {
            newPool.put(entry.getClassFile());
        }

        this.classPool = newPool;
    }

    /**
     * Get a class entry by internal name.
     */
    public ClassEntryModel getClass(String internalName) {
        return internalName == null ? null : classEntries.get(internalName);
    }

    /**
     * Get all class entries.
     */
    public List<ClassEntryModel> getAllClasses() {
        return new ArrayList<>(classEntries.values());
    }

    /**
     * Drops every class's cached decompilation. A project mutation (rename, script transform) can change references
     * in any class, so a cache scoped to the mutated class alone leaves other classes showing stale source; clearing
     * all of them forces a fresh decompile from current bytecode the next time each is viewed.
     */
    public void invalidateAllDecompilationCaches() {
        for (ClassEntryModel entry : classEntries.values()) {
            entry.invalidateDecompilationCache();
        }
    }

    /**
     * Get user classes only (classes explicitly loaded by user).
     */
    public List<ClassEntryModel> getUserClasses() {
        return classEntries.values().stream()
                .filter(c -> userClassNames.contains(c.getClassName()))
                .collect(Collectors.toList());
    }

    /**
     * Check if a class name is a user class (not JDK/library).
     */
    public boolean isUserClass(String className) {
        return className != null && userClassNames.contains(className);
    }

    /**
     * Get the set of user class names (classes explicitly loaded by user).
     */
    public Set<String> getUserClassNames() {
        return Collections.unmodifiableSet(userClassNames);
    }

    /**
     * Get classes in a specific package.
     */
    public List<ClassEntryModel> getClassesInPackage(String packagePrefix) {
        List<ClassEntryModel> result = new ArrayList<>();
        for (ClassEntryModel entry : classEntries.values()) {
            if (entry.getPackageName().startsWith(packagePrefix)) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Get all unique package names.
     */
    public List<String> getPackages() {
        List<String> packages = new ArrayList<>();
        for (ClassEntryModel entry : classEntries.values()) {
            String pkg = entry.getPackageName();
            if (!packages.contains(pkg)) {
                packages.add(pkg);
            }
        }
        Collections.sort(packages);
        return packages;
    }

    /**
     * Get the number of classes in the project.
     */
    public int getClassCount() {
        return classEntries.size();
    }

    public void addResource(ResourceEntryModel resource) {
        resources.put(resource.getPath(), resource);
        markDirty();
    }

    /**
     * Remove a resource from the project.
     * @return true if the resource was removed, false if it didn't exist
     */
    public boolean removeResource(String path) {
        ResourceEntryModel removed = resources.remove(path);
        if (removed != null) {
            markDirty();
            return true;
        }
        return false;
    }

    public ResourceEntryModel getResource(String path) {
        return resources.get(path);
    }

    public Collection<ResourceEntryModel> getAllResources() {
        return Collections.unmodifiableCollection(resources.values());
    }

    public int getResourceCount() {
        return resources.size();
    }

    /**
     * Clear all classes from the project.
     */
    public void clear() {
        classEntries.clear();
        userClassNames.clear();
        resources.clear();
        if (classPool != null) {
            classPool.getClasses().clear();
        }
        if (xrefDatabase != null) {
            xrefDatabase.clear();
        }
        dirty = false;
    }

    // Getters and setters

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public void setSourceFile(File sourceFile) {
        this.sourceFile = sourceFile;
    }

    public void setXrefDatabase(XrefDatabase xrefDatabase) {
        this.xrefDatabase = xrefDatabase;
    }

    /**
     * Find a class entry by name (supports both internal and qualified names).
     */
    public ClassEntryModel findClassByName(String name) {
        if (name == null) return null;

        // Try direct lookup
        ClassEntryModel entry = classEntries.get(name);
        if (entry != null) return entry;

        // Try with dots replaced by slashes
        String internalName = name.replace('.', '/');
        entry = classEntries.get(internalName);
        if (entry != null) return entry;

        // Try partial match
        for (Map.Entry<String, ClassEntryModel> e : classEntries.entrySet()) {
            if (e.getKey().endsWith("/" + name) || e.getKey().endsWith(name)) {
                return e.getValue();
            }
        }

        return null;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public void notifyClassRenamed(String oldName, String newName) {
        ClassEntryModel entry = classEntries.remove(oldName);
        if (entry != null) {
            entry.refreshDisplayData();
            classEntries.put(newName, entry);
        }

        if (userClassNames.remove(oldName)) {
            userClassNames.add(newName);
        }

        if (xrefDatabase != null) {
            xrefDatabase.clear();
        }

        markDirty();
    }

    public void applyClassNameMappings(Map<String, String> oldToNewNames) {
        for (Map.Entry<String, String> entry : oldToNewNames.entrySet()) {
            String oldName = entry.getKey();
            String newName = entry.getValue();

            if (userClassNames.remove(oldName)) {
                userClassNames.add(newName);
            }

            ClassEntryModel classEntry = classEntries.remove(oldName);
            if (classEntry != null) {
                classEntry.refreshDisplayData();
                classEntries.put(newName, classEntry);
            }
        }

        if (xrefDatabase != null) {
            xrefDatabase.clear();
        }

        markDirty();
    }

    /**
     * Replaces every user class (and all resources) with the given name->bytes sets, parsing each into a fresh
     * {@link ClassFile}, then rebuilds the class pool ONCE. Used by Local History restore; non-user (library/JDK)
     * entries are left untouched. Callers must trigger a UI refresh afterward (decompilation caches are cleared here).
     */
    public void replaceUserClasses(Map<String, byte[]> classBytes, Map<String, byte[]> resourceBytes) {
        for (String name : new ArrayList<>(userClassNames)) {
            classEntries.remove(name);
        }
        userClassNames.clear();

        for (Map.Entry<String, byte[]> entry : classBytes.entrySet()) {
            try {
                ClassFile cf = new ClassFile(new java.io.ByteArrayInputStream(entry.getValue()));
                classEntries.put(cf.getClassName(), new ClassEntryModel(cf));
                userClassNames.add(cf.getClassName());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to restore class " + entry.getKey() + ": " + e.getMessage(), e);
            }
        }

        rebuildClassPool();

        resources.clear();
        for (Map.Entry<String, byte[]> entry : resourceBytes.entrySet()) {
            resources.put(entry.getKey(), new ResourceEntryModel(entry.getKey(), entry.getValue()));
        }

        if (xrefDatabase != null) {
            xrefDatabase.clear();
        }
        invalidateAllDecompilationCaches();
        markDirty();
    }

    /**
     * Replaces a single user class's bytecode from stored bytes (Local History per-class restore). No-op if the class
     * is not present. Callers must trigger a UI refresh afterward.
     */
    public void replaceClass(String internalName, byte[] bytes) {
        ClassEntryModel entry = classEntries.get(internalName);
        if (entry == null) {
            return;
        }
        ClassFile cf;
        try {
            cf = new ClassFile(new java.io.ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to restore class " + internalName + ": " + e.getMessage(), e);
        }
        entry.updateClassFile(cf);
        if (classPool != null) {
            classPool.put(cf);
        }
        if (xrefDatabase != null) {
            xrefDatabase.clear();
        }
        markDirty();
    }

    @Override
    public String toString() {
        int resCount = getResourceCount();
        if (resCount > 0) {
            return projectName + " (" + getClassCount() + " classes, " + resCount + " resources)";
        }
        return projectName + " (" + getClassCount() + " classes)";
    }
}
