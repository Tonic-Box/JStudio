package com.tonic.ui.navigator;

import javax.swing.JTree;
import javax.swing.tree.TreePath;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

/**
 * Owns the navigator tree's expansion/selection bookkeeping: capturing which nodes are open (plus the selection)
 * before a rebuild and restoring them afterwards by matching rebuild-stable name-path keys, as well as the
 * collapse/expand-all and expand-to-level operations driven by the toolbar.
 */
final class NavigatorTreeStateManager {

    private final JTree tree;
    private final ClassTreeModel treeModel;

    private Set<String> capturedExpandedKeys;
    private String capturedSelectedKey;

    NavigatorTreeStateManager(JTree tree, ClassTreeModel treeModel) {
        this.tree = tree;
        this.treeModel = treeModel;
    }

    /**
     * Records the currently-expanded nodes and the selection so they can be re-applied after the tree is rebuilt
     * (which discards them). State is matched by each node's name-path, so it survives the new node objects;
     * best-effort: a renamed node's key changes, so only that node loses its state.
     */
    void capture() {
        capturedExpandedKeys = captureExpandedKeys();
        TreePath selectionPath = tree.getSelectionPath();
        capturedSelectedKey = selectionPath != null ? pathKey(selectionPath) : null;
    }

    /** Re-expands the nodes that were open and re-selects the previously selected node from the last {@link #capture()}. */
    void restore() {
        if (capturedExpandedKeys == null) {
            return;
        }
        restoreTreeState(capturedExpandedKeys, capturedSelectedKey);
        capturedExpandedKeys = null;
        capturedSelectedKey = null;
    }

    /** Name-path keys of every currently-expanded node, so expansion can be restored after a tree rebuild. */
    private Set<String> captureExpandedKeys() {
        Set<String> keys = new HashSet<>();
        Object root = treeModel.getRoot();
        if (root == null) {
            return keys;
        }
        Enumeration<TreePath> expanded = tree.getExpandedDescendants(new TreePath(root));
        if (expanded != null) {
            while (expanded.hasMoreElements()) {
                keys.add(pathKey(expanded.nextElement()));
            }
        }
        return keys;
    }

    /** Re-expands the nodes that were open and re-selects the previously selected node, by matching name-paths. */
    private void restoreTreeState(Set<String> expandedKeys, String selectedKey) {
        Object root = treeModel.getRoot();
        if (root instanceof NavigatorNode) {
            restoreNode((NavigatorNode) root, new TreePath(root), expandedKeys, selectedKey, true);
        }
    }

    private void restoreNode(NavigatorNode node, TreePath path, Set<String> expandedKeys,
                             String selectedKey, boolean isRoot) {
        String key = pathKey(path);
        if (selectedKey != null && selectedKey.equals(key)) {
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
        }
        // A collapsed node had no expanded descendants, so prune the walk there (keeps it bounded to the open subtree).
        if (!isRoot && !expandedKeys.contains(key)) {
            return;
        }
        tree.expandPath(path);
        for (int i = 0; i < node.getChildCount(); i++) {
            Object child = node.getChildAt(i);
            if (child instanceof NavigatorNode) {
                restoreNode((NavigatorNode) child, path.pathByAddingChild(child), expandedKeys, selectedKey, false);
            }
        }
    }

    /** A rebuild-stable key for a tree path: its nodes' display texts joined (newline-separated, absent from names). */
    static String pathKey(TreePath path) {
        StringBuilder sb = new StringBuilder();
        for (Object node : path.getPath()) {
            sb.append('\n').append(node);
        }
        return sb.toString();
    }

    void collapseAll() {
        int row = tree.getRowCount() - 1;
        while (row >= 0) {
            tree.collapseRow(row);
            row--;
        }
    }

    void expandAll() {
        int row = 0;
        while (row < tree.getRowCount()) {
            tree.expandRow(row);
            row++;
        }
    }

    void expandToLevel(int level) {
        expandToLevel((NavigatorNode) treeModel.getRoot(), 0, level);
    }

    private void expandToLevel(NavigatorNode node, int currentLevel, int targetLevel) {
        if (currentLevel >= targetLevel) return;

        TreePath path = new TreePath(treeModel.getPathToRoot(node));
        tree.expandPath(path);

        for (int i = 0; i < node.getChildCount(); i++) {
            Object child = node.getChildAt(i);
            if (child instanceof NavigatorNode) {
                expandToLevel((NavigatorNode) child, currentLevel + 1, targetLevel);
            }
        }
    }
}
