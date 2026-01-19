package com.tonic.ui.analysis;

import com.tonic.analysis.xref.Xref;
import com.tonic.analysis.xref.XrefBuilder;
import com.tonic.analysis.xref.XrefDatabase;
import com.tonic.analysis.xref.XrefType;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.editor.EditorPanel;
import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.ClassSelectedEvent;
import com.tonic.ui.event.events.FindUsagesEvent;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeChangeListener;
import com.tonic.ui.theme.ThemeManager;
import com.tonic.ui.util.JdkClassFilter;
import lombok.Getter;
import lombok.Setter;

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

public class FindUsagesResultsPanel extends ThemedJPanel implements ThemeChangeListener {

    @Setter
    private ProjectModel project;

    @Setter
    private EditorPanel editorPanel;

    private final JTree resultsTree;
    private final DefaultMutableTreeNode rootNode;
    private final DefaultTreeModel treeModel;
    private final JLabel statusLabel;
    private final JScrollPane scrollPane;

    @Getter
    private String tabTitle = "Find Usages";

    public FindUsagesResultsPanel() {
        super(BackgroundStyle.SECONDARY, new BorderLayout());

        rootNode = new DefaultMutableTreeNode("Usages");
        treeModel = new DefaultTreeModel(rootNode);
        resultsTree = new JTree(treeModel);
        resultsTree.setBackground(JStudioTheme.getBgTertiary());
        resultsTree.setForeground(JStudioTheme.getTextPrimary());
        resultsTree.setFont(JStudioTheme.getCodeFont(11));
        resultsTree.setRootVisible(false);
        resultsTree.setShowsRootHandles(true);
        resultsTree.setCellRenderer(new UsageTreeCellRenderer());

        resultsTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateToSelection();
                }
            }
        });

        scrollPane = new JScrollPane(resultsTree);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());
        add(scrollPane, BorderLayout.CENTER);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        statusPanel.setBackground(JStudioTheme.getBgSecondary());
        statusLabel = new JLabel("Double-click to navigate.");
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        statusLabel.setFont(JStudioTheme.getUIFont(11));
        statusPanel.add(statusLabel);
        add(statusPanel, BorderLayout.SOUTH);

        setPreferredSize(new Dimension(0, 200));
        setMinimumSize(new Dimension(0, 100));

        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    public void showUsages(FindUsagesEvent event) {
        this.tabTitle = event.getTargetDisplay();

        if (project == null || project.getClassPool() == null) {
            statusLabel.setText("No project loaded.");
            rootNode.removeAllChildren();
            treeModel.reload();
            return;
        }

        statusLabel.setText("Searching...");
        rootNode.removeAllChildren();
        treeModel.reload();

        SwingWorker<Map<XrefType, List<Xref>>, String> worker = new SwingWorker<>() {
            @Override
            protected Map<XrefType, List<Xref>> doInBackground() {
                ensureXrefDatabase();
                publish("Querying database...");
                List<Xref> results = queryXrefs(event);
                return groupByType(results);
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    statusLabel.setText(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                try {
                    Map<XrefType, List<Xref>> grouped = get();
                    buildResultsTree(grouped);
                    treeModel.reload();
                    expandAll();

                    int totalUsages = grouped.values().stream().mapToInt(List::size).sum();
                    int typeCount = grouped.size();
                    statusLabel.setText("Found " + totalUsages + " usages in " + typeCount + " categories. Double-click to navigate.");
                } catch (Exception e) {
                    statusLabel.setText("Error: " + e.getMessage());
                }
            }
        };

        worker.execute();
        setVisible(true);
    }

    private void ensureXrefDatabase() {
        if (project.getXrefDatabase() == null || project.getXrefDatabase().isEmpty()) {
            XrefBuilder builder = new XrefBuilder(project.getClassPool());
            XrefDatabase db = builder.build();
            project.setXrefDatabase(db);
        }
    }

    private List<Xref> queryXrefs(FindUsagesEvent event) {
        XrefDatabase db = project.getXrefDatabase();
        if (db == null) {
            return Collections.emptyList();
        }

        List<Xref> results;
        switch (event.getTargetType()) {
            case METHOD:
                results = db.getRefsToMethod(event.getClassName(), event.getMemberName(), event.getMemberDescriptor());
                break;
            case FIELD:
                results = db.getRefsToField(event.getClassName(), event.getMemberName(), event.getMemberDescriptor());
                break;
            case CLASS:
            default:
                results = db.getRefsToClass(event.getClassName());
                break;
        }

        List<Xref> filtered = new ArrayList<>();
        for (Xref xref : results) {
            if (!JdkClassFilter.isJdkClass(xref.getSourceClass())
                && xref.getSourceMethod() != null) {
                filtered.add(xref);
            }
        }
        return filtered;
    }

    private Map<XrefType, List<Xref>> groupByType(List<Xref> results) {
        Map<XrefType, List<Xref>> grouped = new LinkedHashMap<>();
        for (Xref xref : results) {
            grouped.computeIfAbsent(xref.getType(), k -> new ArrayList<>()).add(xref);
        }
        return grouped;
    }

    private void buildResultsTree(Map<XrefType, List<Xref>> grouped) {
        rootNode.removeAllChildren();

        for (Map.Entry<XrefType, List<Xref>> entry : grouped.entrySet()) {
            XrefType type = entry.getKey();
            List<Xref> xrefs = entry.getValue();

            DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(
                    new UsageNode(type.getDisplayName() + " (" + xrefs.size() + ")", null, type));

            for (Xref xref : xrefs) {
                String label = xref.getSourceDisplay();
                typeNode.add(new DefaultMutableTreeNode(new UsageNode(label, xref, type)));
            }

            rootNode.add(typeNode);
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
            if (usageNode.xref != null) {
                Xref xref = usageNode.xref;
                String className = xref.getSourceClass();
                ClassEntryModel classEntry = project.findClassByName(className);
                if (classEntry != null) {
                    EventBus.getInstance().post(new ClassSelectedEvent(this, classEntry));

                    if (editorPanel != null) {
                        int lineNumber = xref.getLineNumber();
                        String methodName = xref.getSourceMethod();
                        String methodDesc = xref.getSourceMethodDesc();

                        SwingUtilities.invokeLater(() -> {
                            if (lineNumber > 0) {
                                editorPanel.goToLineAndHighlight(lineNumber);
                            } else if (methodName != null && !methodName.isEmpty()) {
                                MethodEntryModel method = classEntry.getMethod(methodName, methodDesc);
                                if (method != null) {
                                    editorPanel.scrollToMethod(method);
                                }
                            }
                        });
                    }
                }
            }
        }
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        SwingUtilities.invokeLater(this::applyThemeToComponents);
    }

    private void applyThemeToComponents() {
        resultsTree.setBackground(JStudioTheme.getBgTertiary());
        resultsTree.setForeground(JStudioTheme.getTextPrimary());
        scrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
    }

    private static class UsageNode {
        final String label;
        final Xref xref;
        final XrefType type;

        UsageNode(String label, Xref xref, XrefType type) {
            this.label = label;
            this.xref = xref;
            this.type = type;
        }

        @Override
        public String toString() {
            return label;
        }
    }

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
                    if (node.xref == null) {
                        setForeground(JStudioTheme.getAccent());
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else {
                        setForeground(JStudioTheme.getTextPrimary());
                        setFont(getFont().deriveFont(Font.PLAIN));
                    }
                }
            }

            return this;
        }
    }
}
