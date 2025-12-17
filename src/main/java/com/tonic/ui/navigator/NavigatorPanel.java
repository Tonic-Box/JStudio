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
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
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
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
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
        tree.setToggleClickCount(0);
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

    private void showContextMenu(MouseEvent e) {
        TreePath path = tree.getClosestPathForLocation(e.getX(), e.getY());
        if (path == null) return;

        tree.setSelectionPath(path);
        Object node = path.getLastPathComponent();

        JPopupMenu menu = new JPopupMenu();
        styleMenu(menu);

        if (node instanceof NavigatorNode.MethodNode) {
            buildMethodMenu(menu, (NavigatorNode.MethodNode) node);
        } else if (node instanceof NavigatorNode.FieldNode) {
            buildFieldMenu(menu, (NavigatorNode.FieldNode) node);
        } else if (node instanceof NavigatorNode.ClassNode) {
            buildClassMenu(menu, (NavigatorNode.ClassNode) node);
        }

        if (menu.getComponentCount() > 0) {
            menu.show(tree, e.getX(), e.getY());
        }
    }

    private void buildMethodMenu(JPopupMenu menu, NavigatorNode.MethodNode node) {
        MethodEntryModel method = node.getMethodEntry();

        addMenuItem(menu, "View in Call Graph", () -> {
            mainFrame.showCallGraphForMethod(method.getMethodEntry());
        });

        addMenuItem(menu, "Find Cross-References", () -> {
            mainFrame.showXrefsForMethod(
                method.getOwner().getClassName(),
                method.getName(),
                method.getDescriptor()
            );
        });

        addMenuItem(menu, "Find Usages", () -> {
            mainFrame.showUsagesForMethod(method.getName());
        });

        menu.addSeparator();

        addMenuItem(menu, "Copy Signature", () -> {
            String ownerSimple = getSimpleClassName(method.getOwner().getClassName());
            String sig = ownerSimple + "." + method.getName() + formatDescriptorParams(method.getDescriptor());
            copyToClipboard(sig);
        });

        addMenuItem(menu, "Copy Descriptor", () -> {
            copyToClipboard(method.getDescriptor());
        });

        addMenuItem(menu, "Copy Full Reference", () -> {
            String fullRef = method.getOwner().getClassName() + "." + method.getName() + method.getDescriptor();
            copyToClipboard(fullRef);
        });
    }

    private void buildFieldMenu(JPopupMenu menu, NavigatorNode.FieldNode node) {
        FieldEntryModel field = node.getFieldEntry();

        addMenuItem(menu, "Go to Field", () -> {
            EventBus.getInstance().post(new ClassSelectedEvent(this, field.getOwner()));
            SwingUtilities.invokeLater(() -> {
                mainFrame.getEditorPanel().setViewMode(ViewMode.SOURCE);
                mainFrame.getEditorPanel().scrollToField(field);
            });
        });

        addMenuItem(menu, "Find Cross-References", () -> {
            mainFrame.showXrefsForField(
                field.getOwner().getClassName(),
                field.getName(),
                field.getDescriptor()
            );
        });

        addMenuItem(menu, "Find Usages", () -> {
            mainFrame.showUsagesForField(field.getName());
        });

        menu.addSeparator();

        addMenuItem(menu, "Copy Name", () -> {
            copyToClipboard(field.getName());
        });

        addMenuItem(menu, "Copy Full Name", () -> {
            String ownerSimple = getSimpleClassName(field.getOwner().getClassName());
            copyToClipboard(ownerSimple + "." + field.getName());
        });

        addMenuItem(menu, "Copy Descriptor", () -> {
            copyToClipboard(field.getDescriptor());
        });
    }

    private void buildClassMenu(JPopupMenu menu, NavigatorNode.ClassNode node) {
        ClassEntryModel classEntry = node.getClassEntry();

        addMenuItem(menu, "Open in Editor", () -> {
            EventBus.getInstance().post(new ClassSelectedEvent(this, classEntry));
        });

        addMenuItem(menu, "View Dependencies", () -> {
            mainFrame.showDependenciesForClass(classEntry.getClassName());
        });

        addMenuItem(menu, "Find Cross-References", () -> {
            mainFrame.showXrefsForClass(classEntry.getClassName());
        });

        addMenuItem(menu, "Find Usages", () -> {
            mainFrame.showUsagesForClass(classEntry.getClassName());
        });

        menu.addSeparator();

        addMenuItem(menu, "Copy Class Name", () -> {
            copyToClipboard(classEntry.getClassName().replace('/', '.'));
        });

        addMenuItem(menu, "Copy Internal Name", () -> {
            copyToClipboard(classEntry.getClassName());
        });

        addMenuItem(menu, "Copy Simple Name", () -> {
            copyToClipboard(classEntry.getSimpleName());
        });
    }

    private void styleMenu(JPopupMenu menu) {
        menu.setBackground(JStudioTheme.getBgSecondary());
        menu.setBorder(BorderFactory.createLineBorder(JStudioTheme.getBorder()));
    }

    private void addMenuItem(JPopupMenu menu, String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setBackground(JStudioTheme.getBgSecondary());
        item.setForeground(JStudioTheme.getTextPrimary());
        item.addActionListener(e -> action.run());
        menu.add(item);
    }

    private void copyToClipboard(String text) {
        StringSelection selection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
    }

    private String getSimpleClassName(String internalName) {
        int lastSlash = internalName.lastIndexOf('/');
        return lastSlash >= 0 ? internalName.substring(lastSlash + 1) : internalName;
    }

    private String formatDescriptorParams(String descriptor) {
        if (descriptor == null || !descriptor.startsWith("(")) {
            return "()";
        }
        int endParen = descriptor.indexOf(')');
        if (endParen < 0) {
            return "()";
        }
        String params = descriptor.substring(1, endParen);
        if (params.isEmpty()) {
            return "()";
        }
        StringBuilder result = new StringBuilder("(");
        int i = 0;
        boolean first = true;
        while (i < params.length()) {
            if (!first) {
                result.append(", ");
            }
            first = false;
            char c = params.charAt(i);
            switch (c) {
                case 'B': result.append("byte"); i++; break;
                case 'C': result.append("char"); i++; break;
                case 'D': result.append("double"); i++; break;
                case 'F': result.append("float"); i++; break;
                case 'I': result.append("int"); i++; break;
                case 'J': result.append("long"); i++; break;
                case 'S': result.append("short"); i++; break;
                case 'Z': result.append("boolean"); i++; break;
                case 'V': result.append("void"); i++; break;
                case '[':
                    int arrayDims = 0;
                    while (i < params.length() && params.charAt(i) == '[') {
                        arrayDims++;
                        i++;
                    }
                    String elementType = parseOneType(params, i);
                    i += rawTypeLength(params, i);
                    result.append(elementType);
                    for (int d = 0; d < arrayDims; d++) {
                        result.append("[]");
                    }
                    break;
                case 'L':
                    int semi = params.indexOf(';', i);
                    if (semi > i) {
                        String className = params.substring(i + 1, semi);
                        result.append(getSimpleClassName(className));
                        i = semi + 1;
                    } else {
                        i++;
                    }
                    break;
                default:
                    i++;
            }
        }
        result.append(")");
        return result.toString();
    }

    private String parseOneType(String params, int i) {
        if (i >= params.length()) return "?";
        char c = params.charAt(i);
        switch (c) {
            case 'B': return "byte";
            case 'C': return "char";
            case 'D': return "double";
            case 'F': return "float";
            case 'I': return "int";
            case 'J': return "long";
            case 'S': return "short";
            case 'Z': return "boolean";
            case 'V': return "void";
            case 'L':
                int semi = params.indexOf(';', i);
                if (semi > i) {
                    return getSimpleClassName(params.substring(i + 1, semi));
                }
                return "Object";
            default:
                return "?";
        }
    }

    private int rawTypeLength(String params, int i) {
        if (i >= params.length()) return 0;
        char c = params.charAt(i);
        if (c == 'L') {
            int semi = params.indexOf(';', i);
            return semi > i ? (semi - i + 1) : 1;
        }
        return 1;
    }
}
