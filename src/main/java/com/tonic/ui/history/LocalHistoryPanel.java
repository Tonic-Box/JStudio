package com.tonic.ui.history;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.model.Snapshot;
import com.tonic.parser.ClassFile;
import com.tonic.service.ProjectService;
import com.tonic.service.history.LocalHistoryService;
import com.tonic.ui.MainFrame;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Bottom-tab browser for Local History. Lists snapshots newest-first; expanding a snapshot shows the classes it
 * changed (vs the previous snapshot), each a clickable link that opens a "Current vs Snapshot" diff in a center tab.
 * Snapshots can be restored (whole-project or per-class) and deleted. Reads the {@link LocalHistoryService} live.
 */
public final class LocalHistoryPanel extends ThemedJPanel {

    private static final String LOADING = "Loading...";
    private static final String NO_DIFF = "(no differences from current)";

    private final JTree tree;
    private final JLabel statusLabel;

    public LocalHistoryPanel() {
        super(BackgroundStyle.PRIMARY, new BorderLayout());

        add(buildToolbar(), BorderLayout.NORTH);

        tree = new JTree(new DefaultTreeModel(new DefaultMutableTreeNode()));
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setBackground(JStudioTheme.getBgPrimary());
        tree.setCellRenderer(new HistoryCellRenderer());
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ClassRow row = classRowAt(e);
                if (row != null && e.getClickCount() == 1) {
                    openDiff(row.snapshot, row.internalName);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }
        });
        tree.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                tree.setCursor(classRowAt(e) != null
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
            }
        });
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                if (node.getUserObject() instanceof SnapRow && isUnloaded(node)) {
                    populateSnapshot(node, ((SnapRow) node.getUserObject()).snapshot);
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {
                // no-op
            }
        });

        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(JStudioTheme.getBgPrimary());
        add(scroll, BorderLayout.CENTER);

        statusLabel = new JLabel();
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(statusLabel, BorderLayout.SOUTH);

        LocalHistoryService.getInstance().addListener(this::refresh);
        refresh();
    }

    private JToolBar buildToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(JStudioTheme.getBgSecondary());
        toolbar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        toolbar.add(toolButton("Create Checkpoint", "save", e -> createCheckpoint()));
        toolbar.add(toolButton("Restore selected snapshot", "undo", e -> restoreSelected()));
        toolbar.add(toolButton("Delete selected snapshot", "delete", e -> deleteSelected()));
        toolbar.addSeparator();
        toolbar.add(toolButton("Refresh", "refresh", e -> refresh()));
        return toolbar;
    }

    private JButton toolButton(String tooltip, String icon, ActionListener action) {
        JButton button = new JButton(Icons.getIcon(icon));
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setPreferredSize(new Dimension(28, 28));
        button.addActionListener(action);
        return button;
    }

    private void refresh() {
        LocalHistoryService service = LocalHistoryService.getInstance();
        List<Snapshot> snapshots = service.list();
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        for (Snapshot snapshot : snapshots) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(new SnapRow(snapshot));
            node.add(new DefaultMutableTreeNode(LOADING));
            root.add(node);
        }
        tree.setModel(new DefaultTreeModel(root));

        if (!service.isEnabled()) {
            statusLabel.setText("History unavailable - open a project from a file to enable it.");
        } else if (snapshots.isEmpty()) {
            statusLabel.setText("No snapshots yet - they're created automatically before edits, on save, and on demand.");
        } else {
            statusLabel.setText(snapshots.size() + " snapshot" + (snapshots.size() == 1 ? "" : "s"));
        }
    }

    private static boolean isUnloaded(DefaultMutableTreeNode node) {
        return node.getChildCount() == 1
                && LOADING.equals(((DefaultMutableTreeNode) node.getChildAt(0)).getUserObject());
    }

    /** Lazily fills a snapshot node with the classes whose bytes differ from the CURRENT project - its diffable links. */
    private void populateSnapshot(DefaultMutableTreeNode node, Snapshot snapshot) {
        node.removeAllChildren();
        Map<String, String> current = LocalHistoryService.getInstance().currentClassHashes();
        for (String name : new TreeSet<>(snapshot.getClasses().keySet())) {
            if (!snapshot.getClasses().get(name).equals(current.get(name))) {
                node.add(new DefaultMutableTreeNode(new ClassRow(snapshot, name)));
            }
        }
        if (node.getChildCount() == 0) {
            node.add(new DefaultMutableTreeNode(NO_DIFF));
        }
        ((DefaultTreeModel) tree.getModel()).nodeStructureChanged(node);
    }

    private void createCheckpoint() {
        if (!ensureEnabled()) {
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Checkpoint name:", "Create Checkpoint",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null) {
            return;
        }
        String label = name.trim().isEmpty() ? Snapshot.Trigger.MANUAL.getDefaultLabel() : name.trim();
        Snapshot created = LocalHistoryService.getInstance().snapshot(label, Snapshot.Trigger.MANUAL);
        if (created == null) {
            JOptionPane.showMessageDialog(this, "No changes since the last snapshot.", "Create Checkpoint",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void restoreSelected() {
        Snapshot snapshot = selectedSnapshot();
        if (snapshot == null) {
            JOptionPane.showMessageDialog(this, "Select a snapshot to restore.", "Restore",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Restore the whole project to \"" + snapshot.getLabel() + "\" (" + time(snapshot) + ")?\n"
                        + "Current state is snapshotted first.",
                "Restore Snapshot", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        LocalHistoryService service = LocalHistoryService.getInstance();
        service.snapshot("Before restore to " + snapshot.getLabel(), Snapshot.Trigger.MANUAL);
        if (service.restore(snapshot)) {
            refreshProjectUi();
        } else {
            JOptionPane.showMessageDialog(this, "Restore failed - see the console.", "Restore",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelected() {
        Snapshot snapshot = selectedSnapshot();
        if (snapshot == null) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete snapshot \"" + snapshot.getLabel() + "\" (" + time(snapshot) + ")?",
                "Delete Snapshot", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            LocalHistoryService.getInstance().delete(snapshot);
        }
    }

    private void showContextMenu(MouseEvent e) {
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
            return;
        }
        tree.setSelectionPath(path);
        Object userObject = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        JPopupMenu menu = new JPopupMenu();
        if (userObject instanceof ClassRow) {
            ClassRow row = (ClassRow) userObject;
            JMenuItem diff = new JMenuItem("Show Diff vs Current");
            diff.addActionListener(ev -> openDiff(row.snapshot, row.internalName));
            menu.add(diff);
            JMenuItem restore = new JMenuItem("Restore This Class");
            restore.addActionListener(ev -> restoreClass(row.snapshot, row.internalName));
            menu.add(restore);
        } else if (userObject instanceof SnapRow) {
            JMenuItem restore = new JMenuItem("Restore Whole Project...");
            restore.addActionListener(ev -> restoreSelected());
            menu.add(restore);
            JMenuItem delete = new JMenuItem("Delete Snapshot...");
            delete.addActionListener(ev -> deleteSelected());
            menu.add(delete);
        }
        if (menu.getComponentCount() > 0) {
            menu.show(tree, e.getX(), e.getY());
        }
    }

    private void restoreClass(Snapshot snapshot, String internalName) {
        LocalHistoryService service = LocalHistoryService.getInstance();
        service.snapshot("Before restore " + simpleName(internalName), Snapshot.Trigger.MANUAL);
        if (service.restoreClass(snapshot, internalName)) {
            refreshProjectUi();
        }
    }

    private void openDiff(Snapshot snapshot, String internalName) {
        MainFrame frame = mainFrame();
        if (frame == null) {
            return;
        }
        byte[] oldBytes = LocalHistoryService.getInstance().classBytes(snapshot, internalName);
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        ClassEntryModel current = project != null ? project.getClass(internalName) : null;
        new SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() {
                // Decompile both sides fresh (never the cache - after a recompile the cache holds the user's
                // hand-edited source, which would diff noisily against the snapshot's decompiler output).
                String oldSource = oldBytes != null ? decompile(oldBytes) : "// (class not present in this snapshot)";
                String newSource = current == null
                        ? "// (class not present in the current project)"
                        : decompile(current.getClassFile());
                return new String[]{oldSource, newSource};
            }

            @Override
            protected void done() {
                try {
                    String[] sources = get();
                    JComponent view = new HistoryDiffView("Snapshot " + time(snapshot), "Current",
                            sources[0], sources[1]);
                    frame.getEditorPanel().openCustomView("history-diff:" + snapshot.getId() + ":" + internalName,
                            "Diff: " + simpleName(internalName), Icons.getIcon("undo"), view);
                } catch (Throwable t) {
                    com.tonic.service.ConsoleLogService.getInstance().error("History: diff failed for " + internalName, t);
                }
            }
        }.execute();
    }

    private static String decompile(byte[] bytes) {
        try {
            return decompile(new ClassFile(new ByteArrayInputStream(bytes)));
        } catch (Exception e) {
            return "// decompile failed: " + e.getMessage();
        }
    }

    private static String decompile(ClassFile classFile) {
        try {
            return new ClassDecompiler(classFile).decompile();
        } catch (Exception e) {
            return "// decompile failed: " + e.getMessage();
        }
    }

    private boolean ensureEnabled() {
        if (LocalHistoryService.getInstance().isEnabled()) {
            return true;
        }
        JOptionPane.showMessageDialog(this, "History is unavailable for this project (no backing file).",
                "Local History", JOptionPane.INFORMATION_MESSAGE);
        return false;
    }

    private void refreshProjectUi() {
        MainFrame frame = mainFrame();
        if (frame != null) {
            frame.refreshAfterProjectChange();
        }
    }

    private Snapshot selectedSnapshot() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return null;
        }
        Object userObject = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if (userObject instanceof SnapRow) {
            return ((SnapRow) userObject).snapshot;
        }
        if (userObject instanceof ClassRow) {
            return ((ClassRow) userObject).snapshot;
        }
        return null;
    }

    private ClassRow classRowAt(MouseEvent e) {
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
            return null;
        }
        Object userObject = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        return userObject instanceof ClassRow ? (ClassRow) userObject : null;
    }

    private MainFrame mainFrame() {
        java.awt.Window window = SwingUtilities.getWindowAncestor(this);
        if (window instanceof MainFrame) {
            return (MainFrame) window;
        }
        for (java.awt.Window candidate : java.awt.Window.getWindows()) {
            if (candidate instanceof MainFrame) {
                return (MainFrame) candidate;
            }
        }
        return null;
    }

    private static String simpleName(String internalName) {
        int slash = internalName.lastIndexOf('/');
        return slash >= 0 ? internalName.substring(slash + 1) : internalName;
    }

    private static String time(Snapshot snapshot) {
        return new SimpleDateFormat("MMM d, HH:mm:ss").format(new Date(snapshot.getTimestampMs()));
    }

    /** Tree node payload for a snapshot row. */
    private static final class SnapRow {
        final Snapshot snapshot;

        SnapRow(Snapshot snapshot) {
            this.snapshot = snapshot;
        }
    }

    /** Tree node payload for a changed-class (link) row. */
    private static final class ClassRow {
        final Snapshot snapshot;
        final String internalName;

        ClassRow(Snapshot snapshot, String internalName) {
            this.snapshot = snapshot;
            this.internalName = internalName;
        }
    }

    private static final class HistoryCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree t, Object value, boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean focus) {
            super.getTreeCellRendererComponent(t, value, selected, expanded, leaf, row, focus);
            Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
            if (userObject instanceof SnapRow) {
                SnapRow snap = (SnapRow) userObject;
                setText(time(snap.snapshot) + "   -   " + snap.snapshot.getLabel());
                setIcon(Icons.getIcon("undo"));
                if (!selected) {
                    setForeground(JStudioTheme.getTextPrimary());
                }
            } else if (userObject instanceof ClassRow) {
                setText(simpleName(((ClassRow) userObject).internalName));
                setIcon(null);
                if (!selected) {
                    setForeground(JStudioTheme.getAccent());
                }
            } else if (userObject instanceof String) {
                setText((String) userObject);
                setIcon(null);
                if (!selected) {
                    setForeground(JStudioTheme.getTextSecondary());
                }
            }
            return this;
        }
    }
}
