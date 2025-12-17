package com.tonic.ui.script;

import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;

public class ScriptDocumentationDialog extends JDialog {

    private JTree navigationTree;
    private JEditorPane contentPane;
    private JSplitPane splitPane;

    public ScriptDocumentationDialog(Window owner) {
        super(owner, "Script Language Reference", ModalityType.MODELESS);
        initComponents();
        applyTheme();

        ThemeManager.getInstance().addThemeChangeListener(theme -> {
            applyTheme();
            refreshContent();
        });
    }

    private void initComponents() {
        setSize(950, 700);
        setMinimumSize(new Dimension(700, 500));
        setLocationRelativeTo(getOwner());

        navigationTree = createNavigationTree();
        JScrollPane treeScroll = new JScrollPane(navigationTree);
        treeScroll.setBorder(BorderFactory.createEmptyBorder());
        treeScroll.setMinimumSize(new Dimension(180, 100));

        contentPane = new JEditorPane();
        contentPane.setEditable(false);
        contentPane.setContentType("text/html");
        contentPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        JScrollPane contentScroll = new JScrollPane(contentPane);
        contentScroll.setBorder(BorderFactory.createEmptyBorder());
        contentScroll.getVerticalScrollBar().setUnitIncrement(16);

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, contentScroll);
        splitPane.setDividerLocation(200);
        splitPane.setDividerSize(4);
        splitPane.setBorder(BorderFactory.createEmptyBorder());

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        mainPanel.add(splitPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);

        showSection("Overview");
    }

    private JTree createNavigationTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Documentation");

        DefaultMutableTreeNode overview = new DefaultMutableTreeNode("Overview");
        root.add(overview);

        DefaultMutableTreeNode language = new DefaultMutableTreeNode("Language");
        language.add(new DefaultMutableTreeNode("  Loops"));
        language.add(new DefaultMutableTreeNode("  Array Methods"));
        language.add(new DefaultMutableTreeNode("  Try/Catch"));
        root.add(language);

        DefaultMutableTreeNode analysisApis = new DefaultMutableTreeNode("Analysis APIs");
        analysisApis.add(new DefaultMutableTreeNode("  Results"));
        analysisApis.add(new DefaultMutableTreeNode("  Project"));
        analysisApis.add(new DefaultMutableTreeNode("  Call Graph"));
        analysisApis.add(new DefaultMutableTreeNode("  Data Flow"));
        analysisApis.add(new DefaultMutableTreeNode("  Dependencies"));
        analysisApis.add(new DefaultMutableTreeNode("  Patterns"));
        root.add(analysisApis);

        DefaultMutableTreeNode advancedApis = new DefaultMutableTreeNode("Advanced APIs");
        advancedApis.add(new DefaultMutableTreeNode("  Simulation"));
        advancedApis.add(new DefaultMutableTreeNode("  Instrumentation"));
        advancedApis.add(new DefaultMutableTreeNode("  Types"));
        advancedApis.add(new DefaultMutableTreeNode("  Strings"));
        advancedApis.add(new DefaultMutableTreeNode("  Pipeline"));
        root.add(advancedApis);

        DefaultMutableTreeNode examples = new DefaultMutableTreeNode("Examples");
        examples.add(new DefaultMutableTreeNode("  Security Scanner"));
        examples.add(new DefaultMutableTreeNode("  Dead Code Finder"));
        root.add(examples);

        JTree tree = new JTree(new DefaultTreeModel(root));
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        tree.addTreeSelectionListener(this::onTreeSelection);

        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }

        return tree;
    }

    private void onTreeSelection(TreeSelectionEvent e) {
        TreePath path = e.getNewLeadSelectionPath();
        if (path == null) return;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        String section = node.getUserObject().toString();

        if (node.isLeaf() || isTopLevelSection(section)) {
            showSection(section);
        }
    }

    private boolean isTopLevelSection(String section) {
        return section.equals("Overview") ||
               section.equals("Language") ||
               section.equals("Analysis APIs") ||
               section.equals("Advanced APIs") ||
               section.equals("Examples");
    }

    private void showSection(String section) {
        String content = ScriptDocumentation.getContentForSection(section);
        contentPane.setText(content);
        contentPane.setCaretPosition(0);
    }

    private void refreshContent() {
        TreePath path = navigationTree.getSelectionPath();
        if (path != null) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            showSection(node.getUserObject().toString());
        } else {
            showSection("Overview");
        }
    }

    private void applyTheme() {
        Theme theme = ThemeManager.getInstance().getCurrentTheme();
        Color bg = theme.getBgPrimary();
        Color bgSecondary = theme.getBgSecondary();
        Color text = theme.getTextPrimary();
        Color accent = theme.getAccent();

        getContentPane().setBackground(bg);

        if (splitPane != null) {
            splitPane.setBackground(bg);
        }

        if (contentPane != null) {
            contentPane.setBackground(bg);
            contentPane.setForeground(text);
            contentPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            contentPane.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        }

        if (navigationTree != null) {
            navigationTree.setBackground(bgSecondary);
            navigationTree.setForeground(text);

            DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
            renderer.setBackgroundNonSelectionColor(bgSecondary);
            renderer.setBackgroundSelectionColor(accent);
            renderer.setTextNonSelectionColor(text);
            renderer.setTextSelectionColor(theme.getBgPrimary());
            renderer.setBorderSelectionColor(accent);
            renderer.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            navigationTree.setCellRenderer(renderer);
        }

        for (Component comp : getContentPane().getComponents()) {
            if (comp instanceof JPanel) {
                applyThemeToPanel((JPanel) comp, bg, text);
            }
        }
    }

    private void applyThemeToPanel(JPanel panel, Color bg, Color text) {
        panel.setBackground(bg);
        for (Component comp : panel.getComponents()) {
            if (comp instanceof JPanel) {
                applyThemeToPanel((JPanel) comp, bg, text);
            } else if (comp instanceof JButton) {
                comp.setBackground(bg);
                comp.setForeground(text);
            }
        }
    }
}
