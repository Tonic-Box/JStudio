package com.tonic.ui.navigator;

import com.tonic.ui.MainFrame;
import com.tonic.ui.editor.ViewMode;
import com.tonic.ui.event.EventBus;
import com.tonic.ui.event.events.ClassSelectedEvent;
import com.tonic.ui.event.events.MethodSelectedEvent;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.FieldEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.Theme;
import com.tonic.ui.theme.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class NavigatorPanel extends JPanel implements ThemeManager.ThemeChangeListener {

    private final MainFrame mainFrame;
    private final JTree tree;
    private final ClassTreeModel treeModel;
    private final JTextField searchField;
    private final JToolBar toolbar;
    private final JScrollPane scrollPane;

    public NavigatorPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;

        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(250, 0));

        toolbar = createToolbar();
        searchField = createSearchField();
        toolbar.add(searchField);

        add(toolbar, BorderLayout.NORTH);

        treeModel = new ClassTreeModel();
        tree = new JTree(treeModel);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new ClassTreeCellRenderer());
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        ToolTipManager.sharedInstance().registerComponent(tree);

        tree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                handleSelection();
            }
        });

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleDoubleClick();
                }
            }
        });

        tree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    handleDoubleClick();
                }
            }
        });

        scrollPane = new JScrollPane(tree);
        scrollPane.setBorder(null);

        add(scrollPane, BorderLayout.CENTER);

        applyTheme();

        ThemeManager.getInstance().addThemeChangeListener(this);
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        SwingUtilities.invokeLater(this::applyTheme);
    }

    private void applyTheme() {
        setBackground(JStudioTheme.getBgSecondary());
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, JStudioTheme.getBorder()));

        toolbar.setBackground(JStudioTheme.getBgSecondary());
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));

        tree.setBackground(JStudioTheme.getBgSecondary());

        scrollPane.getViewport().setBackground(JStudioTheme.getBgSecondary());

        searchField.setBackground(JStudioTheme.getBgTertiary());
        searchField.setForeground(JStudioTheme.getTextPrimary());
        searchField.setCaretColor(JStudioTheme.getTextPrimary());
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(4, 4, 4, 4),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                        BorderFactory.createEmptyBorder(2, 4, 2, 4)
                )
        ));

        repaint();
    }

    private JToolBar createToolbar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);

        JButton collapseAllButton = new JButton(Icons.getIcon("close", 12));
        collapseAllButton.setToolTipText("Collapse All");
        collapseAllButton.setFocusable(false);
        collapseAllButton.setBorderPainted(false);
        collapseAllButton.setContentAreaFilled(false);
        collapseAllButton.addActionListener(e -> collapseAll());
        tb.add(collapseAllButton);

        JButton expandAllButton = new JButton(Icons.getIcon("open", 12));
        expandAllButton.setToolTipText("Expand All");
        expandAllButton.setFocusable(false);
        expandAllButton.setBorderPainted(false);
        expandAllButton.setContentAreaFilled(false);
        expandAllButton.addActionListener(e -> expandAll());
        tb.add(expandAllButton);

        JButton refreshButton = new JButton(Icons.getIcon("refresh"));
        refreshButton.setToolTipText("Refresh");
        refreshButton.setFocusable(false);
        refreshButton.setBorderPainted(false);
        refreshButton.setContentAreaFilled(false);
        refreshButton.addActionListener(e -> refresh());
        tb.add(refreshButton);

        return tb;
    }

    private JTextField createSearchField() {
        JTextField field = new JTextField();
        field.putClientProperty("JTextField.placeholderText", "Filter classes...");

        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });

        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    field.setText("");
                    tree.requestFocus();
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    tree.requestFocus();
                    if (tree.getSelectionCount() == 0 && tree.getRowCount() > 0) {
                        tree.setSelectionRow(0);
                    }
                }
            }
        });

        return field;
    }

    private void applyFilter() {
        String text = searchField.getText().trim();
        if (text.isEmpty()) {
            treeModel.clearFilter();
        } else {
            treeModel.setFilter(text);
        }
        if (!text.isEmpty()) {
            expandAll();
        }
    }

    private void handleSelection() {
        TreePath path = tree.getSelectionPath();
        if (path == null) return;

        Object node = path.getLastPathComponent();
        if (node instanceof NavigatorNode.ClassNode) {
            ClassEntryModel classEntry = ((NavigatorNode.ClassNode) node).getClassEntry();
            mainFrame.getPropertiesPanel().showClass(classEntry);
        } else if (node instanceof NavigatorNode.MethodNode) {
            MethodEntryModel methodEntry = ((NavigatorNode.MethodNode) node).getMethodEntry();
            mainFrame.getPropertiesPanel().showMethod(methodEntry);
        }
    }

    private void handleDoubleClick() {
        TreePath path = tree.getSelectionPath();
        if (path == null) return;

        Object node = path.getLastPathComponent();
        if (node instanceof NavigatorNode.ClassNode) {
            ClassEntryModel classEntry = ((NavigatorNode.ClassNode) node).getClassEntry();
            EventBus.getInstance().post(new ClassSelectedEvent(this, classEntry));
        } else if (node instanceof NavigatorNode.MethodNode) {
            MethodEntryModel methodEntry = ((NavigatorNode.MethodNode) node).getMethodEntry();
            EventBus.getInstance().post(new MethodSelectedEvent(this, methodEntry));
            EventBus.getInstance().post(new ClassSelectedEvent(this, methodEntry.getOwner()));
        } else if (node instanceof NavigatorNode.FieldNode) {
            FieldEntryModel fieldEntry = ((NavigatorNode.FieldNode) node).getFieldEntry();
            EventBus.getInstance().post(new ClassSelectedEvent(this, fieldEntry.getOwner()));
            SwingUtilities.invokeLater(() -> {
                mainFrame.getEditorPanel().setViewMode(ViewMode.SOURCE);
                mainFrame.getEditorPanel().scrollToField(fieldEntry);
            });
        }
    }

    public void loadProject(ProjectModel project) {
        treeModel.loadProject(project);
        expandToLevel(1);
    }

    public void clear() {
        treeModel.clear();
        searchField.setText("");
    }

    public void refresh() {
        ProjectModel project = com.tonic.ui.service.ProjectService.getInstance().getCurrentProject();
        if (project != null) {
            treeModel.loadProject(project);
        }
    }

    public void filterByName(String name) {
        searchField.setText(name);
    }

    public void focusSearchField() {
        searchField.requestFocus();
        searchField.selectAll();
    }

    public void selectClass(String className) {
        NavigatorNode.ClassNode node = treeModel.findClassNode(className);
        if (node != null) {
            TreePath path = new TreePath(treeModel.getPathToRoot(node));
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
        }
    }

    public void collapseAll() {
        int row = tree.getRowCount() - 1;
        while (row >= 0) {
            tree.collapseRow(row);
            row--;
        }
    }

    public void expandAll() {
        int row = 0;
        while (row < tree.getRowCount()) {
            tree.expandRow(row);
            row++;
        }
    }

    public void expandToLevel(int level) {
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
