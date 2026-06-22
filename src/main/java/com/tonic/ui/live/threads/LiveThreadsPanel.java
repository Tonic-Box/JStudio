package com.tonic.ui.live.threads;

import com.tonic.live.LiveSession;
import com.tonic.live.protocol.StackFrame;
import com.tonic.live.protocol.ThreadStack;
import com.tonic.ui.MainFrame;
import com.tonic.ui.core.SwingWorkers;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.component.ThemedJScrollPane;
import com.tonic.ui.live.LiveAttachService;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Right-dock tool (shown only while attached) listing the target JVM's threads with their current stacks.
 * Each thread expands to its frames; double-clicking a frame opens the declaring class's decompiled source
 * at that method. The stacks are a point-in-time sample - Refresh takes a fresh one.
 */
public final class LiveThreadsPanel extends ThemedJPanel {

    private static final int MAX_DEPTH = 64;

    private final MainFrame mainFrame;
    private final JButton refreshButton;
    private final JLabel statusLabel = new JLabel("Refresh to sample threads.");
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("Threads");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(root);
    private final JTree tree = new JTree(treeModel);

    public LiveThreadsPanel(MainFrame mainFrame) {
        super(BackgroundStyle.SECONDARY, new BorderLayout());
        this.mainFrame = mainFrame;

        ThemedJPanel topBar = new ThemedJPanel(BackgroundStyle.PRIMARY, new FlowLayout(FlowLayout.LEFT, 8, 4));
        refreshButton = new JButton("Refresh", Icons.getIcon("refresh"));
        refreshButton.setFocusable(false);
        refreshButton.addActionListener(e -> refresh());
        statusLabel.setForeground(JStudioTheme.getTextSecondary());
        statusLabel.setFont(JStudioTheme.getUIFont(11));
        topBar.add(refreshButton);
        add(topBar, BorderLayout.NORTH);

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setBackground(JStudioTheme.getBgSecondary());
        tree.setFont(JStudioTheme.getCodeFont(12));
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new FrameTreeRenderer());
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateSelected();
                }
            }
        });
        add(new ThemedJScrollPane(tree), BorderLayout.CENTER);

        ThemedJPanel south = new ThemedJPanel(BackgroundStyle.PRIMARY, new FlowLayout(FlowLayout.LEFT, 8, 2));
        south.add(statusLabel);
        add(south, BorderLayout.SOUTH);
    }

    public void refresh() {
        LiveSession session = LiveAttachService.getInstance().getSession();
        if (session == null) {
            root.removeAllChildren();
            treeModel.reload();
            statusLabel.setText("Not attached.");
            return;
        }
        refreshButton.setEnabled(false);
        statusLabel.setText("Sampling threads...");
        SwingWorkers.run(
                () -> session.getThreadStacks(MAX_DEPTH),
                this::populate,
                err -> {
                    refreshButton.setEnabled(true);
                    statusLabel.setText("Failed: " + err.getMessage());
                });
    }

    private void populate(List<ThreadStack> threads) {
        refreshButton.setEnabled(true);
        root.removeAllChildren();
        for (ThreadStack t : threads) {
            DefaultMutableTreeNode threadNode = new DefaultMutableTreeNode(t);
            for (StackFrame f : t.getFrames()) {
                threadNode.add(new DefaultMutableTreeNode(f));
            }
            root.add(threadNode);
        }
        treeModel.reload();
        statusLabel.setText(threads.size() + " thread" + (threads.size() == 1 ? "" : "s")
                + " - double-click a frame to open its source");
    }

    private void navigateSelected() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return;
        }
        Object node = path.getLastPathComponent();
        if (node instanceof DefaultMutableTreeNode) {
            Object userObject = ((DefaultMutableTreeNode) node).getUserObject();
            if (userObject instanceof StackFrame) {
                StackFrame frame = (StackFrame) userObject;
                mainFrame.openLiveFrame(frame.getDeclaringClass(), frame.getMethod());
            }
        }
    }

    private static final class FrameTreeRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            setBackgroundNonSelectionColor(JStudioTheme.getBgSecondary());
            setBackgroundSelectionColor(JStudioTheme.getSelection());
            setTextNonSelectionColor(JStudioTheme.getTextPrimary());
            setTextSelectionColor(JStudioTheme.getTextPrimary());
            Object userObject = value instanceof DefaultMutableTreeNode
                    ? ((DefaultMutableTreeNode) value).getUserObject() : value;
            if (userObject instanceof ThreadStack) {
                ThreadStack t = (ThreadStack) userObject;
                setText("\"" + t.getName() + "\"  [" + t.getStateEnum() + "]");
                if (!selected) {
                    setTextNonSelectionColor(JStudioTheme.getAccent());
                }
            } else if (userObject instanceof StackFrame) {
                StackFrame f = (StackFrame) userObject;
                String cls = f.getDeclaringClass().substring(f.getDeclaringClass().lastIndexOf('/') + 1);
                String where = f.getLine() >= 0 ? ":" + f.getLine() : "";
                setText(cls + "." + f.getMethod() + "(" + f.getFile() + where + ")");
            }
            setIcon(null);
            return this;
        }
    }
}
