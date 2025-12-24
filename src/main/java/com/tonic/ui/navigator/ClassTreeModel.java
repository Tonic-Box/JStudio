package com.tonic.ui.navigator;

import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.FieldEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.util.JdkClassFilter;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tree model for the class navigator, backed by a ProjectModel.
 */
public class ClassTreeModel extends DefaultTreeModel {

    private ProjectModel project;
    private String filterText;
    private boolean showMembers = true;

    public ClassTreeModel() {
        super(new NavigatorNode.ProjectNode("No Project", 0));
    }

    /**
     * Load a project into the tree model.
     */
    public void loadProject(ProjectModel project) {
        this.project = project;
        this.filterText = null;
        rebuildTree();
    }

    /**
     * Filter classes by name.
     */
    public void setFilter(String filterText) {
        this.filterText = filterText;
        rebuildTree();
    }

    /**
     * Clear the filter.
     */
    public void clearFilter() {
        this.filterText = null;
        rebuildTree();
    }

    /**
     * Set whether to show class members (methods, fields).
     */
    public void setShowMembers(boolean showMembers) {
        this.showMembers = showMembers;
        rebuildTree();
    }

    /**
     * Rebuild the tree structure from the project.
     */
    private void rebuildTree() {
        if (project == null) {
            setRoot(new NavigatorNode.ProjectNode("No Project", 0));
            reload();
            return;
        }

        // Get filtered classes
        List<ClassEntryModel> classes = getFilteredClasses();

        // Create root node
        NavigatorNode.ProjectNode root = new NavigatorNode.ProjectNode(
                project.getProjectName(), classes.size());

        // Build package hierarchy
        Map<String, NavigatorNode.PackageNode> packageNodes = new HashMap<>();

        for (ClassEntryModel classEntry : classes) {
            String packageName = classEntry.getPackageName();

            // Get or create package nodes
            NavigatorNode.PackageNode packageNode = getOrCreatePackageNode(
                    root, packageNodes, packageName);

            // Create class node
            NavigatorNode.ClassNode classNode = new NavigatorNode.ClassNode(classEntry);

            // Add members if enabled
            if (showMembers) {
                addMembersToClass(classNode, classEntry);
            }

            packageNode.add(classNode);
        }

        collapseEmptyPackages(root);

        setRoot(root);
        reload();
    }

    private void collapseEmptyPackages(NavigatorNode.ProjectNode root) {
        Deque<NavigatorNode> stack = new ArrayDeque<>();
        for (int i = 0; i < root.getChildCount(); i++) {
            TreeNode child = root.getChildAt(i);
            if (child instanceof NavigatorNode.PackageNode) {
                stack.push((NavigatorNode.PackageNode) child);
            }
        }

        while (!stack.isEmpty()) {
            NavigatorNode node = stack.pop();
            if (!(node instanceof NavigatorNode.PackageNode)) {
                continue;
            }

            NavigatorNode.PackageNode pkgNode = (NavigatorNode.PackageNode) node;

            while (hasSinglePackageChild(pkgNode)) {
                NavigatorNode.PackageNode childPkg = getSinglePackageChild(pkgNode);
                String combinedName = pkgNode.getPackageName() + "." + getLastSegment(childPkg.getPackageName());
                pkgNode.setDisplayName(combinedName);

                List<TreeNode> grandChildren = new ArrayList<>();
                for (int i = 0; i < childPkg.getChildCount(); i++) {
                    grandChildren.add(childPkg.getChildAt(i));
                }

                pkgNode.remove(childPkg);

                for (TreeNode grandChild : grandChildren) {
                    if (grandChild instanceof NavigatorNode) {
                        pkgNode.add((NavigatorNode) grandChild);
                    }
                }
            }

            for (int i = 0; i < pkgNode.getChildCount(); i++) {
                TreeNode child = pkgNode.getChildAt(i);
                if (child instanceof NavigatorNode.PackageNode) {
                    stack.push((NavigatorNode.PackageNode) child);
                }
            }
        }
    }

    private boolean hasSinglePackageChild(NavigatorNode.PackageNode node) {
        if (node.getChildCount() != 1) {
            return false;
        }
        return node.getChildAt(0) instanceof NavigatorNode.PackageNode;
    }

    private NavigatorNode.PackageNode getSinglePackageChild(NavigatorNode.PackageNode node) {
        if (node.getChildCount() == 1 && node.getChildAt(0) instanceof NavigatorNode.PackageNode) {
            return (NavigatorNode.PackageNode) node.getChildAt(0);
        }
        return null;
    }

    private String getLastSegment(String packageName) {
        int lastDot = packageName.lastIndexOf('.');
        return lastDot >= 0 ? packageName.substring(lastDot + 1) : packageName;
    }

    private List<ClassEntryModel> getFilteredClasses() {
        List<ClassEntryModel> classes = new ArrayList<>();
        for (ClassEntryModel entry : project.getAllClasses()) {
            if (!JdkClassFilter.isJdkClass(entry.getClassName())) {
                classes.add(entry);
            }
        }

        if (filterText != null && !filterText.isEmpty()) {
            String lowerFilter = filterText.toLowerCase();
            List<ClassEntryModel> filtered = new ArrayList<>();
            for (ClassEntryModel entry : classes) {
                if (matchesFilter(entry, lowerFilter)) {
                    filtered.add(entry);
                }
            }
            classes = filtered;
        }

        Collections.sort(classes, (a, b) -> a.getClassName().compareTo(b.getClassName()));

        return classes;
    }

    private boolean matchesFilter(ClassEntryModel entry, String lowerFilter) {
        if (entry.getSimpleName().toLowerCase().contains(lowerFilter) ||
                entry.getClassName().toLowerCase().contains(lowerFilter)) {
            return true;
        }

        for (MethodEntryModel method : entry.getMethods()) {
            if (method.getName().toLowerCase().contains(lowerFilter)) {
                return true;
            }
        }

        for (MethodEntryModel ctor : entry.getConstructors()) {
            if (ctor.getName().toLowerCase().contains(lowerFilter)) {
                return true;
            }
        }

        return false;
    }

    private NavigatorNode.PackageNode getOrCreatePackageNode(
            NavigatorNode.ProjectNode root,
            Map<String, NavigatorNode.PackageNode> packageNodes,
            String packageName) {

        if (packageName.isEmpty()) {
            packageName = "(default package)";
        }

        NavigatorNode.PackageNode node = packageNodes.get(packageName);
        if (node != null) {
            return node;
        }

        // Create package node hierarchy
        String[] parts = packageName.split("\\.");
        StringBuilder fullName = new StringBuilder();
        NavigatorNode parent = root;

        for (int i = 0; i < parts.length; i++) {
            if (i > 0) fullName.append(".");
            fullName.append(parts[i]);

            String currentFullName = fullName.toString();
            NavigatorNode.PackageNode current = packageNodes.get(currentFullName);

            if (current == null) {
                current = new NavigatorNode.PackageNode(currentFullName);
                packageNodes.put(currentFullName, current);

                // Insert in sorted order
                insertSorted(parent, current);
            }

            parent = current;
        }

        return packageNodes.get(packageName);
    }

    private void insertSorted(NavigatorNode parent, NavigatorNode child) {
        int count = parent.getChildCount();
        for (int i = 0; i < count; i++) {
            TreeNode existing = parent.getChildAt(i);
            if (existing instanceof NavigatorNode) {
                String existingText = ((NavigatorNode) existing).getDisplayText();
                String newText = child.getDisplayText();

                // Packages before classes
                boolean existingIsPackage = existing instanceof NavigatorNode.PackageNode;
                boolean newIsPackage = child instanceof NavigatorNode.PackageNode;

                if (existingIsPackage == newIsPackage) {
                    if (newText.compareToIgnoreCase(existingText) < 0) {
                        parent.insert(child, i);
                        return;
                    }
                } else if (newIsPackage && !existingIsPackage) {
                    parent.insert(child, i);
                    return;
                }
            }
        }
        parent.add(child);
    }

    private void addMembersToClass(NavigatorNode.ClassNode classNode, ClassEntryModel classEntry) {
        String lowerFilter = (filterText != null && !filterText.isEmpty()) ? filterText.toLowerCase() : null;
        boolean classNameMatches = lowerFilter == null ||
                classEntry.getSimpleName().toLowerCase().contains(lowerFilter) ||
                classEntry.getClassName().toLowerCase().contains(lowerFilter);

        if (lowerFilter == null || classNameMatches) {
            List<FieldEntryModel> fields = classEntry.getFields();
            if (!fields.isEmpty()) {
                NavigatorNode.CategoryNode fieldsCategory = new NavigatorNode.CategoryNode("Fields", Icons.getIcon("field"));
                for (FieldEntryModel field : fields) {
                    fieldsCategory.add(new NavigatorNode.FieldNode(field));
                }
                classNode.add(fieldsCategory);
            }
        }

        List<MethodEntryModel> constructors = classEntry.getConstructors();
        List<MethodEntryModel> filteredCtors = new ArrayList<>();
        for (MethodEntryModel ctor : constructors) {
            if (lowerFilter == null || classNameMatches ||
                    ctor.getName().toLowerCase().contains(lowerFilter)) {
                filteredCtors.add(ctor);
            }
        }
        if (!filteredCtors.isEmpty()) {
            NavigatorNode.CategoryNode ctorCategory = new NavigatorNode.CategoryNode("Constructors", Icons.getIcon("constructor"));
            for (MethodEntryModel ctor : filteredCtors) {
                ctorCategory.add(new NavigatorNode.MethodNode(ctor));
            }
            classNode.add(ctorCategory);
        }

        List<MethodEntryModel> filteredMethods = new ArrayList<>();
        for (MethodEntryModel method : classEntry.getMethods()) {
            if (!method.isConstructor() && !method.isStaticInitializer()) {
                if (lowerFilter == null || classNameMatches ||
                        method.getName().toLowerCase().contains(lowerFilter)) {
                    filteredMethods.add(method);
                }
            }
        }

        if (!filteredMethods.isEmpty()) {
            NavigatorNode.CategoryNode methodsCategory = new NavigatorNode.CategoryNode("Methods", Icons.getIcon("method_public"));
            for (MethodEntryModel method : filteredMethods) {
                methodsCategory.add(new NavigatorNode.MethodNode(method));
            }
            classNode.add(methodsCategory);
        }
    }

    /**
     * Find a class node in the tree.
     */
    public NavigatorNode.ClassNode findClassNode(String className) {
        return findClassNode((NavigatorNode) getRoot(), className);
    }

    private NavigatorNode.ClassNode findClassNode(NavigatorNode node, String className) {
        if (node instanceof NavigatorNode.ClassNode) {
            NavigatorNode.ClassNode classNode = (NavigatorNode.ClassNode) node;
            if (classNode.getClassEntry().getClassName().equals(className)) {
                return classNode;
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            TreeNode child = node.getChildAt(i);
            if (child instanceof NavigatorNode) {
                NavigatorNode.ClassNode found = findClassNode((NavigatorNode) child, className);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * Clear the tree.
     */
    public void clear() {
        this.project = null;
        this.filterText = null;
        setRoot(new NavigatorNode.ProjectNode("No Project", 0));
        reload();
    }
}
