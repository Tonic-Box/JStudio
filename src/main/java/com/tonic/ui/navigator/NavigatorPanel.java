package com.tonic.ui.navigator;

import com.tonic.ui.MainFrame;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.core.constants.ColumnWidths;
import com.tonic.ui.core.constants.UIConstants;
import com.tonic.ui.editor.ViewMode;
import com.tonic.event.EventBus;
import com.tonic.event.events.ClassSelectedEvent;
import com.tonic.event.events.MethodSelectedEvent;
import com.tonic.event.events.ResourceSelectedEvent;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.FieldEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.model.ResourceEntryModel;
import com.tonic.plugin.api.ui.NavigatorActionProvider;
import com.tonic.service.ProjectService;
import com.tonic.ui.theme.Icons;
import com.tonic.ui.theme.JStudioTheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.OverlayLayout;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class NavigatorPanel extends ThemedJPanel {

    private final MainFrame mainFrame;
    private final JTree tree;
    private final ClassTreeModel treeModel;
    private final JTextField searchField;
    private final JToolBar toolbar;
    private final JScrollPane scrollPane;
    private final JPanel loadingOverlay;
    private final JPanel contentWrapper;
    private final NavigatorTreeStateManager treeState;
    private final NavigatorContextMenuFactory contextMenuFactory;
    /** Plugin-contributed context-menu providers, consulted each time the tree's popup opens. */
    private final List<NavigatorActionProvider> actionProviders = new CopyOnWriteArrayList<>();

    public NavigatorPanel(MainFrame mainFrame) {
        super(BackgroundStyle.SECONDARY, new BorderLayout());
        this.mainFrame = mainFrame;

        setPreferredSize(new Dimension(ColumnWidths.CLASS_NAME, 0));

        toolbar = createToolbar();
        searchField = createSearchField();
        toolbar.add(searchField);

        add(toolbar, BorderLayout.NORTH);

        treeModel = new ClassTreeModel();
        tree = new JTree(treeModel);
        treeState = new NavigatorTreeStateManager(tree, treeModel);
        NavigatorActions actions = new NavigatorActions(this, mainFrame, this::selectClass, this::setLoading);
        contextMenuFactory = new NavigatorContextMenuFactory(this, mainFrame, actions, actionProviders);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setToggleClickCount(0);
        tree.setCellRenderer(new ClassTreeCellRenderer());
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        ToolTipManager.sharedInstance().registerComponent(tree);

        tree.addTreeSelectionListener(e -> handleSelection());

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleDoubleClick();
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

        tree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    handleDoubleClick();
                }
            }

            @Override
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if (!Character.isISOControl(c)) {
                    searchField.requestFocus();
                    searchField.setText(searchField.getText() + c);
                    searchField.setCaretPosition(searchField.getText().length());
                }
            }
        });

        scrollPane = new JScrollPane(tree);
        scrollPane.setBorder(null);

        loadingOverlay = createLoadingOverlay();
        loadingOverlay.setVisible(false);

        contentWrapper = new JPanel();
        contentWrapper.setLayout(new OverlayLayout(contentWrapper));
        loadingOverlay.setAlignmentX(0.5f);
        loadingOverlay.setAlignmentY(0.5f);
        scrollPane.setAlignmentX(0.5f);
        scrollPane.setAlignmentY(0.5f);
        contentWrapper.add(loadingOverlay);
        contentWrapper.add(scrollPane);

        add(contentWrapper, BorderLayout.CENTER);
    }

    private JPanel createLoadingOverlay() {
        JPanel overlay = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(new Color(0, 0, 0, 120));
                g.fillRect(0, 0, getWidth(), getHeight());
                super.paintComponent(g);
            }
        };
        overlay.setOpaque(false);

        JLabel label = new JLabel("Renaming...", SwingConstants.CENTER);
        label.setForeground(Color.WHITE);
        label.setFont(JStudioTheme.getUIFont(14).deriveFont(Font.BOLD));
        overlay.add(label, BorderLayout.CENTER);

        return overlay;
    }

    @Override
    protected void applyChildThemes() {
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, JStudioTheme.getBorder()));
        toolbar.setBackground(JStudioTheme.getBgSecondary());
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JStudioTheme.getBorder()));

        tree.setBackground(JStudioTheme.getBgSecondary());

        scrollPane.getViewport().setBackground(JStudioTheme.getBgSecondary());

        searchField.setBackground(JStudioTheme.getBgTertiary());
        searchField.setForeground(JStudioTheme.getTextPrimary());
        searchField.setCaretColor(JStudioTheme.getTextPrimary());
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL, UIConstants.SPACING_SMALL),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(JStudioTheme.getBorder()),
                        BorderFactory.createEmptyBorder(UIConstants.SPACING_TINY, UIConstants.SPACING_SMALL, UIConstants.SPACING_TINY, UIConstants.SPACING_SMALL)
                )
        ));
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
        } else if (node instanceof NavigatorNode.FieldNode) {
            FieldEntryModel fieldEntry = ((NavigatorNode.FieldNode) node).getFieldEntry();
            mainFrame.getPropertiesPanel().showField(fieldEntry);
        }
    }

    private void handleDoubleClick() {
        TreePath path = tree.getSelectionPath();
        if (path == null) return;

        Object node = path.getLastPathComponent();
        
        // Handle folder nodes (expand/collapse on double-click)
        if (node instanceof NavigatorNode.ProjectNode ||
            node instanceof NavigatorNode.PackageNode ||
            node instanceof NavigatorNode.CategoryNode ||
            node instanceof NavigatorNode.ResourcesRootNode ||
            node instanceof NavigatorNode.ResourceFolderNode) {
            if (tree.isExpanded(path)) {
                tree.collapsePath(path);
            } else {
                tree.expandPath(path);
            }
            return;
        }
        
        // Handle class, method, and field nodes
        if (node instanceof NavigatorNode.ClassNode) {
            ClassEntryModel classEntry = ((NavigatorNode.ClassNode) node).getClassEntry();
            EventBus.getInstance().post(new ClassSelectedEvent(this, classEntry));
        } else if (node instanceof NavigatorNode.MethodNode) {
            MethodEntryModel methodEntry = ((NavigatorNode.MethodNode) node).getMethodEntry();
            EventBus.getInstance().post(new MethodSelectedEvent(this, methodEntry));
            EventBus.getInstance().post(new ClassSelectedEvent(this, methodEntry.getOwner()));
            SwingUtilities.invokeLater(() -> {
                ViewMode currentMode = mainFrame.getEditorPanel().getViewMode();
                if (currentMode == ViewMode.SOURCE || currentMode == ViewMode.BYTECODE) {
                    mainFrame.getEditorPanel().scrollToMethod(methodEntry);
                }
            });
        } else if (node instanceof NavigatorNode.FieldNode) {
            FieldEntryModel fieldEntry = ((NavigatorNode.FieldNode) node).getFieldEntry();
            EventBus.getInstance().post(new ClassSelectedEvent(this, fieldEntry.getOwner()));
            SwingUtilities.invokeLater(() -> {
                ViewMode currentMode = mainFrame.getEditorPanel().getViewMode();
                if (currentMode == ViewMode.SOURCE || currentMode == ViewMode.BYTECODE) {
                    mainFrame.getEditorPanel().scrollToField(fieldEntry);
                }
            });
        } else if (node instanceof NavigatorNode.ResourceNode) {
            ResourceEntryModel resource = ((NavigatorNode.ResourceNode) node).getResource();
            EventBus.getInstance().post(new ResourceSelectedEvent(this, resource));
        }
    }

    public void loadProject(ProjectModel project) {
        treeModel.loadProject(project);
        treeState.expandToLevel(1);
    }

    public void clear() {
        treeModel.clear();
        searchField.setText("");
    }

    public void refresh() {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project == null) {
            return;
        }
        treeState.capture();
        treeModel.loadProject(project);
        treeState.restore();
    }

    public void setLoading(boolean loading) {
        loadingOverlay.setVisible(loading);
        contentWrapper.revalidate();
        contentWrapper.repaint();
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
        treeState.collapseAll();
    }

    public void expandAll() {
        treeState.expandAll();
    }

    public void expandToLevel(int level) {
        treeState.expandToLevel(level);
    }

    private void showContextMenu(MouseEvent e) {
        TreePath path = tree.getClosestPathForLocation(e.getX(), e.getY());
        if (path == null) return;

        tree.setSelectionPath(path);
        Object node = path.getLastPathComponent();

        JPopupMenu menu = contextMenuFactory.buildFor(node);

        if (menu.getComponentCount() > 0) {
            menu.show(tree, e.getX(), e.getY());
        }
    }

    /**
     * Registers a plugin context-menu provider. Returns nothing; callers track removal via
     * {@link #removeActionProvider(NavigatorActionProvider)}.
     */
    public void addActionProvider(NavigatorActionProvider provider) {
        actionProviders.add(provider);
    }

    public void removeActionProvider(NavigatorActionProvider provider) {
        actionProviders.remove(provider);
    }

}
