package com.tonic.ui.navigator;

import javax.swing.JTree;
import javax.swing.tree.TreePath;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

final class NavigatorTreeStateManager
{

    private final JTree tree;
    private final ClassTreeModel treeModel;

    private Set<String> capturedExpandedKeys;
    private String capturedSelectedKey;

    NavigatorTreeStateManager(JTree tree, ClassTreeModel treeModel)
    {
        this.tree = tree;
        this.treeModel = treeModel;
    }

    void capture()
    {
        capturedExpandedKeys = captureExpandedKeys();
        TreePath selectionPath = tree.getSelectionPath();
        capturedSelectedKey = selectionPath != null ? pathKey(selectionPath) : null;
    }

    void restore()
    {
        if (capturedExpandedKeys == null)
        {
            return;
        }
        restoreTreeState(capturedExpandedKeys, capturedSelectedKey);
        capturedExpandedKeys = null;
        capturedSelectedKey = null;
    }

    private Set<String> captureExpandedKeys()
    {
        Set<String> keys = new HashSet<>();
        Object root = treeModel.getRoot();
        if (root == null)
        {
            return keys;
        }
        Enumeration<TreePath> expanded = tree.getExpandedDescendants(new TreePath(root));
        if (expanded != null)
        {
            while (expanded.hasMoreElements())
            {
                keys.add(pathKey(expanded.nextElement()));
            }
        }
        return keys;
    }

    private void restoreTreeState(Set<String> expandedKeys, String selectedKey)
    {
        Object root = treeModel.getRoot();
        if (root instanceof NavigatorNode)
        {
            restoreNode((NavigatorNode) root, new TreePath(root), expandedKeys, selectedKey, true);
        }
    }

    private void restoreNode(NavigatorNode node, TreePath path, Set<String> expandedKeys, String selectedKey, boolean isRoot)
    {
        String key = pathKey(path);
        if (selectedKey != null && selectedKey.equals(key))
        {
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
        }
        if (!isRoot && !expandedKeys.contains(key))
        {
            return;
        }
        tree.expandPath(path);
        for (int i = 0; i < node.getChildCount(); i++)
        {
            Object child = node.getChildAt(i);
            if (child instanceof NavigatorNode)
            {
                restoreNode((NavigatorNode) child, path.pathByAddingChild(child), expandedKeys, selectedKey, false);
            }
        }
    }

    static String pathKey(TreePath path)
    {
        StringBuilder sb = new StringBuilder();
        for (Object node : path.getPath())
        {
            sb.append('\n').append(node);
        }
        return sb.toString();
    }

    void collapseAll()
    {
        int row = tree.getRowCount() - 1;
        while (row >= 0)
        {
            tree.collapseRow(row);
            row--;
        }
    }

    void expandAll()
    {
        int row = 0;
        while (row < tree.getRowCount())
        {
            tree.expandRow(row);
            row++;
        }
    }

    void expandToLevel(int level)
    {
        expandToLevel((NavigatorNode) treeModel.getRoot(), 0, level);
    }

    private void expandToLevel(NavigatorNode node, int currentLevel, int targetLevel)
    {
        if (currentLevel >= targetLevel) return;

        TreePath path = new TreePath(treeModel.getPathToRoot(node));
        tree.expandPath(path);

        for (int i = 0; i < node.getChildCount(); i++)
        {
            Object child = node.getChildAt(i);
            if (child instanceof NavigatorNode)
            {
                expandToLevel((NavigatorNode) child, currentLevel + 1, targetLevel);
            }
        }
    }
}
