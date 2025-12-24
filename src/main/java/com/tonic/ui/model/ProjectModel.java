package com.tonic.ui.model;

import com.tonic.analysis.xref.XrefDatabase;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents an open project containing classes to analyze.
 * Can be loaded from a JAR file, directory, or individual class files.
 */
public class ProjectModel {

    private String projectName;
    private File sourceFile;
    private ClassPool classPool;
    private XrefDatabase xrefDatabase;
    private final Map<String, ClassEntryModel> classEntries = new HashMap<>();
    private final Set<String> userClassNames = new HashSet<>();
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

    /**
     * Clear all classes from the project.
     */
    public void clear() {
        classEntries.clear();
        userClassNames.clear();
        if (classPool != null) {
            classPool.getClasses().clear();
        }
        if (xrefDatabase != null) {
            xrefDatabase.clear();
        }
        dirty = false;
    }

    // Getters and setters

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(File sourceFile) {
        this.sourceFile = sourceFile;
    }

    public ClassPool getClassPool() {
        return classPool;
    }

    public XrefDatabase getXrefDatabase() {
        return xrefDatabase;
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

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    @Override
    public String toString() {
        return projectName + " (" + getClassCount() + " classes)";
    }
}
