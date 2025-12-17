package com.tonic.ui.navigator;

import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.FieldEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.Icons;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collections;
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

        // Collapse single-child package chains
        collapseEmptyPackages(root);

        setRoot(root);
        reload();
    }

    private void collapseEmptyPackages(NavigatorNode node) {
        // Process children first (bottom-up)
        for (int i = 0; i < node.getChildCount(); i++) {
            Object child = node.getChildAt(i);
            if (child instanceof NavigatorNode) {
                collapseEmptyPackages((NavigatorNode) child);
            }
        }

        // Check if this node should collapse its single package child
        if (node instanceof NavigatorNode.PackageNode || node instanceof NavigatorNode.ProjectNode) {
            collapseSinglePackageChild(node);
        }
    }

    private void collapseSinglePackageChild(NavigatorNode parent) {
        while (parent.getChildCount() == 1) {
            Object onlyChild = parent.getChildAt(0);
            if (!(onlyChild instanceof NavigatorNode.PackageNode)) {
                break;
            }

            NavigatorNode.PackageNode childPkg = (NavigatorNode.PackageNode) onlyChild;

            // Build combined display name
            String parentDisplay;
            if (parent instanceof NavigatorNode.PackageNode) {
                parentDisplay = ((NavigatorNode.PackageNode) parent).getDisplayText();
            } else {
                break; // Don't collapse into project node
            }

            String combinedDisplay = parentDisplay + "." + childPkg.getDisplayText();
            ((NavigatorNode.PackageNode) parent).setDisplayName(combinedDisplay);

            // Move grandchildren to parent and remove child
            parent.remove(childPkg);
            while (childPkg.getChildCount() > 0) {
                NavigatorNode grandchild = (NavigatorNode) childPkg.getChildAt(0);
                childPkg.remove(grandchild);
                parent.add(grandchild);
            }
        }
    }

    private List<ClassEntryModel> getFilteredClasses() {
        List<ClassEntryModel> classes = new ArrayList<>(project.getAllClasses());

        // Apply filter
        if (filterText != null && !filterText.isEmpty()) {
            String lowerFilter = filterText.toLowerCase();
            List<ClassEntryModel> filtered = new ArrayList<>();
            for (ClassEntryModel entry : classes) {
                if (entry.getSimpleName().toLowerCase().contains(lowerFilter) ||
                        entry.getClassName().toLowerCase().contains(lowerFilter)) {
                    filtered.add(entry);
                }
            }
            classes = filtered;
        }

        // Sort by name
        Collections.sort(classes, (a, b) -> a.getClassName().compareTo(b.getClassName()));

        return classes;
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
        // Add fields
        List<FieldEntryModel> fields = classEntry.getFields();
        if (!fields.isEmpty()) {
            NavigatorNode.CategoryNode fieldsCategory = new NavigatorNode.CategoryNode("Fields", Icons.getIcon("field"));
            for (FieldEntryModel field : fields) {
                fieldsCategory.add(new NavigatorNode.FieldNode(field));
            }
            classNode.add(fieldsCategory);
        }

        // Add constructors
        List<MethodEntryModel> constructors = classEntry.getConstructors();
        if (!constructors.isEmpty()) {
            NavigatorNode.CategoryNode ctorCategory = new NavigatorNode.CategoryNode("Constructors", Icons.getIcon("constructor"));
            for (MethodEntryModel ctor : constructors) {
                ctorCategory.add(new NavigatorNode.MethodNode(ctor));
            }
            classNode.add(ctorCategory);
        }

        // Add methods (excluding constructors and static initializer)
        List<MethodEntryModel> methods = new ArrayList<>();
        for (MethodEntryModel method : classEntry.getMethods()) {
            if (!method.isConstructor() && !method.isStaticInitializer()) {
                methods.add(method);
            }
        }

        if (!methods.isEmpty()) {
            NavigatorNode.CategoryNode methodsCategory = new NavigatorNode.CategoryNode("Methods", Icons.getIcon("method_public"));
            for (MethodEntryModel method : methods) {
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
