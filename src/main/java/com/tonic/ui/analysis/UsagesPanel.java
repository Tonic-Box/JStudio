package com.tonic.ui.analysis;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.*;
import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.ClassSelectedEvent;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class UsagesPanel extends ThemedJPanel {

    private final ProjectModel project;
    private final JTextField searchField;
    private final JTree resultsTree;
    private final DefaultMutableTreeNode rootNode;
    private final DefaultTreeModel treeModel;
    private final JLabel statusLabel;
    private final JButton searchButton;
    private final JComboBox<String> searchTypeCombo;

    public UsagesPanel(ProjectModel project) {
        super(BackgroundStyle.SECONDARY, new BorderLayout());
        this.project = project;

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, UIConstants.SPACING_MEDIUM, UIConstants.SPACING_SMALL));
        toolbar.setBackground(JStudioTheme.getBgSecondary());

        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setForeground(JStudioTheme.getTextPrimary());
        toolbar.add(searchLabel);

        searchField = new JTextField(20);
        searchField.setBackground(JStudioTheme.getBgTertiary());
        searchField.setForeground(JStudioTheme.getTextPrimary());
        searchField.setCaretColor(JStudioTheme.getTextPrimary());
        searchField.addActionListener(e -> findUsages());
        toolbar.add(searchField);

        searchTypeCombo = new JComboBox<>(new String[]{"Class", "Method", "Field", "String"});
        searchTypeCombo.setBackground(JStudioTheme.getBgTertiary());
        searchTypeCombo.setForeground(JStudioTheme.getTextPrimary());
        toolbar.add(searchTypeCombo);

        searchButton = new JButton("Find Usages");
        searchButton.setBackground(JStudioTheme.getBgTertiary());
        searchButton.setForeground(JStudioTheme.getTextPrimary());
        searchButton.addActionListener(e -> findUsages());
        toolbar.add(searchButton);

        add(toolbar, BorderLayout.NORTH);

        // Results tree
        rootNode = new DefaultMutableTreeNode("Usages");
        treeModel = new DefaultTreeModel(rootNode);
        resultsTree = new JTree(treeModel);
        resultsTree.setBackground(JStudioTheme.getBgTertiary());
        resultsTree.setForeground(JStudioTheme.getTextPrimary());
        resultsTree.setFont(JStudioTheme.getCodeFont(UIConstants.FONT_SIZE_CODE));
        resultsTree.setRootVisible(false);
        resultsTree.setShowsRootHandles(true);

        // Custom cell renderer
        resultsTree.setCellRenderer(new UsageTreeCellRenderer());

        // Double-click to navigate
        resultsTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateToSelection();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(resultsTree);
        scrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, JStudioTheme.getBorder()));
        scrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());
        add(scrollPane, BorderLayout.CENTER);

        // Status bar
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBackground(JStudioTheme.getBgSecondary());
        statusLabel = new JLabel("Enter a search term and click 'Find Usages'.");
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        statusPanel.add(statusLabel);
        add(statusPanel, BorderLayout.SOUTH);
    }

    /**
     * Find usages based on current search.
     */
    public void findUsages() {
        String searchTerm = searchField.getText().trim();
        if (searchTerm.isEmpty()) {
            statusLabel.setText("Enter a search term.");
            return;
        }

        if (project.getClassPool() == null) {
            statusLabel.setText("No project loaded.");
            return;
        }

        searchButton.setEnabled(false);
        statusLabel.setText("Searching...");
        rootNode.removeAllChildren();

        String searchType = (String) searchTypeCombo.getSelectedItem();

        SwingWorker<Map<ClassEntryModel, List<UsageInfo>>, Void> worker = new SwingWorker<>() {
            @Override
            protected Map<ClassEntryModel, List<UsageInfo>> doInBackground() {
                Map<ClassEntryModel, List<UsageInfo>> results = new LinkedHashMap<>();

                for (ClassEntryModel classEntry : project.getUserClasses()) {
                    List<UsageInfo> usages = findUsagesInClass(classEntry, searchTerm, searchType);
                    if (!usages.isEmpty()) {
                        results.put(classEntry, usages);
                    }
                }

                return results;
            }

            @Override
            protected void done() {
                try {
                    Map<ClassEntryModel, List<UsageInfo>> results = get();
                    int totalUsages = 0;

                    for (Map.Entry<ClassEntryModel, List<UsageInfo>> entry : results.entrySet()) {
                        ClassEntryModel classEntry = entry.getKey();
                        List<UsageInfo> usages = entry.getValue();

                        DefaultMutableTreeNode classNode = new DefaultMutableTreeNode(
                                new UsageNode(classEntry.getClassName().replace('/', '.'), classEntry, null));

                        for (UsageInfo usage : usages) {
                            classNode.add(new DefaultMutableTreeNode(
                                    new UsageNode(usage.description, classEntry, usage)));
                            totalUsages++;
                        }

                        rootNode.add(classNode);
                    }

                    treeModel.reload();
                    expandAll();

                    statusLabel.setText("Found " + totalUsages + " usages in " + results.size() + " classes. Double-click to navigate.");
                } catch (Exception e) {
                    statusLabel.setText("Error: " + e.getMessage());
                }
                searchButton.setEnabled(true);
            }
        };

        worker.execute();
    }

    private List<UsageInfo> findUsagesInClass(ClassEntryModel classEntry, String searchTerm, String searchType) {
        List<UsageInfo> usages = new ArrayList<>();
        ClassFile cf = classEntry.getClassFile();
        ConstPool constPool = cf.getConstPool();
        List<Item<?>> items = constPool.getItems();

        switch (searchType) {
            case "Class":
                findClassUsages(items, searchTerm, usages);
                break;
            case "Method":
                findMethodUsages(items, searchTerm, usages);
                break;
            case "Field":
                findFieldUsages(items, searchTerm, usages);
                break;
            case "String":
                findStringUsages(items, searchTerm, usages);
                break;
        }

        return usages;
    }

    private void findClassUsages(List<Item<?>> items, String searchTerm, List<UsageInfo> usages) {
        String normalizedSearch = searchTerm.replace('.', '/');

        for (int i = 1; i < items.size(); i++) {
            try {
                Item<?> item = items.get(i);
                if (item instanceof ClassRefItem) {
                    ClassRefItem classRef = (ClassRefItem) item;
                    int nameIndex = classRef.getValue();
                    Item<?> nameItem = items.get(nameIndex);
                    if (nameItem instanceof Utf8Item) {
                        String className = ((Utf8Item) nameItem).getValue();
                        if (className != null && (className.contains(normalizedSearch) ||
                                className.replace('/', '.').contains(searchTerm))) {
                            usages.add(new UsageInfo("Class reference: " + className.replace('/', '.'), i));
                        }
                    }
                }
            } catch (Exception e) {
                // Skip invalid entries
            }
        }
    }

    private void findMethodUsages(List<Item<?>> items, String searchTerm, List<UsageInfo> usages) {
        for (int i = 1; i < items.size(); i++) {
            try {
                Item<?> item = items.get(i);
                if (item instanceof MethodRefItem) {
                    MethodRefItem methodRef = (MethodRefItem) item;
                    String methodName = methodRef.getName();
                    if (methodName != null && methodName.contains(searchTerm)) {
                        String ownerClass = methodRef.getClassName();
                        if (ownerClass == null) ownerClass = "?";
                        usages.add(new UsageInfo("Method call: " + ownerClass + "." + methodName, i));
                    }
                } else if (item instanceof InterfaceRefItem) {
                    InterfaceRefItem ifMethodRef = (InterfaceRefItem) item;
                    String methodName = ifMethodRef.getName();
                    if (methodName != null && methodName.contains(searchTerm)) {
                        String ownerClass = ifMethodRef.getOwner();
                        if (ownerClass != null) {
                            int lastSlash = ownerClass.lastIndexOf('/');
                            ownerClass = lastSlash >= 0 ? ownerClass.substring(lastSlash + 1) : ownerClass;
                        } else {
                            ownerClass = "?";
                        }
                        usages.add(new UsageInfo("Interface method call: " + ownerClass + "." + methodName, i));
                    }
                }
            } catch (Exception e) {
                // Skip invalid entries
            }
        }
    }

    private void findFieldUsages(List<Item<?>> items, String searchTerm, List<UsageInfo> usages) {
        for (int i = 1; i < items.size(); i++) {
            try {
                Item<?> item = items.get(i);
                if (item instanceof FieldRefItem) {
                    FieldRefItem fieldRef = (FieldRefItem) item;
                    String fieldName = fieldRef.getName();
                    if (fieldName != null && fieldName.contains(searchTerm)) {
                        String ownerClass = fieldRef.getClassName();
                        if (ownerClass == null) ownerClass = "?";
                        usages.add(new UsageInfo("Field access: " + ownerClass + "." + fieldName, i));
                    }
                }
            } catch (Exception e) {
                // Skip invalid entries
            }
        }
    }

    private void findStringUsages(List<Item<?>> items, String searchTerm, List<UsageInfo> usages) {
        for (int i = 1; i < items.size(); i++) {
            try {
                Item<?> item = items.get(i);
                if (item instanceof StringRefItem) {
                    StringRefItem stringRef = (StringRefItem) item;
                    int utf8Index = stringRef.getValue();
                    Item<?> utf8Item = items.get(utf8Index);
                    if (utf8Item instanceof Utf8Item) {
                        String str = ((Utf8Item) utf8Item).getValue();
                        if (str != null && str.toLowerCase().contains(searchTerm.toLowerCase())) {
                            String preview = str.length() > 50 ? str.substring(0, 50) + "..." : str;
                            usages.add(new UsageInfo("String: \"" + preview + "\"", i));
                        }
                    }
                }
            } catch (Exception e) {
                // Skip invalid entries
            }
        }
    }

    private void expandAll() {
        for (int i = 0; i < resultsTree.getRowCount(); i++) {
            resultsTree.expandRow(i);
        }
    }

    private void navigateToSelection() {
        TreePath path = resultsTree.getSelectionPath();
        if (path == null) return;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObject = node.getUserObject();

        if (userObject instanceof UsageNode) {
            UsageNode usageNode = (UsageNode) userObject;
            if (usageNode.classEntry != null) {
                EventBus.getInstance().post(new ClassSelectedEvent(this, usageNode.classEntry));
            }
        }
    }

    /**
     * Search for usages of a specific term.
     */
    public void searchFor(String term, String type) {
        searchField.setText(term);
        for (int i = 0; i < searchTypeCombo.getItemCount(); i++) {
            if (searchTypeCombo.getItemAt(i).equals(type)) {
                searchTypeCombo.setSelectedIndex(i);
                break;
            }
        }
        findUsages();
    }

    /**
     * Refresh the panel.
     */
    public void refresh() {
        // Nothing to refresh on open
    }

    // Data classes
    private static class UsageInfo {
        final String description;
        final int constPoolIndex;

        UsageInfo(String description, int constPoolIndex) {
            this.description = description;
            this.constPoolIndex = constPoolIndex;
        }
    }

    private static class UsageNode {
        final String label;
        final ClassEntryModel classEntry;
        final UsageInfo usageInfo;

        UsageNode(String label, ClassEntryModel classEntry, UsageInfo usageInfo) {
            this.label = label;
            this.classEntry = classEntry;
            this.usageInfo = usageInfo;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    // Custom tree cell renderer
    private static class UsageTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            setBackground(sel ? JStudioTheme.getSelection() : JStudioTheme.getBgTertiary());
            setForeground(JStudioTheme.getTextPrimary());

            if (value instanceof DefaultMutableTreeNode) {
                Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObject instanceof UsageNode) {
                    UsageNode node = (UsageNode) userObject;
                    if (node.usageInfo == null) {
                        // This is a class node
                        setForeground(JStudioTheme.getAccent());
                    } else {
                        // This is a usage node
                        setForeground(JStudioTheme.getTextPrimary());
                    }
                }
            }

            return this;
        }
    }
}
