package com.tonic.ui.analysis;

import com.tonic.analysis.xref.*;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.ClassSelectedEvent;
import com.tonic.ui.event.events.ShowXrefsEvent;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.util.JdkClassFilter;

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

/**
 * Panel for displaying cross-references (xrefs) to and from a symbol.
 * Split view showing incoming (who references this?) and outgoing (what does this reference?).
 */
public class XrefPanel extends ThemedJPanel {

    private final ProjectModel project;
    private XrefDatabase xrefDatabase;

    private JTextField searchField;
    private JComboBox<XrefType> typeFilterCombo;
    private JButton buildButton;
    private final JLabel statusLabel;
    private final JLabel targetLabel;

    private final JTree incomingTree;
    private final JTree outgoingTree;
    private final DefaultMutableTreeNode incomingRoot;
    private final DefaultMutableTreeNode outgoingRoot;
    private final DefaultTreeModel incomingModel;
    private final DefaultTreeModel outgoingModel;

    private String currentTargetClass;
    private String currentTargetMember;
    private String currentTargetDesc;
    private ShowXrefsEvent.TargetType currentTargetType;

    public XrefPanel(ProjectModel project) {
        super(BackgroundStyle.SECONDARY, new BorderLayout());
        this.project = project;

        // Top toolbar
        JPanel toolbar = createToolbar();
        add(toolbar, BorderLayout.NORTH);

        // Target display
        targetLabel = new JLabel("Target: (none selected)");
        targetLabel.setForeground(JStudioTheme.getAccent());
        targetLabel.setFont(JStudioTheme.getCodeFont(12).deriveFont(Font.BOLD));
        targetLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        targetLabel.setBackground(JStudioTheme.getBgTertiary());
        targetLabel.setOpaque(true);

        // Create split pane with incoming/outgoing trees
        JSplitPane splitPane = createSplitPane();

        // Center panel
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(JStudioTheme.getBgSecondary());
        centerPanel.add(targetLabel, BorderLayout.NORTH);
        centerPanel.add(splitPane, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // Status bar
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBackground(JStudioTheme.getBgSecondary());
        statusLabel = new JLabel("Click 'Build Xrefs' to analyze cross-references.");
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        statusPanel.add(statusLabel);
        add(statusPanel, BorderLayout.SOUTH);

        // Initialize trees
        incomingRoot = new DefaultMutableTreeNode("Incoming References (To)");
        outgoingRoot = new DefaultMutableTreeNode("Outgoing References (From)");
        incomingModel = new DefaultTreeModel(incomingRoot);
        outgoingModel = new DefaultTreeModel(outgoingRoot);
        incomingTree = createTree(incomingModel);
        outgoingTree = createTree(outgoingModel);

        // Add trees to split pane
        JScrollPane incomingScroll = createScrollPane(incomingTree, "Incoming (To)");
        JScrollPane outgoingScroll = createScrollPane(outgoingTree, "Outgoing (From)");
        splitPane.setLeftComponent(incomingScroll);
        splitPane.setRightComponent(outgoingScroll);

        // Register for xref events
        EventBus.getInstance().register(ShowXrefsEvent.class, this::handleShowXrefsEvent);
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.setBackground(JStudioTheme.getBgSecondary());

        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setForeground(JStudioTheme.getTextPrimary());
        toolbar.add(searchLabel);

        searchField = new JTextField(15);
        searchField.setBackground(JStudioTheme.getBgTertiary());
        searchField.setForeground(JStudioTheme.getTextPrimary());
        searchField.setCaretColor(JStudioTheme.getTextPrimary());
        searchField.addActionListener(e -> searchXrefs());
        toolbar.add(searchField);

        JLabel filterLabel = new JLabel("Type:");
        filterLabel.setForeground(JStudioTheme.getTextPrimary());
        toolbar.add(filterLabel);

        typeFilterCombo = new JComboBox<>();
        typeFilterCombo.addItem(null); // "All" option
        for (XrefType type : XrefType.values()) {
            typeFilterCombo.addItem(type);
        }
        typeFilterCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value == null) {
                    setText("All Types");
                } else {
                    setText(((XrefType) value).getDisplayName());
                }
                return this;
            }
        });
        typeFilterCombo.setBackground(JStudioTheme.getBgTertiary());
        typeFilterCombo.setForeground(JStudioTheme.getTextPrimary());
        typeFilterCombo.addActionListener(e -> refreshDisplay());
        toolbar.add(typeFilterCombo);

        buildButton = new JButton("Build Xrefs");
        buildButton.setBackground(JStudioTheme.getBgTertiary());
        buildButton.setForeground(JStudioTheme.getTextPrimary());
        buildButton.addActionListener(e -> buildXrefDatabase());
        toolbar.add(buildButton);

        return toolbar;
    }

    private JSplitPane createSplitPane() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerLocation(0.5);
        splitPane.setBackground(JStudioTheme.getBgSecondary());
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        return splitPane;
    }

    private JTree createTree(DefaultTreeModel model) {
        JTree tree = new JTree(model);
        tree.setBackground(JStudioTheme.getBgTertiary());
        tree.setForeground(JStudioTheme.getTextPrimary());
        tree.setFont(JStudioTheme.getCodeFont(11));
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new XrefTreeCellRenderer());

        // Double-click to navigate
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateToSelection(tree);
                }
            }
        });

        return tree;
    }

    private JScrollPane createScrollPane(JTree tree, String title) {
        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JStudioTheme.getBorder()), title,
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP,
            JStudioTheme.getCodeFont(11),
            JStudioTheme.getAccent()));
        scrollPane.getViewport().setBackground(JStudioTheme.getBgTertiary());
        return scrollPane;
    }

    /**
     * Build the xref database from the current project.
     */
    public void buildXrefDatabase() {
        if (project.getClassPool() == null) {
            statusLabel.setText("No project loaded.");
            return;
        }

        buildButton.setEnabled(false);
        statusLabel.setText("Building cross-reference database...");

        SwingWorker<XrefDatabase, String> worker = new SwingWorker<>() {
            @Override
            protected XrefDatabase doInBackground() {
                XrefBuilder builder = new XrefBuilder(project.getClassPool());
                builder.setProgressCallback(msg -> publish(msg));
                return builder.build();
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
                    xrefDatabase = get();
                    project.setXrefDatabase(xrefDatabase);
                    statusLabel.setText(xrefDatabase.getSummary());
                    refreshDisplay();
                } catch (Exception e) {
                    statusLabel.setText("Error building xrefs: " + e.getMessage());
                }
                buildButton.setEnabled(true);
            }
        };

        worker.execute();
    }

    /**
     * Handle ShowXrefsEvent - display xrefs for the specified target.
     */
    private void handleShowXrefsEvent(ShowXrefsEvent event) {
        currentTargetClass = event.getClassName();
        currentTargetMember = event.getMemberName();
        currentTargetDesc = event.getMemberDescriptor();
        currentTargetType = event.getTargetType();

        targetLabel.setText("Target: " + event.getTargetDisplay());
        searchField.setText("");

        if (xrefDatabase == null || xrefDatabase.isEmpty()) {
            statusLabel.setText("Xref database not built. Click 'Build Xrefs' first.");
            return;
        }

        refreshDisplay();
    }

    /**
     * Search xrefs by the search field text.
     */
    private void searchXrefs() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            refreshDisplay();
            return;
        }

        if (xrefDatabase == null || xrefDatabase.isEmpty()) {
            statusLabel.setText("Xref database not built. Click 'Build Xrefs' first.");
            return;
        }

        // Set target based on search
        currentTargetClass = query.replace('.', '/');
        currentTargetMember = null;
        currentTargetDesc = null;
        currentTargetType = ShowXrefsEvent.TargetType.CLASS;
        targetLabel.setText("Target: " + query);

        refreshDisplay();
    }

    /**
     * Refresh the tree display with current filters.
     */
    private void refreshDisplay() {
        incomingRoot.removeAllChildren();
        outgoingRoot.removeAllChildren();

        if (xrefDatabase == null || currentTargetClass == null) {
            incomingModel.reload();
            outgoingModel.reload();
            return;
        }

        XrefType filterType = (XrefType) typeFilterCombo.getSelectedItem();

        // Get incoming refs
        List<Xref> incomingRefs;
        List<Xref> outgoingRefs;

        switch (currentTargetType) {
            case METHOD:
                incomingRefs = xrefDatabase.getRefsToMethod(currentTargetClass, currentTargetMember, currentTargetDesc);
                outgoingRefs = xrefDatabase.getRefsFromMethod(currentTargetClass, currentTargetMember, currentTargetDesc);
                break;
            case FIELD:
                incomingRefs = xrefDatabase.getRefsToField(currentTargetClass, currentTargetMember, currentTargetDesc);
                outgoingRefs = new ArrayList<>(); // Fields don't have outgoing refs
                break;
            case CLASS:
            default:
                incomingRefs = xrefDatabase.getRefsToClass(currentTargetClass);
                outgoingRefs = xrefDatabase.getRefsFromClass(currentTargetClass);
                break;
        }

        // Apply type filter
        if (filterType != null) {
            incomingRefs = filterByType(incomingRefs, filterType);
            outgoingRefs = filterByType(outgoingRefs, filterType);
        }

        // Build incoming tree (group by type)
        buildGroupedTree(incomingRoot, incomingRefs, true);
        buildGroupedTree(outgoingRoot, outgoingRefs, false);

        incomingModel.reload();
        outgoingModel.reload();
        expandAll(incomingTree);
        expandAll(outgoingTree);

        statusLabel.setText(String.format("Found %d incoming, %d outgoing references.",
            incomingRefs.size(), outgoingRefs.size()));
    }

    private List<Xref> filterByType(List<Xref> refs, XrefType type) {
        List<Xref> filtered = new ArrayList<>();
        for (Xref ref : refs) {
            if (ref.getType() == type) {
                filtered.add(ref);
            }
        }
        return filtered;
    }

    private void buildGroupedTree(DefaultMutableTreeNode root, List<Xref> refs, boolean incoming) {
        // Group by XrefType, filtering out JDK references
        Map<XrefType, List<Xref>> grouped = new LinkedHashMap<>();
        for (Xref ref : refs) {
            String className = incoming ? ref.getSourceClass() : ref.getTargetClass();
            if (JdkClassFilter.isJdkClass(className)) {
                continue;
            }
            grouped.computeIfAbsent(ref.getType(), k -> new ArrayList<>()).add(ref);
        }

        for (Map.Entry<XrefType, List<Xref>> entry : grouped.entrySet()) {
            XrefType type = entry.getKey();
            List<Xref> typeRefs = entry.getValue();

            DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(
                new XrefNode(type.getDisplayName() + " (" + typeRefs.size() + ")", null, type));

            for (Xref ref : typeRefs) {
                String label = incoming ? ref.getSourceDisplay() : ref.getTargetDisplay();
                typeNode.add(new DefaultMutableTreeNode(new XrefNode(label, ref, type)));
            }

            root.add(typeNode);
        }
    }

    private void expandAll(JTree tree) {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    private void navigateToSelection(JTree tree) {
        TreePath path = tree.getSelectionPath();
        if (path == null) return;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObject = node.getUserObject();

        if (userObject instanceof XrefNode) {
            XrefNode xrefNode = (XrefNode) userObject;
            if (xrefNode.xref != null) {
                // Navigate to the source class
                String className = xrefNode.xref.getSourceClass();
                ClassEntryModel classEntry = project.findClassByName(className);
                if (classEntry != null) {
                    EventBus.getInstance().post(new ClassSelectedEvent(this, classEntry));
                }
            }
        }
    }

    /**
     * Set the xref database directly (for external initialization).
     */
    public void setXrefDatabase(XrefDatabase database) {
        this.xrefDatabase = database;
        if (database != null) {
            statusLabel.setText(database.getSummary());
        }
    }

    /**
     * Get the current xref database.
     */
    public XrefDatabase getXrefDatabase() {
        return xrefDatabase;
    }

    /**
     * Refresh the panel (rebuild if necessary).
     */
    public void refresh() {
        if (xrefDatabase == null || xrefDatabase.isEmpty()) {
            if (project.getClassPool() != null) {
                buildXrefDatabase();
            }
        }
    }

    /**
     * Show xrefs for a specific class.
     */
    public void showXrefsForClass(String className) {
        currentTargetClass = className;
        currentTargetMember = null;
        currentTargetDesc = null;
        currentTargetType = ShowXrefsEvent.TargetType.CLASS;
        targetLabel.setText("Target: " + className.replace('/', '.'));
        searchField.setText("");
        refreshDisplay();
    }

    /**
     * Show xrefs for a specific method.
     */
    public void showXrefsForMethod(String className, String methodName, String methodDesc) {
        currentTargetClass = className;
        currentTargetMember = methodName;
        currentTargetDesc = methodDesc;
        currentTargetType = ShowXrefsEvent.TargetType.METHOD;
        targetLabel.setText("Target: " + className.replace('/', '.') + "." + methodName + "()");
        searchField.setText("");
        refreshDisplay();
    }

    /**
     * Show xrefs for a specific field.
     */
    public void showXrefsForField(String className, String fieldName, String fieldDesc) {
        currentTargetClass = className;
        currentTargetMember = fieldName;
        currentTargetDesc = fieldDesc;
        currentTargetType = ShowXrefsEvent.TargetType.FIELD;
        targetLabel.setText("Target: " + className.replace('/', '.') + "." + fieldName);
        searchField.setText("");
        refreshDisplay();
    }

    // Data class for tree nodes
    private static class XrefNode {
        final String label;
        final Xref xref;
        final XrefType type;

        XrefNode(String label, Xref xref, XrefType type) {
            this.label = label;
            this.xref = xref;
            this.type = type;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    // Custom tree cell renderer
    private static class XrefTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            setBackground(sel ? JStudioTheme.getSelection() : JStudioTheme.getBgTertiary());
            setForeground(JStudioTheme.getTextPrimary());

            if (value instanceof DefaultMutableTreeNode) {
                Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObject instanceof XrefNode) {
                    XrefNode node = (XrefNode) userObject;
                    if (node.xref == null) {
                        // This is a category node (type header)
                        setForeground(JStudioTheme.getAccent());
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else {
                        // This is an individual xref
                        setForeground(JStudioTheme.getTextPrimary());
                        setFont(getFont().deriveFont(Font.PLAIN));
                    }
                }
            }

            return this;
        }
    }
}
