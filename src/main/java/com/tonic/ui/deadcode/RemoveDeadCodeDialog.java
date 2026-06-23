package com.tonic.ui.deadcode;

import com.tonic.event.EventBus;
import com.tonic.event.events.ClassSelectedEvent;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.service.ProjectService;
import com.tonic.service.deadcode.DeadCodeAnalyzer;
import com.tonic.service.deadcode.DeadCodeConfig;
import com.tonic.service.deadcode.DeadCodeRemover;
import com.tonic.service.deadcode.DeadCodeReport;
import com.tonic.service.deadcode.DeadItem;
import com.tonic.ui.MainFrame;
import com.tonic.ui.core.SwingWorkers;
import com.tonic.util.Settings;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configure-and-preview dialog for the Remove Dead Code feature: pick entry-point options and keep/skip lists,
 * Analyze to populate a collapsible checkbox tree of everything found dead (classes / methods / fields), then
 * remove the checked items. There is no undo, so the preview is the safety net.
 */
public final class RemoveDeadCodeDialog extends JDialog {

    private final MainFrame mainFrame;
    private final JCheckBox publicBox = new JCheckBox("Treat public methods/fields as entry points");
    private final JTextArea keepArea = new JTextArea(4, 28);
    private final JTextArea skipArea = new JTextArea(4, 28);
    private final JButton analyzeButton = new JButton("Analyze");
    private final JButton removeButton = new JButton("Remove checked");
    private final JLabel status = new JLabel("Configure and Analyze.");
    private final JProgressBar progressBar = new JProgressBar();

    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(root);
    private final JTree tree = new JTree(treeModel);
    private final Set<DefaultMutableTreeNode> checked = new HashSet<>();

    public RemoveDeadCodeDialog(MainFrame mainFrame) {
        super(mainFrame, "Remove Dead Code", false);
        this.mainFrame = mainFrame;

        setContentPane(buildUi());
        setSize(720, 640);
        setLocationRelativeTo(mainFrame);
        loadSettings();

        analyzeButton.addActionListener(e -> analyze());
        removeButton.addActionListener(e -> removeChecked());
        removeButton.setEnabled(false);
    }

    private JPanel buildUi() {
        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel config = new JPanel(new BorderLayout(0, 4));
        config.add(publicBox, BorderLayout.NORTH);
        JPanel lists = new JPanel(new GridLayout(1, 2, 8, 0));
        lists.add(labeledArea("Keep (entry points) - com.foo.Bar#member", keepArea));
        lists.add(labeledArea("Skip classes entirely - com.foo.Bar", skipArea));
        config.add(lists, BorderLayout.CENTER);
        content.add(config, BorderLayout.NORTH);

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new CheckboxRenderer());
        tree.setToggleClickCount(0);
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onTreeClick(e);
            }
        });
        content.add(new JScrollPane(tree), BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(8, 0));
        south.add(status, BorderLayout.WEST);
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(160, 12));
        south.add(progressBar, BorderLayout.CENTER);
        JPanel buttons = new JPanel();
        buttons.add(analyzeButton);
        buttons.add(removeButton);
        south.add(buttons, BorderLayout.EAST);
        content.add(south, BorderLayout.SOUTH);
        return content;
    }

    private JPanel labeledArea(String label, JTextArea area) {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.add(new JLabel(label), BorderLayout.NORTH);
        area.setLineWrap(false);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        return panel;
    }

    private void loadSettings() {
        Settings s = Settings.getInstance();
        publicBox.setSelected(s.isDeadCodePublicEntryPoints());
        keepArea.setText(s.getDeadCodeKeepList());
        skipArea.setText(s.getDeadCodeSkipList());
    }

    private void saveSettings() {
        Settings s = Settings.getInstance();
        s.setDeadCodePublicEntryPoints(publicBox.isSelected());
        s.setDeadCodeKeepList(keepArea.getText());
        s.setDeadCodeSkipList(skipArea.getText());
    }

    private void analyze() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            status.setText("No project loaded.");
            return;
        }
        saveSettings();
        DeadCodeConfig config = new DeadCodeConfig(publicBox.isSelected(),
                toSet(keepArea.getText()), toSet(skipArea.getText()));
        DeadCodeAnalyzer analyzer = new DeadCodeAnalyzer(project, config);
        analyzer.setProgressListener(msg -> SwingUtilities.invokeLater(() -> status.setText(msg)));
        analyzeButton.setEnabled(false);
        removeButton.setEnabled(false);
        progressBar.setVisible(true);
        status.setText("Analyzing...");
        SwingWorkers.run(
                analyzer::analyze,
                report -> {
                    progressBar.setVisible(false);
                    populate(report);
                },
                err -> {
                    progressBar.setVisible(false);
                    analyzeButton.setEnabled(true);
                    status.setText("Analysis failed: " + err.getMessage());
                });
    }

    private void populate(DeadCodeReport report) {
        analyzeButton.setEnabled(true);
        checked.clear();
        root.removeAllChildren();

        if (!report.getDeadClasses().isEmpty()) {
            DefaultMutableTreeNode group = group("Dead classes (" + report.getDeadClasses().size() + ")");
            for (DeadItem item : report.getDeadClasses()) {
                addLeaf(group, item);
            }
        }
        Map<String, List<DeadItem>> byOwner = new LinkedHashMap<>();
        for (DeadItem item : report.getDeadMethods()) {
            byOwner.computeIfAbsent(item.getOwner(), k -> new ArrayList<>()).add(item);
        }
        for (DeadItem item : report.getDeadFields()) {
            byOwner.computeIfAbsent(item.getOwner(), k -> new ArrayList<>()).add(item);
        }
        for (Map.Entry<String, List<DeadItem>> e : byOwner.entrySet()) {
            DefaultMutableTreeNode group = group(e.getKey().replace('/', '.') + "  (" + e.getValue().size() + ")");
            for (DeadItem item : e.getValue()) {
                addLeaf(group, item);
            }
        }

        treeModel.reload();
        removeButton.setEnabled(!report.isEmpty());
        status.setText(report.isEmpty() ? "No dead code found."
                : report.getDeadClasses().size() + " classes, " + report.getDeadMethods().size()
                + " methods, " + report.getDeadFields().size() + " fields");
    }

    private DefaultMutableTreeNode group(String label) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(label);
        root.add(node);
        checked.add(node);
        return node;
    }

    private void addLeaf(DefaultMutableTreeNode group, DeadItem item) {
        DefaultMutableTreeNode leaf = new DefaultMutableTreeNode(item);
        group.add(leaf);
        checked.add(leaf);
    }

    private void onTreeClick(MouseEvent e) {
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
            return;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (e.getClickCount() == 2 && node.getUserObject() instanceof DeadItem) {
            navigate((DeadItem) node.getUserObject());
            return;
        }
        Rectangle bounds = tree.getPathBounds(path);
        if (bounds != null && e.getX() <= bounds.x + 18) {
            setChecked(node, !checked.contains(node));
            tree.repaint();
        }
    }

    private void setChecked(DefaultMutableTreeNode node, boolean state) {
        if (state) {
            checked.add(node);
        } else {
            checked.remove(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            setChecked((DefaultMutableTreeNode) node.getChildAt(i), state);
        }
    }

    private void navigate(DeadItem item) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            return;
        }
        ClassEntryModel entry = project.getClass(item.getOwner());
        if (entry == null) {
            return;
        }
        if (item.getKind() == DeadItem.Kind.METHOD) {
            EventBus.getInstance().post(new ClassSelectedEvent(this, entry, item.getName(), -1));
        } else {
            EventBus.getInstance().post(new ClassSelectedEvent(this, entry));
        }
    }

    private void removeChecked() {
        List<DeadItem> items = new ArrayList<>();
        collectChecked(root, items);
        if (items.isEmpty()) {
            status.setText("Nothing checked.");
            return;
        }
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Remove " + items.size() + " checked item(s)? This cannot be undone - export/save your work first.",
                "Remove Dead Code", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        com.tonic.service.history.LocalHistoryService.getInstance()
                .snapshot("Remove dead members", com.tonic.model.Snapshot.Trigger.DEAD_CODE);
        DeadCodeRemover.Result result = DeadCodeRemover.apply(project, items);
        project.markDirty();
        mainFrame.refreshAfterDeadCodeRemoval(result.getRemovedClasses());
        checked.clear();
        root.removeAllChildren();
        treeModel.reload();
        removeButton.setEnabled(false);
        status.setText("Removed " + result.getClassesRemoved() + " classes, "
                + result.getMethodsRemoved() + " methods, " + result.getFieldsRemoved() + " fields.");
    }

    private void collectChecked(DefaultMutableTreeNode node, List<DeadItem> out) {
        if (node.isLeaf() && node.getUserObject() instanceof DeadItem && checked.contains(node)) {
            out.add((DeadItem) node.getUserObject());
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectChecked((DefaultMutableTreeNode) node.getChildAt(i), out);
        }
    }

    private static Set<String> toSet(String text) {
        Set<String> set = new HashSet<>();
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                set.add(t);
            }
        }
        return set;
    }

    /** Renders each node as a checkbox + label, reflecting the {@link #checked} state. */
    private final class CheckboxRenderer extends JPanel implements TreeCellRenderer {
        private final JCheckBox box = new JCheckBox();
        private final JLabel label = new JLabel();

        CheckboxRenderer() {
            super(new BorderLayout(2, 0));
            setOpaque(false);
            box.setOpaque(false);
            add(box, BorderLayout.WEST);
            add(label, BorderLayout.CENTER);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree t, Object value, boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            box.setSelected(checked.contains(node));
            Object userObject = node.getUserObject();
            label.setText(userObject instanceof DeadItem ? ((DeadItem) userObject).displayLabel()
                    : String.valueOf(userObject));
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(d.width + 8, Math.max(d.height, box.getPreferredSize().height));
        }
    }
}
