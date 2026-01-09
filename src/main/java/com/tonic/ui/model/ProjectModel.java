package com.tonic.ui.model;

import com.tonic.analysis.xref.XrefDatabase;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import lombok.Getter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final Map<String, ClassEntryModel> classEntries = new HashMap<>();
    private final Set<String> userClassNames = new HashSet<>();
    private final Map<String, ResourceEntryModel> resources = new LinkedHashMap<>();
    @Getter
    private boolean dirty;

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
        dirty = true;
        return entry;
    }

    /**
     * Get a class entry by internal name.
     */
    public ClassEntryModel getClass(String internalName) {
        return classEntries.get(internalName);
    }

    /**
     * Get all class entries.
     */
    public List<ClassEntryModel> getAllClasses() {
        return new ArrayList<>(classEntries.values());
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
        return userClassNames.contains(className);
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

        dirty = true;
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

        dirty = true;
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
